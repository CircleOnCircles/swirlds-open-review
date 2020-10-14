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
import java.util.List;

class SyncShadowEvent {
	final Event event; // the real event
	final List<SyncShadowEvent> selfChildren;
	final List<SyncShadowEvent> otherChildren;

	SyncShadowEvent selfParent;
	SyncShadowEvent otherParent;
	long sequenceNumber;
	long searchMark;
	long syncMark;

	SyncShadowEvent(final Event event, final SyncShadowEvent selfParent, final SyncShadowEvent otherParent) {
		this(event);
		setSelfParent(selfParent);
		setOtherParent(otherParent);
		this.sequenceNumber = event.getSeq();
	}

	SyncShadowEvent(final Event event) {
		this.event = event;
		this.selfParent = null;
		this.otherParent = null;
		this.selfChildren = new ArrayList<>();
		this.otherChildren = new ArrayList<>();
		this.sequenceNumber = event.getSeq();
	}

	/**
	 * The hash of a event shadow is the hash of the event
	 */
	Hash getBaseEventHash() {
		return event.getBaseHash();
	}

	boolean isTip() {
		return selfChildren.size() == 0;
	}

	void markForSearch() {
		searchMark++;
	}

	void markForSync() {
		syncMark++;
	}

	void disconnect() {
		removeSelfParent();
		removeOtherParent();

		for (SyncShadowEvent s : selfChildren) {
			s.selfParent = null;
		}
		
		for (SyncShadowEvent s : otherChildren) {
			s.otherParent = null;
		}

		selfChildren.clear();
		otherChildren.clear();
	}

	boolean hasSelfDescendant(final SyncShadowEvent y) {
		if (y == null) {
			return false;
		}

		if (this == y) {
			return true;
		}

		for (final SyncShadowEvent sc : this.selfChildren) {
			if (sc.hasSelfDescendant(y)) {
				return true;
			}
		}

		return false;
	}

	boolean hasDescendant(final SyncShadowEvent y) {
		if (y == null) {
			return false;
		}

		if (this == y) {
			return true;
		}

		for (final SyncShadowEvent sc : this.selfChildren) {
			if (sc.hasDescendant(y)) {
				return true;
			}
		}

		for (final SyncShadowEvent oc : this.otherChildren) {
			if (oc.hasDescendant(y)) {
				return true;
			}
		}

		return false;
	}


	void addSelfChild(final SyncShadowEvent s) {
		if (s != null) {
			s.setSelfParent(this);
		}
	}

	void addOtherChild(final SyncShadowEvent s) {
		if (s != null) {
			s.setOtherParent(this);
		}
	}

	void setSelfParent(final SyncShadowEvent s) {
		if (s != null) {
			selfParent = s;

			if (!s.selfChildren.contains(s)) {
				s.selfChildren.add(this);
			}
		} else {
			removeSelfParent();
		}
	}

	void setOtherParent(final SyncShadowEvent s) {
		if (s != null) {
			otherParent = s;

			if (!s.otherChildren.contains(s)) {
				s.otherChildren.add(this);
			}
		} else {
			removeOtherParent();
		}
	}

	void removeSelfParent() {
		if (selfParent != null) {
			selfParent.selfChildren.remove(this);
			selfParent = null;
		}
	}

	void removeOtherParent() {
		if (otherParent != null) {
			otherParent.otherChildren.remove(this);
			otherParent = null;
		}
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof SyncShadowEvent)) {
			return false;
		}

		final SyncShadowEvent s = (SyncShadowEvent) o;

		return getBaseEventHash().equals(s.getBaseEventHash());
	}

}






