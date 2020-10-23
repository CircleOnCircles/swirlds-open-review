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

package com.swirlds.platform.event;

import com.swirlds.common.events.Event;
import com.swirlds.platform.EventImpl;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public abstract class EventUtils {
	/**
	 * A method that does a XOR on all the event hashes in an array
	 *
	 * @param events
	 * 		the events whose hashes are to be XORed
	 * @return XOR of the event hashes, or null if there are no events
	 */
	public static byte[] xorEventHashes(EventImpl[] events) {
		if (events == null || events.length == 0) {
			return null;
		}
		byte[] xor = new byte[events[0].getBaseHash().getValue().length];
		for (EventImpl event : events) {
			for (int j = 0; j < xor.length; j++) {
				xor[j] = (byte) (xor[j] ^ event.getBaseHash().getValue()[j]);
			}
		}
		return xor;
	}

	/**
	 * Converts the event to a short string based on it's creator ID and sequence number. Internally this uses {@link
	 * EventImpl#getCreatorSeqPair()}.
	 *
	 * @param event
	 * 		the event to convert
	 * @return a short string of the form "(creatorId, creatorSeq)"
	 */
	public static String toShortString(EventImpl event) {
		return (event != null) ? event.getCreatorSeqPair().toString() : "null";
	}

	/**
	 * convert the event to a longer string, based on the creator ID and sequence number, of it and its other parent.
	 *
	 * @param event
	 * 		the event to convert
	 * @return a short string of the form "(creatorId, creatorSeq, otherId, otherSeq)"
	 */
	public static String toLongerString(EventImpl event) {
		if (event == null) {
			return "null";
		}
		return "("
				+ event.getBaseEventHashedData().getCreatorId() + ","
				+ event.getBaseEventUnhashedData().getCreatorSeq() + ","
				+ event.getBaseEventUnhashedData().getOtherId() + ","
				+ event.getBaseEventUnhashedData().getOtherSeq() + ")";
	}

	/**
	 * Convert an array of events to a single string, using toShortString() on each, and separating with commas.
	 *
	 * @param events
	 * 		array of events to convert
	 * @return a single string with a comma separated list of all of the event strings
	 */
	public static String toShortStrings(EventImpl[] events) {
		if (events == null) {
			return "null";
		}
		return Arrays.stream(events).map(EventUtils::toShortString).collect(
				Collectors.joining(","));
	}

	public static String toShortStrings(Iterable<EventImpl> events) {
		if (events == null) {
			return "null";
		}
		return StreamSupport.stream(events.spliterator(), false)
				.map(EventUtils::toShortString)
				.collect(Collectors.joining(","));
	}

//	static public boolean sorted(List<Event> events, BiPredicate<Event, Event> p) {
//		boolean sorted = true;
//		for(int i = 1; i < events.size(); ++i)
//			sorted = sorted && p.test(events.get(i-1), events.get(i));
//
//		return sorted;
//	}

	static public boolean sorted(List<EventImpl> events, BiPredicate<Event, Event> p) {
		boolean sorted = true;
		for(int i = 1; i < events.size(); ++i)
			sorted = sorted && p.test(events.get(i-1), events.get(i));

		return sorted;
	}

	// return `true` iff the offset-ordering of the given `events` could have been produced by a
	// DFS, with ancestors preceding descendant
	static public boolean DFSParentSorted(List<EventImpl> events) {
		boolean sorted = true, SPsorted = true, OPsorted = true;
		for(int i = 0; i < events.size() && sorted; ++i) {
			for (int j = 0; j < events.size(); ++j) {

				if(events.get(i).getSelfParent() != null) {
					if (events.get(i).getSelfParent().equals(events.get(j))) {
						SPsorted = SPsorted && j < i;
					}
				}

				if(events.get(i).getOtherParent() != null) {
					if (events.get(i).getOtherParent().equals(events.get(j))) {
						OPsorted = OPsorted && j < i;
					}
				}

				sorted = SPsorted && OPsorted;
			}
		}

		return sorted;
	}

	// return `true` iff the offset-ordering of the given `events` identifies the self-parent
	// of an event before the event itself
	static public boolean DFSSelfParentSorted(List<EventImpl> events) {
		boolean SPsorted = true;
		for(int i = 0; i < events.size() && SPsorted; ++i) {
			for (int j = 0; j < events.size(); ++j) {
				if(events.get(i).getSelfParent() != null) {
					if (events.get(i).getSelfParent().equals(events.get(j))) {
						SPsorted = SPsorted && j < i;
					}
				}
			}
		}

		return SPsorted;
	}

	// return `true` iff the offset-ordering of the given `events` identifies the other-parent
	// of an event before the event itself
	static public boolean DFSOtherParentSorted(List<EventImpl> events) {
		boolean OPsorted = true;
		for(int i = 0; i < events.size() && OPsorted; ++i) {
			for (int j = 0; j < events.size(); ++j) {
				if(events.get(i).getOtherParent() != null) {
					if (events.get(i).getOtherParent().equals(events.get(j))) {
						OPsorted = OPsorted && j < i;
					}
				}
			}
		}

		return OPsorted;
	}


	static public String briefBaseHash(Event e) {
		if(e == null)
			return "null";
		else
			return e.getBaseHash().toString().substring(0, 4);
	}

	static public String creator(Event e) {
		if(e == null)
			return " ";
		else
			return Long.toString(e.getCreatorId());
	}

	static public String seq(Event e) {
		if(e == null)
			return " ";
		else
			return Long.toString(e.getSeq());
	}

}
