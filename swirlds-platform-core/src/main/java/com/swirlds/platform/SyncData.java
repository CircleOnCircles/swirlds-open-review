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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

public class SyncData {
	final List<EventImpl> sendList = new ArrayList<>();
	final List<Hash> receivedTipHashes = new ArrayList<>();
	final HashSet<SyncShadowEvent> workingTips = new LinkedHashSet<>();

	final HashSet<Hash> sync = new HashSet<>();
	final HashSet<Hash> search = new HashSet<>();

	void resetForSync() {
		sendList.clear();
		receivedTipHashes.clear();
		workingTips.clear();

		sync.clear();
		search.clear();
	}

	void markForSync(SyncShadowEvent s) {
		sync.add(s.getEventBaseHash());
	}

	void markForSync(Hash h) {
		sync.add(h);
	}

	void markForSearch(SyncShadowEvent s) {
		search.add(s.getEventBaseHash());
	}

	void markForSearch(Hash h) {
		search.add(h);
	}

	boolean markedForSync(SyncShadowEvent s) {
		return sync.contains(s.getEventBaseHash());
	}

	boolean markedForSearch(SyncShadowEvent s) {
		return search.contains(s.getEventBaseHash());
	}

	int getNumMarkedForSync() {
		return sync.size();
	}

	int getNumMarkedForSearch() {
		return search.size();
	}
}



