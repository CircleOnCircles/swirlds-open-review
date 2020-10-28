/*
 * (c) 2016-2020 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */
package com.swirlds.platform;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.swirlds.logging.LogMarker.SYNC_SGM;

class SyncShadowGraphManager {
	SyncShadowGraph shadowGraph;
	HashSet<SyncShadowEvent> tips;
	long expiredGen;

	SyncShadowGraphManager() {
		this.shadowGraph = new SyncShadowGraph();
		this.expiredGen = Long.MIN_VALUE;

		this.tips = new HashSet<>();
	}

	SyncShadowGraphManager(SyncShadowGraph shadowGraph) {
		this(shadowGraph, -1);
	}

	SyncShadowGraphManager(SyncShadowGraph shadowGraph, long expiredGen) {
		this.shadowGraph = shadowGraph;
		this.expiredGen = expiredGen;

		this.tips = new HashSet<>();
		getTips();
	}

	enum VerificationStatus {
		VERIFIED,
		EVENT_NOT_IN_SHADOW_GRAPH,
		MISMATCHED_EVENT_HASH,
		MISSING_SELF_PARENT,
		MISSING_OTHER_PARENT,
		CONTAINS_EXPIRED_SELF_PARENT,
		CONTAINS_EXPIRED_OTHER_PARENT,
		SELF_PARENT_MISMATCH,
		OTHER_PARENT_MISMATCH,
		MISSING_INBOUND_SELF_CHILD_POINTER,
		MISSING_INBOUND_OTHER_CHILD_POINTER,
		MISSING_INBOUND_SELF_PARENT_POINTER,
		MISSING_INBOUND_OTHER_PARENT_POINTER,
		CONTAINS_INVALID_TIP,
		MISSING_TIP,
		INVALID_STATUS
	}

	synchronized VerificationStatus verify(Hashgraph hashgraph) {
		EventImpl[] events = hashgraph.getAllEvents();

		if (events.length == 0) {
			return VerificationStatus.VERIFIED;
		}

		for (EventImpl e : events) {
			if (expired(e)) {
				continue;
			}

			SyncShadowEvent s = shadow(e);

			if (s == null) {
				// This should be a transitory result, when the hashgraph has created a new event
				// (on intake queue thread) since the creation of this shadow graph.
				return VerificationStatus.EVENT_NOT_IN_SHADOW_GRAPH;
			}

			if (!s.getEvent().equals(e)) {
				// Internal structural failure, should never happen. This is a sanity check, only for
				// thoroughness.
				return VerificationStatus.MISMATCHED_EVENT_HASH;
			}
		}

		for (EventImpl e : events) {
			if (expired(e)) {
				continue;
			}

			SyncShadowEvent s = shadow(e);
			if (e.getSelfParent() != null) {
				if (s.getSelfParent() == null) {
					// In general, this is not a failure. The hashgraph from which this shadow graph
					// was constructed may contain expired events (with expiration as defined by the
					// hashgraph object), but this return value should be transitory in the call sequence.
					return VerificationStatus.MISSING_SELF_PARENT;
				}
			}

			if (e.getOtherParent() != null) {
				if (s.getOtherParent() == null) {
					// In general, this is not a failure. The hashgraph from which this shadow graph
					// was constructed may contain expired events (with expiration as defined by the
					// hashgraph object), but this return value should be transitory in the call sequence.
					return VerificationStatus.MISSING_OTHER_PARENT;
				}
			}

			if (e.getSelfParent() == null) {
				if (s.getSelfParent() != null) {
					// The shadow graph should not contain events that the hashgraph no longer contains.
					return VerificationStatus.CONTAINS_EXPIRED_SELF_PARENT;
				}
			}

			if (e.getOtherParent() == null) {
				if (s.getOtherParent() != null) {
					// The shadow graph should not contain events that the hashgraph no longer contains.
					return VerificationStatus.CONTAINS_EXPIRED_OTHER_PARENT;
				}
			}

			// Internal structural failures: these should never happen.
			if (s.getSelfParent() != null && !s.getSelfParent().getEvent().equals(e.getSelfParent())) {
				return VerificationStatus.SELF_PARENT_MISMATCH;
			}

			if (s.getOtherParent() != null && !s.getOtherParent().getEvent().equals(e.getOtherParent())) {
				return VerificationStatus.OTHER_PARENT_MISMATCH;
			}

			if (s.getSelfParent() != null && !s.getSelfParent().getSelfChildren().contains(s)) {
				return VerificationStatus.MISSING_INBOUND_SELF_CHILD_POINTER;
			}

			if (s.getOtherParent() != null && !s.getOtherParent().getOtherChildren().contains(s)) {
				return VerificationStatus.MISSING_INBOUND_OTHER_CHILD_POINTER;
			}

			for (SyncShadowEvent sc : s.getSelfChildren()) {
				if (sc.getSelfParent() != null && !sc.getSelfParent().getEvent().equals(e)) {
					return VerificationStatus.MISSING_INBOUND_SELF_PARENT_POINTER;
				}
			}

			for (SyncShadowEvent oc : s.getOtherChildren()) {
				if (oc.getOtherParent() != null && !oc.getOtherParent().getEvent().equals(e)) {
					return VerificationStatus.MISSING_INBOUND_OTHER_PARENT_POINTER;
				}
			}
		}

		for (EventImpl e : events) {
			if (expired(e)) {
				continue;
			}

			boolean isSelfParent = Arrays.stream(events).anyMatch(
					(EventImpl e0) -> e0.getSelfParent() != null && e0.getSelfParent().equals(e));

			// Internal failure: tip identification. Should never happen.
			if (isSelfParent && tips.contains(shadow(e))) {
				return VerificationStatus.CONTAINS_INVALID_TIP;
			}

			// Internal failure: tip identification. Should never happen.
			if (!isSelfParent && !tips.contains(shadow(e))) {
				return VerificationStatus.MISSING_TIP;
			}
		}

		return VerificationStatus.VERIFIED;
	}

