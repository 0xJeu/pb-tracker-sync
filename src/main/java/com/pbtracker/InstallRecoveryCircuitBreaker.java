package com.pbtracker;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory protection against repeatedly submitting an install credential
 * that the backend has already quarantined for recovery. State is keyed by
 * account hash so switching accounts never carries one player's block to
 * another. No credential or PB payload is retained here.
 */
final class InstallRecoveryCircuitBreaker
{
	private static final long RETRY_FAILURE_BACKOFF_MILLIS = 60_000L;
	private static final long DEFAULT_RETRY_MILLIS = 15 * 60_000L;
	private static final long MINIMUM_RETRY_MILLIS = 60_000L;
	private static final long MAXIMUM_RETRY_MILLIS = 60 * 60_000L;
	private static final long REJECTED_DEFAULT_RETRY_MILLIS = 6 * 60 * 60_000L;
	private static final long REJECTED_MINIMUM_RETRY_MILLIS = 60 * 60_000L;
	private static final long REJECTED_MAXIMUM_RETRY_MILLIS = 24 * 60 * 60_000L;

	private final Map<String, RecoveryState> states = new HashMap<>();

	synchronized AttemptDecision beginAutomaticAttempt(String accountHash, long nowMillis)
	{
		RecoveryState state = states.get(accountHash);
		if (state == null)
		{
			return AttemptDecision.allowed();
		}

		if (state.probeInFlight || nowMillis < state.blockedUntilMillis)
		{
			return AttemptDecision.blocked(state.response);
		}

		// The server-requested backoff has elapsed. Permit exactly one probe;
		// other automatic triggers remain blocked until it completes.
		state.probeInFlight = true;
		return AttemptDecision.allowed();
	}

	synchronized void recordMismatch(String accountHash, SyncClient.SyncErrorResponse response, long nowMillis)
	{
		long blockedUntilMillis;
		// Every recovery state may later be changed by an operator. Even a
		// rejected candidate gets a much slower dormant probe so a later
		// reactivation is discovered without making the player reinstall.
		try
		{
			blockedUntilMillis = Math.addExact(nowMillis, retryDelayMillis(response));
		}
		catch (ArithmeticException ignored)
		{
			blockedUntilMillis = Long.MAX_VALUE;
		}

		states.put(accountHash, new RecoveryState(response, blockedUntilMillis));
	}

	static long retryDelayMillis(SyncClient.SyncErrorResponse response)
	{
		boolean rejected = response != null && "RECOVERY_REJECTED".equals(response.code);
		long defaultDelay = rejected ? REJECTED_DEFAULT_RETRY_MILLIS : DEFAULT_RETRY_MILLIS;
		long minimumDelay = rejected ? REJECTED_MINIMUM_RETRY_MILLIS : MINIMUM_RETRY_MILLIS;
		long maximumDelay = rejected ? REJECTED_MAXIMUM_RETRY_MILLIS : MAXIMUM_RETRY_MILLIS;
		if (response == null || response.retryAfterSeconds == null)
		{
			return defaultDelay;
		}

		long requestedMillis;
		try
		{
			requestedMillis = Math.multiplyExact(response.retryAfterSeconds, 1_000L);
		}
		catch (ArithmeticException ignored)
		{
			requestedMillis = maximumDelay;
		}
		return Math.max(minimumDelay, Math.min(requestedMillis, maximumDelay));
	}

	/**
	 * Returns null when no automatic recovery retry is appropriate, otherwise
	 * the delay until the next single probe. An in-flight probe gets a short
	 * guard delay so unrelated automatic triggers cannot create a tight loop.
	 */
	synchronized Long millisUntilAutomaticRetry(String accountHash, long nowMillis)
	{
		RecoveryState state = states.get(accountHash);
		if (state == null || state.blockedUntilMillis == Long.MAX_VALUE)
		{
			return null;
		}
		if (state.probeInFlight)
		{
			return RETRY_FAILURE_BACKOFF_MILLIS;
		}
		return Math.max(0L, state.blockedUntilMillis - nowMillis);
	}

	synchronized void completeUnsuccessfulAutomaticAttempt(String accountHash, long nowMillis)
	{
		RecoveryState state = states.get(accountHash);
		if (state == null || !state.probeInFlight)
		{
			return;
		}

		state.probeInFlight = false;
		long retryAt;
		try
		{
			retryAt = Math.addExact(nowMillis, RETRY_FAILURE_BACKOFF_MILLIS);
		}
		catch (ArithmeticException ignored)
		{
			retryAt = Long.MAX_VALUE;
		}
		state.blockedUntilMillis = Math.max(state.blockedUntilMillis, retryAt);
	}

	synchronized void recordSuccess(String accountHash)
	{
		states.remove(accountHash);
	}

	synchronized void clear()
	{
		states.clear();
	}

	private static final class RecoveryState
	{
		private final SyncClient.SyncErrorResponse response;
		private long blockedUntilMillis;
		private boolean probeInFlight;

		private RecoveryState(SyncClient.SyncErrorResponse response, long blockedUntilMillis)
		{
			this.response = response;
			this.blockedUntilMillis = blockedUntilMillis;
		}
	}

	static final class AttemptDecision
	{
		final boolean allowed;
		final SyncClient.SyncErrorResponse response;

		private AttemptDecision(boolean allowed, SyncClient.SyncErrorResponse response)
		{
			this.allowed = allowed;
			this.response = response;
		}

		private static AttemptDecision allowed()
		{
			return new AttemptDecision(true, null);
		}

		private static AttemptDecision blocked(SyncClient.SyncErrorResponse response)
		{
			return new AttemptDecision(false, response);
		}
	}
}
