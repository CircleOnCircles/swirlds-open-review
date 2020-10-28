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
import com.swirlds.platform.event.EventUtils;

import java.util.ArrayList;
import java.util.List;

class SyncShadowEvent {
	private final Event event; // the real event
	private final List<SyncShadowEvent> selfChildren;
	private final List<SyncShadowEvent> otherChildren;

	private SyncShadowEvent selfParent;
	private SyncShadowEvent otherParent;

	SyncShadowEvent(final Event event, final SyncShadowEvent selfParent, final SyncShadowEvent otherParent) {
		this(event);
		setSelfParent(selfParent);
		setOtherParent(otherParent);
	}

	SyncShadowEvent(final Event event) {
		this.event = event;
		this.selfParent = null;
		this.otherParent = null;
		this.selfChildren = new ArrayList<>();
		this.otherChildren = new ArrayList<>();
	}

	SyncShadowEvent getSelfParent() {
		return this.selfParent;
	}

	SyncShadowEvent getOtherParent() {
		return this.otherParent;
	}

	void setSelfParent(final SyncShadowEvent s) {
		if (s != null) {
			selfParent = s;

			if (!s.selfChildren.contains(this)) {
				s.selfChildren.add(this);
			}
		} else {
			removeSelfParent();
		}
	}

	void setOtherParent(final SyncShadowEvent s) {
		if (s != null) {
			otherParent = s;

			if (!s.otherChildren.contains(this)) {
				s.otherChildren.add(this);
			}
		} else {
			removeOtherParent();
		}
	}

	List<SyncShadowEvent> getSelfChildren() {
		return selfChildren;
	}

	List<SyncShadowEvent> getOtherChildren() {
		return otherChildren;
	}

	int getNumSelfChildren() {
		return selfChildren.size();
	}

	int getNumOtherChildren() {
		return otherChildren.size();
	}

	Event getEvent() {
		return event;
	}

	/**
	 * The cryptographic hash of an event shadow is the cryptographic hash of the event base
	 *
	 * @return The cryptographic base hash of an event.
	 */
	Hash getEventBaseHash() {
		return event.getBaseHash();
	}

	boolean isTip() {
		return selfChildren.size() == 0;
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

	private void removeSelfParent() {
		if (selfParent != null) {
			selfParent.selfChildren.remove(this);
			selfParent = null;
		}
	}

	private void removeOtherParent() {
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

		return getEventBaseHash().equals(s.getEventBaseHash());
	}


	String logString() {
		return String.format("%s(%s, %s), sp = %s(%s, %s), op = %s(%s, %s)",
			EventUtils.briefBaseHash(event),
			EventUtils.creator(event), EventUtils.seq(event),
			EventUtils.briefBaseHash(event.getSelfParent()),
			EventUtils.creator(event.getSelfParent()), EventUtils.seq(event.getSelfParent()),
			EventUtils.briefBaseHash(event.getOtherParent()),
			EventUtils.creator(event.getOtherParent()), EventUtils.seq(event.getOtherParent()));
	}
}






