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

package com.swirlds.fcqueue;

import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import com.swirlds.platform.StatsRunningAverage;

/**
 * Singleton factory for loading and registering {@link FCQueue} statistics. This is the primary entry point for all
 * {@link com.swirlds.common.SwirldMain} implementations that wish to track {@link FCQueue} statistics.
 */
public class FCQueueStatistics {

	/**
	 * default half-life for statistics
	 */
	private static final double DEFAULT_HALF_LIFE = 10;

	/**
	 * avg time taken to acquire locks in the FCQueue add method (in microseconds)
	 */
	protected static final StatsRunningAverage fcqAddLockAcquisitionMicros = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to acquire locks in the FCQueue remove method (in microseconds)
	 */
	protected static final StatsRunningAverage fcqRemoveLockAcquisitionMicros = new StatsRunningAverage(
			DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to acquire locks in the FCQueue poll method (in microseconds)
	 */
	protected static final StatsRunningAverage fcqPollLockAcquisitionMicros =
			new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to execute the FCQueue add method, not including locks (in microseconds)
	 */
	protected static final StatsRunningAverage fcqAddExecutionMicros = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to execute the FCQueue remove method, not including locks (in microseconds)
	 */
	protected static final StatsRunningAverage fcqRemoveExecutionMicros = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * Default private constructor to ensure that this may not be instantiated.
	 */
	private FCQueueStatistics() {

	}

	/**
	 * Registers the {@link FCQueue} statistics with the specified {@link Platform} instance. Delegates to the {@link
	 * #register(Platform, boolean)} method and defaults to including lock timings.
	 *
	 * @param platform
	 * 		the platform instance
	 */
	public static void register(final Platform platform) {
		register(platform, true);
	}

	/**
	 * Registers the {@link FCQueue} statistics with the specified {@link Platform} instance.
	 *
	 * @param platform
	 * 		the platform instance
	 * @param includeLocks
	 * 		true if lock acquisition timings should be reported; otherwise false if no lock timings should be reported
	 */
	public static void register(final Platform platform, final boolean includeLocks) {
		if (includeLocks) {
			platform.addAppStatEntry(new StatEntry(
					"internal",
					"fcqAddLockAcqMicroSec",
					"avg time taken to acquire locks in the FCQueue add method (in microseconds)",
					"%,9.6f",
					fcqAddLockAcquisitionMicros,
					(h) -> {
						fcqAddLockAcquisitionMicros.reset(h);
						return fcqAddLockAcquisitionMicros;
					},
					null,
					fcqAddLockAcquisitionMicros::getWeightedMean
			));

			platform.addAppStatEntry(new StatEntry(
					"internal",
					"fcqRemoveLockAcqMicroSec",
					"avg time taken to acquire locks in the FCQueue remove method (in microseconds)",
					"%,9.6f",
					fcqRemoveLockAcquisitionMicros,
					(h) -> {
						fcqRemoveLockAcquisitionMicros.reset(h);
						return fcqRemoveLockAcquisitionMicros;
					},
					null,
					fcqRemoveLockAcquisitionMicros::getWeightedMean
			));


			platform.addAppStatEntry(new StatEntry(
					"internal",
					"fcqPollLockAcqMicroSec",
					"avg time taken to acquire locks in the FCQueue poll method (in microseconds)",
					"%,9.6f",
					fcqPollLockAcquisitionMicros,
					(h) -> {
						fcqPollLockAcquisitionMicros.reset(h);
						return fcqPollLockAcquisitionMicros;
					},
					null,
					fcqPollLockAcquisitionMicros::getWeightedMean
			));
		}

		platform.addAppStatEntry(new StatEntry(
				"internal",
				"fcqAddExecMicroSec",
				"avg time taken to execute the FCQueue add method, not including locks (in microseconds)",
				"%,9.6f",
				fcqAddExecutionMicros,
				(h) -> {
					fcqAddExecutionMicros.reset(h);
					return fcqAddExecutionMicros;
				},
				null,
				fcqAddExecutionMicros::getWeightedMean
		));

		platform.addAppStatEntry(new StatEntry(
				"internal",
				"fcqRemoveExecMicroSec",
				"avg time taken to execute the FCQueue remove method, not including locks (in microseconds)",
				"%,9.6f",
				fcqRemoveExecutionMicros,
				(h) -> {
					fcqRemoveExecutionMicros.reset(h);
					return fcqRemoveExecutionMicros;
				},
				null,
				fcqRemoveExecutionMicros::getWeightedMean
		));
	}
}
