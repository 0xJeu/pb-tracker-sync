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
		long blockedUntilMillis = Long.MAX_VALUE;
		Long retryAfterSeconds = response.retryAfterSeconds;
		// Only a pending candidate can become usable without changing the
		// client credential. Contested/rejected candidates need operator or
		// incumbent action, so automatic retries would only create noise.
		if (allowsTimedRetry(response) && retryAfterSeconds != null)
		{
			long retryMillis;
			try
			{
				retryMillis = Math.multiplyExact(retryAfterSeconds, 1_000L);
				blockedUntilMillis = Math.addExact(nowMillis, retryMillis);
			}
			catch (ArithmeticException ignored)
			{
				blockedUntilMillis = Long.MAX_VALUE;
			}
		}

		states.put(accountHash, new RecoveryState(response, blockedUntilMillis));
	}

	static boolean allowsTimedRetry(SyncClient.SyncErrorResponse response)
	{
		return "RECOVERY_PENDING".equals(response.code)
			|| "RECOVERY_INVALIDATION_PENDING".equals(response.code);
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