	synchronized List<Hash> getSendTipHashes() {
		List<Hash> tipHashes = new ArrayList<>();
		tips.forEach((SyncShadowEvent s) -> tipHashes.add(s.getEventBaseHash()));
		return tipHashes;
	}

	synchronized void setReceivedTipHashes(SyncData syncData, String connectionLogString) {
		connectionLogString += "`setReceivedTipHashes` : ";

		if(SYNC_SGM.isEnabled()) {
			log.debug(SYNC_SGM.getMarker(), connectionLogString + "About to mark from {} received tip hashes",
					syncData.receivedTipHashes.size());
			for (Hash h : syncData.receivedTipHashes) {
				log.debug(SYNC_SGM.getMarker(),
						connectionLogString + "  received tip hash:  {}",
						h.toString().substring(0, 4));
			}

			log.debug(SYNC_SGM.getMarker(),
					connectionLogString + "before setReceivedTipHashes, {} sync-marked events, {} search-marked events",
					syncData.getNumMarkedForSync(), syncData.getNumMarkedForSearch());
		}

		setReceivedTipHashes(syncData);

		if(SYNC_SGM.isEnabled()) {
			log.debug(SYNC_SGM.getMarker(),
					connectionLogString + "after setReceivedTipHashes, {} sync-marked events, {} search-marked events",
					syncData.getNumMarkedForSync(), syncData.getNumMarkedForSearch());

		}
	}

	synchronized void setReceivedTipHashes(SyncData syncData) {
		syncData.workingTips.clear();
		syncData.workingTips.addAll(this.tips);

		syncData.receivedTipHashes.forEach((Hash h) -> {
			SyncShadowEvent receivedTip = shadowGraph.shadow(h);
			if (receivedTip != null) {
				syncData.markForSync(receivedTip);

				log.debug(SYNC_SGM.getMarker(),
						"`setReceivedTipHashes` : " + "  event marked for sync: " + receivedTip.logString());

				syncData.workingTips.remove(receivedTip);
				this.addSSDsToSendListAndRemoveFromWorkingTips(syncData.sendList, syncData.workingTips, receivedTip);
			}
		});
	}

