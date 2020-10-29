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

import com.swirlds.common.AddressBook;
import com.swirlds.common.crypto.Hash;

import java.util.List;

abstract class AbstractHashgraph {

	/**
	 * Get the size of the event intake queue queue
	 *
	 * @return the number of elements in the queue
	 */
	abstract int getEventIntakeQueueSize();

	/**
	 * Checks whether the node with the supplied id is in the super minority by number of events created in the most
	 * recent round.
	 *
	 * @param nodeId
	 * 		the id of the node to check
	 * @return true if it is in the super minority, false otherwise
	 */
	abstract boolean isStrongMinorityInMaxRound(long nodeId);

	/**
	 * @return the number of events with user transactions currently in the hashgraph
	 */
	abstract long getNumUserTransEvents();

	/**
	 * @return the last round received after which there were no non consensus user transactions in the hashgraph
	 */
	abstract long getLastRoundReceivedAllTransCons();

	/** get number of members for whom no event has ever been known */
	abstract long getNumNotStarted();

	/**
	 * Returns whether a node has been started or not, by looking at whether we have received any events
	 * from this node
	 *
	 * @param nodeId
	 * 		the ID of the node
	 * @return true if is has started, false otherwise
	 */
	abstract boolean hasNodeStarted(int nodeId);

	/**
	 * Get the latest version of the address book. It will be immutable.
	 *
	 * The returned value may be slightly out of date. If it needs to be up to date, then call this method
	 * from inside a hashgraph.lock.lock("location") block.
	 *
	 * @return the latest version of the address book (earlier versions may still be in use)
	 */
	abstract AddressBook getAddressBook();

	/**
	 * Get an array of all the events in the hashgraph. This method is slow, so do not call it very often.
	 * The returned array is a shallow copy, so the caller may change it, and no other threads will change
	 * it. However, the events it references may have fields that are used or changed by other threads, so
	 * the caller should not change them, and should not mind if their fields change.
	 *
	 * The returned array values may be slightly out of date, and different elements of the array may be out
	 * of date by different amounts.
	 *
	 * @return an array of all the events
	 */
	abstract EventImpl[] getAllEvents();

	/**
	 * Get an array of all recent events in the hashgraph. This method is slow, so do not call it very often.
	 * The returned array is a shallow copy, so the caller may change it, and no other threads will change
	 * it. However, the events it references may have fields that are used or changed by other threads, so
	 * the caller should not change them, and should not mind if their fields change.
	 *
	 * The returned array values may be slightly out of date, and different elements of the array may be out
	 * of date by different amounts.
	 *
	 * @return an array of all events with generation number >= `minGenerationNumber`
	 */
	abstract EventImpl[] getRecentEvents(long minGenerationNumber);

	/**
	 * @return the number of events in the hashgraph at the time of call
	 */
	abstract int getNumEvents();

	/**
	 * @return the minimum generation that is not ancient. All events with generations less than this are either ancient
	 * 		or expired.
	 */
	abstract long getMinGenerationNonAncient();

	/**
	 * Return the minimum generation of all the famous witnesses that are not in expired rounds.
	 *
	 * @return the minimum (oldest, least recent) generation which is not yet expired
	 */
	abstract long getMinGenerationNonExpired();

	/**
	 * Return the minimum round number from rounds that have not yet expired.
	 *
	 * @return the minimum (oldest, least recent) round which is not yet expired
	 */
	abstract long getMinRoundNonExpired();

	/**
	 * Return the hashes of the judges in the given round. Returns null if the round is unknown, or if the complete
	 * set of judges for that round is not yet decided (i.e., the round doesn't yet have consensus). The caller must not
	 * modify any hash in the list, but it is ok to modify the list itself.
	 *
	 * @param round
	 * 		the round number to get (each judge has this as its roundCreated)
	 * @return the Hash of each judge in that round
	 */
	abstract List<Hash> getJudgeHashes(long round);
}
