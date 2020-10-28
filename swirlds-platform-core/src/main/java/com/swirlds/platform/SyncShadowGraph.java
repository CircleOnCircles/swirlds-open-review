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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

class SyncShadowGraph implements Iterable<SyncShadowEvent> {
	final HashMap<Hash, SyncShadowEvent> hashToShadowEvent;
	final HashSet<SyncShadowEvent> shadowEvents;

	SyncShadowGraph() {
		this.hashToShadowEvent = new HashMap<>();
		this.shadowEvents = new HashSet<>();
	}

	SyncShadowGraph(List<EventImpl> events) {
		this.hashToShadowEvent = new HashMap<>();
		this.shadowEvents = construct(events);
	}

	SyncShadowGraph(AbstractHashgraph hashgraph) {
		List<EventImpl> nonExpiredEvents = new ArrayList<>();
		EventImpl[] allEvents = hashgraph.getAllEvents();

		for (EventImpl e : allEvents) {
			if (e.getGeneration() >= hashgraph.getMinGenerationNonExpired()) {
				nonExpiredEvents.add(e);
			}
		}

		this.hashToShadowEvent = new HashMap<>();
		this.shadowEvents = construct(nonExpiredEvents);
	}

	int size() {
		return shadowEvents.size();
	}

	SyncShadowEvent shadow(Event e) {
		if (e == null) {
			return null;
		}

		return hashToShadowEvent.get(e.getBaseHash());
	}

	SyncShadowEvent shadow(Hash hash) {
		return hashToShadowEvent.get(hash);
	}

	boolean insert(Event event) {
		return insert(new SyncShadowEvent(event));
	}

	private boolean insert(SyncShadowEvent shadowEvent) {
		if (shadowEvent == null) {
			return false;
		}

		if (shadowEvent.getEvent() == null) {
			return false;
		}

		SyncShadowEvent sp = shadow(shadowEvent.getEvent().getSelfParent());
		SyncShadowEvent op = shadow(shadowEvent.getEvent().getOtherParent());

		if (sp != null) {
			sp.addSelfChild(shadowEvent);
		}

		if (op != null) {
			op.addOtherChild(shadowEvent);
		}

		hashToShadowEvent.put(shadowEvent.getEventBaseHash(), shadowEvent);
		shadowEvents.add(shadowEvent);

		return true;
	}

	int removeStrictSelfAncestry(SyncShadowEvent s, Predicate<SyncShadowEvent> p) {
		int count = 0;
		count += removeSelfAncestry(s.getSelfParent(), p);
		return count;
	}

	int removeSelfAncestry(SyncShadowEvent s) {
		return removeSelfAncestry(s, (x) -> true);
	}

	int removeSelfAncestry(SyncShadowEvent s, Predicate<SyncShadowEvent> p) {
		if (s == null) {
			return 0;
		}

		int count = 0;

		count += removeSelfAncestry(s.getSelfParent(), p);

		if (p.test(s)) {
			count += remove(s) ? 1 : 0;
		}

		return count;
	}

	int removeStrictAncestry(SyncShadowEvent s, Predicate<SyncShadowEvent> p) {
		int count = 0;
		count += removeAncestry(s.getSelfParent(), p);
		count += removeAncestry(s.getOtherParent(), p);
		return count;
	}

	int removeAncestry(SyncShadowEvent s, Predicate<SyncShadowEvent> p) {
		if (s == null) {
			return 0;
		}

		int count = 0;

		count += removeAncestry(s.getSelfParent(), p);
		count += removeAncestry(s.getOtherParent(), p);

		if (p.test(s)) {
			count += remove(s) ? 1 : 0;
		}

		return count;
	}

	int removeAncestry(SyncShadowEvent s) {
		return removeAncestry(s, (x) -> true);
	}

	private boolean remove(SyncShadowEvent s) {
		if (s == null) {
			return false;
		}

		if (!shadowEvents.contains(s)) {
			return false;
		}

		s.disconnect();
		hashToShadowEvent.remove(s.getEventBaseHash());
		shadowEvents.remove(s);

		return true;
	}

	private HashSet<SyncShadowEvent> construct(List<EventImpl> events) {
		return constructByLoops(events.toArray(new EventImpl[0]));
	}

	private HashSet<SyncShadowEvent> constructByLoops(EventImpl[] events) {
		List<SyncShadowEvent> shadowEvents = new ArrayList<>();
		for (int i = 0; i < events.length; ++i) {
			if (events[i].getBaseHash() != null) {
				shadowEvents.add(new SyncShadowEvent(events[i]));
			}
		}

		SyncShadowEvent[] s = shadowEvents.toArray(new SyncShadowEvent[0]);

		for (int i = 0; i < s.length; ++i) {
			for (int j = 0; j < s.length; ++j) {
				if (s[i].getEvent().getSelfParent() == s[j].getEvent()) {
					s[i].setSelfParent(s[j]);
				}

				if (s[j].getEvent().getSelfParent() == s[i].getEvent()) {
					s[j].setSelfParent(s[i]);
				}

				if (s[i].getEvent().getOtherParent() == s[j].getEvent()) {
					s[i].setOtherParent(s[j]);
				}

				if (s[j].getEvent().getOtherParent() == s[i].getEvent()) {
					s[j].setOtherParent(s[i]);
				}
			}
		}

		for (int i = 0; i < s.length; ++i) {
			for (int j = 0; j < s.length; ++j) {
				if (s[j].getSelfParent() == s[i]) {
					s[i].addSelfChild(s[j]);
				}

				if (s[j].getOtherParent() == s[i]) {
					s[i].addOtherChild(s[j]);
				}
			}
		}

		for (SyncShadowEvent ss : s) {
			hashToShadowEvent.put(ss.getEventBaseHash(), ss);
		}

		return new HashSet<>(Arrays.asList(s));
	}


	SyncShadowForestView forest() {
		return new SyncShadowForestView(this);
	}

	SyncShadowForestView forest(SyncShadowEvent start) {
		return new SyncShadowForestView(this, start);
	}

	SyncShadowGraphView graph() {
		return new SyncShadowGraphView(this);
	}

	SyncShadowGraphDescendantView graphDescendants(SyncShadowEvent start) {
		return new SyncShadowGraphDescendantView(this, start);
	}

	@Override
	public Iterator<SyncShadowEvent> iterator() {
		return shadowEvents.iterator();
	}

}