	synchronized List<Boolean> getSendTipFlags(SyncData syncData) {
		List<Hash> receivedTipHashes = syncData.receivedTipHashes;
		return getSendTipFlags(receivedTipHashes);
	}

	synchronized List<Boolean> getSendTipFlags(List<Hash> receivedTipHashes) {
		List<Boolean> sendFlags = new ArrayList<>();

		for (int i = 0; i < receivedTipHashes.size(); ++i) {
			SyncShadowEvent receivedTip = shadowGraph.shadow(receivedTipHashes.get(i));
			if (receivedTip != null && receivedTip.getNumSelfChildren() > 0) {
				sendFlags.add(true);
			} else {
				sendFlags.add(false);
			}
		}

		return sendFlags;
	}

	synchronized void setReceivedTipFlags(SyncData syncData, List<Boolean> receivedTipFlags, String connectionLogString) {
		connectionLogString += " : `setReceivedTipFlags`: ";
		log.debug(SYNC_SGM.getMarker(),
				connectionLogString + "Received {} tip flags...",
				receivedTipFlags.size());
		for (int i = 0; i < receivedTipFlags.size(); ++i) {
			log.debug(SYNC_SGM.getMarker(),
					connectionLogString + "{} {}",
					i,
					receivedTipFlags.get(i));
		}

		List<Hash> tipHashes = getSendTipHashes();
		for (int i = 0; i < receivedTipFlags.size(); ++i) {
			Boolean b = receivedTipFlags.get(i);
			if (b) {
				syncData.markForSync(tipHashes.get(i));

				log.debug(SYNC_SGM.getMarker(),
						"`setReceivedTipFlags` : " + "  event marked for sync: event " + shadowGraph.shadow(tipHashes.get(i)).logString());

				syncData.workingTips.remove(shadowGraph.shadow(tipHashes.get(i)));
			}
		}
	}



	private static final Logger log = LogManager.getLogger();

	synchronized List<EventImpl> getSendEventList(SyncData syncData, String connectionLogString) {
		final List<EventImpl> sendList = syncData.sendList;
		final HashSet<SyncShadowEvent> workingTips = syncData.workingTips;

		if(SYNC_SGM.isEnabled()) {
			connectionLogString += " : `getSendEventList`: ";
			log.debug(SYNC_SGM.getMarker(),
					connectionLogString + "finishing sendList, starting with {} events and {} working tips",
					sendList.size(),
					workingTips.size());

			for (SyncShadowEvent tip : workingTips) {
				log.debug(SYNC_SGM.getMarker(),
						connectionLogString + "  working tip: " + tip.logString());
			}

			log.debug(SYNC_SGM.getMarker(),
					connectionLogString + "before getSendEventList, {} sync-marked events, {} search-marked events",
					syncData.getNumMarkedForSync(), syncData.getNumMarkedForSearch());

			for (SyncShadowEvent s : shadowGraph) {
				if (syncData.markedForSync(s)) {
					log.debug(SYNC_SGM.getMarker(),
							connectionLogString + "  sync-marked event is " + s.logString());
				}

				if (syncData.markedForSearch(s)) {
					log.debug(SYNC_SGM.getMarker(),
							connectionLogString + "  search-marked event is " + s.logString());
				}
			}
		}

		getSendEventList(syncData);

		if(SYNC_SGM.isEnabled()) {
			log.debug(SYNC_SGM.getMarker(),
					connectionLogString + "after getSendEventList, {} sync-marked events, {} search-marked events",
					syncData.getNumMarkedForSync(), syncData.getNumMarkedForSearch());

			for (SyncShadowEvent s : shadowGraph) {
				if (syncData.markedForSync(s)) {
					log.debug(SYNC_SGM.getMarker(),
							connectionLogString + "  sync-marked event is " + s.logString());
				}
				if (syncData.markedForSearch(s)) {
					log.debug(SYNC_SGM.getMarker(),
							connectionLogString + "  search-marked event is " + s.logString());
				}
			}

			log.debug(SYNC_SGM.getMarker(),
					connectionLogString + "Finished. sendList has {} events",
					sendList.size());

			if (sendList.size() > 5 * tips.size()) {
				log.debug(SYNC_SGM.getMarker(),
						connectionLogString + "! Warning: sendList.size() = {} > 5 * {} = {}",
						sendList.size(),
						tips.size(),
						5 * tips.size());

			}
		}

		return sendList;
	}

	synchronized List<EventImpl> getSendEventList(SyncData syncData) {
		HashSet<SyncShadowEvent> workingTips = syncData.workingTips;
		List<EventImpl> sendList = syncData.sendList;
		for (SyncShadowEvent workingTip : workingTips) {
			SyncShadowEvent y = workingTip;

			while (y != null) {

				for (SyncShadowEvent z : shadowGraph.graphDescendants(y)) {
					if (syncData.markedForSync(z)) {
						syncData.markForSearch(y);
						break;
					}
				}

				if (!syncData.markedForSearch(y)) {
					sendList.add((EventImpl) y.getEvent());
				} else {
					break;
				}

				y = y.getSelfParent();
			}
		}

		sort(sendList);

		return sendList;

	}

	/**
	 * Apply a DFS dependency sort to `sendList`.
	 * @param sendList
	 *        The list of events to sort.
	 */
	private static void sort(List<EventImpl> sendList) {
		sendList.sort((EventImpl e1, EventImpl e2) -> (int)(e1.getGeneration() - e2.getGeneration()));
	}

	// Add strict self-descendants of x to send list
	private void addSSDsToSendListAndRemoveFromWorkingTips(List<EventImpl> sendList, HashSet<SyncShadowEvent> workingTips, SyncShadowEvent x) {
		for (SyncShadowEvent y : x.getSelfChildren()) {

			addSDsToSendListAndRemoveFromWorkingTips(sendList, workingTips, y);
		}
	}

	// Add self-descendants of x to send list
	private void addSDsToSendListAndRemoveFromWorkingTips(List<EventImpl> sendList, HashSet<SyncShadowEvent> workingTips, SyncShadowEvent y) {
		if (y == null) {
			return;
		}

		sendList.add((EventImpl) y.getEvent());
		workingTips.remove(y);

		for (SyncShadowEvent y0 : y.getSelfChildren()) {
			addSDsToSendListAndRemoveFromWorkingTips(sendList, workingTips, y0);
		}
	}

	synchronized long getExpiredGeneration() {
		return expiredGen;
	}

	synchronized void setExpiredGeneration(long expiredGen) {
		if (expiredGen < this.expiredGen) {
			String msg = String.format(
					"Shadow graph expired generation can not be decreased. Change %s -> %s disallowed.",
					this.expiredGen,
					expiredGen);
			throw new IllegalArgumentException(msg);
		} else {
			this.expiredGen = expiredGen;
		}
	}

	/**
	 * Predicate to determine if an event referenced by a shadow event has expired.
	 * A shadow is expired iff the event it references is expired.
	 *
	 * @param s
	 *        The shadow event.
	 *
	 * @return true iff the given shadow is expired
	 */
	private boolean expired(SyncShadowEvent s) {
		return s.getEvent().getGeneration() <= expiredGen;
	}

	/**
	 * Predicate to determine if an event has expired.
	 *
	 * @param event
	 *        The event.
	 *
	 * @return true iff the given event is expired
	 */
	synchronized boolean expired(Event event) {
		return event.getGeneration() <= expiredGen;
	}


	/**
	 * Remove events that have expired
	 *
	 * @param newExpiredGeneration
	 *        Any event with a generation less than or equal to `newExpiredGeneration` is to be removed.
	 *
	 * @return number of events expunged from the shadow graph
	 */
	synchronized int expire(long newExpiredGeneration) {
		if (newExpiredGeneration == expiredGen) {
			return 0;
		}

		setExpiredGeneration(newExpiredGeneration);
		return expire();
	}

	/**
	 * Remove events that have expired. Any event with a generation less than or
	 * equal to `this.expiredGen` is to be removed.
	 *
	 * @return number of event expunged from the shadow graph
	 */
	synchronized int expire() {
		HashMap<SyncShadowEvent, Boolean> tipRemove = new HashMap<>();
		AtomicInteger count = new AtomicInteger();

		for (SyncShadowEvent tip : tips) {
			count.addAndGet(shadowGraph.removeStrictSelfAncestry(tip, this::expired));
			tipRemove.put(tip, expired(tip));
		}

		tipRemove.forEach((SyncShadowEvent t, Boolean remove) -> {
			if (remove) {
				count.addAndGet(shadowGraph.removeSelfAncestry(t, this::expired));
			}
		});

		getTips();
		return count.get();
	}

	/**
	 * Get the shadow event that reference a hashgraph event instance.
	 *
	 * @param e
	 *        The event.
	 * @return the shadow event that references an event
	 */
	synchronized SyncShadowEvent shadow(Event e) {
		return shadowGraph.shadow(e);
	}

	enum InsertableStatus {
		INSERTABLE,
		NULL_EVENT,
		DUPLICATE_SHADOW_EVENT,
		EXPIRED_EVENT,
		UNKNOWN_CURRENT_SELF_PARENT,
		UNKNOWN_CURRENT_OTHER_PARENT
	}

	private InsertableStatus insertable(Event e) {
		if (e == null) {
			return InsertableStatus.NULL_EVENT;
		}

		// No multiple insertions
		if (shadow(e) != null) {
			return InsertableStatus.DUPLICATE_SHADOW_EVENT;
		}

		// An expired event will not be referenced in the graph.
		if (expired(e)) {
			return InsertableStatus.EXPIRED_EVENT;
		}

		boolean hasOP = e.getOtherParent() != null;
		boolean hasSP = e.getSelfParent() != null;

		boolean knownOP = hasOP && shadowGraph.shadow(e.getOtherParent()) != null;
		boolean knownSP = hasSP && shadowGraph.shadow(e.getSelfParent()) != null;

		boolean expiredOP = hasOP && expired(e.getOtherParent());
		boolean expiredSP = hasSP && expired(e.getSelfParent());

		// If e has an unexpired parent that is not already referenced
		// by the shadow graph, then do not insert e.
		if (hasOP) {
			if (!knownOP && !expiredOP) {
				return InsertableStatus.UNKNOWN_CURRENT_OTHER_PARENT;
			}
		}

		if (hasSP) {
			if (!knownSP && !expiredSP) {
				return InsertableStatus.UNKNOWN_CURRENT_SELF_PARENT;
			}
		}

		// If both parents are null, then insertion is allowed. This will create
		// a new tree in the forest view of the graph.
		return InsertableStatus.INSERTABLE;
	}

	/**
	 * If Event `e` is insertable, then insert it and update the tip Event set, else do nothing.
	 * @param e
	 *        The event reference to insert.
	 * @return true iff e was inserted
	 */
	synchronized boolean addEvent(Event e) {
		if (InsertableStatus.INSERTABLE == insertable(e)) {
			if (shadowGraph.insert(e)) {
				SyncShadowEvent s = shadowGraph.shadow(e);
				if (tips.contains(s.getSelfParent())) {
					tips.remove(s.getSelfParent());
					tips.add(s);
				}

				if (s.getNumSelfChildren() == 0) {
					tips.add(s);
				}

				return true;
			}
		}

		return false;
	}

	private void getTips() {
		tips.clear();
		for (SyncShadowEvent shadowEvent : shadowGraph.shadowEvents) {
			if (shadowEvent.isTip()) {
				tips.add(shadowEvent);
			}
		}
	}

	synchronized int getNumTips() {
		return tips.size();
	}
}


