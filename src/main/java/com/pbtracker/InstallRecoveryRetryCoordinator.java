package com.pbtracker;

/**
 * Owns the single passive install-recovery timer. The persisted value is only
 * an opaque account's next-attempt timestamp; recovery IDs, response codes,
 * credentials, and PB payloads are never retained here.
 */
final class InstallRecoveryRetryCoordinator
{
	interface Clock
	{
		long nowMillis();
	}

	interface Timer
	{
		Cancellable schedule(Runnable task, long delayMillis);
	}

	interface Cancellable
	{
		void cancel();
	}

	interface DeadlineStore
	{
		Long get(String accountHash);

		void set(String accountHash, long deadlineMillis);

		void clear(String accountHash);
	}

	interface RetryAction
	{
		void retry(String accountHash);
	}

	interface Jitter
	{
		long addToDelay(String accountHash, long delayMillis);
	}

	private final Clock clock;
	private final Timer timer;
	private final DeadlineStore store;
	private final RetryAction retryAction;
	private final Jitter jitter;
	private long generation;
	private Cancellable pending;
	private String pendingAccountHash;
	private String activeAccountHash;

	InstallRecoveryRetryCoordinator(
		Clock clock,
		Timer timer,
		DeadlineStore store,
		RetryAction retryAction,
		Jitter jitter)
	{
		this.clock = clock;
		this.timer = timer;
		this.store = store;
		this.retryAction = retryAction;
		this.jitter = jitter;
	}

	synchronized void schedule(String accountHash, long baseDelayMillis)
	{
		long delayMillis = Math.max(0L, jitter.addToDelay(accountHash, Math.max(0L, baseDelayMillis)));
		long deadlineMillis = saturatingAdd(clock.nowMillis(), delayMillis);
		store.set(accountHash, deadlineMillis);
		if (accountHash.equals(activeAccountHash))
		{
			arm(accountHash, delayMillis);
		}
	}

	synchronized void ensureScheduled(String accountHash, long baseDelayMillis)
	{
		Long existingDeadline = store.get(accountHash);
		if (existingDeadline != null && existingDeadline > clock.nowMillis())
		{
			if (accountHash.equals(activeAccountHash) && !accountHash.equals(pendingAccountHash))
			{
				arm(accountHash, existingDeadline - clock.nowMillis());
			}
			return;
		}
		schedule(accountHash, baseDelayMillis);
	}

	synchronized void restore(String accountHash)
	{
		if (!accountHash.equals(activeAccountHash))
		{
			return;
		}
		Long deadlineMillis = store.get(accountHash);
		if (deadlineMillis == null)
		{
			return;
		}
		arm(accountHash, Math.max(0L, deadlineMillis - clock.nowMillis()));
	}

	/** Re-arms a failed probe only when this account was already in durable recovery. */
	synchronized boolean scheduleFallbackIfTracked(String accountHash, long baseDelayMillis)
	{
		if (store.get(accountHash) == null)
		{
			return false;
		}
		schedule(accountHash, baseDelayMillis);
		return true;
	}

	synchronized void activateAccount(String accountHash)
	{
		if (!accountHash.equals(activeAccountHash))
		{
			cancelTimerLocked();
			activeAccountHash = accountHash;
		}
		restore(accountHash);
	}

	synchronized void deactivate()
	{
		activeAccountHash = null;
		cancelTimerLocked();
	}

	synchronized void cancelTimer()
	{
		cancelTimerLocked();
	}

	synchronized void clear(String accountHash)
	{
		store.clear(accountHash);
		if (accountHash.equals(pendingAccountHash))
		{
			cancelTimerLocked();
		}
	}

	static long boundedDeterministicJitter(String accountHash, long delayMillis)
	{
		if (delayMillis <= 0)
		{
			return 0L;
		}
		long maximumJitter = Math.min(delayMillis / 10L, 5 * 60_000L);
		if (maximumJitter == 0L)
		{
			return delayMillis;
		}
		long hash = Integer.toUnsignedLong(accountHash == null ? 0 : accountHash.hashCode());
		long jitterMillis = hash % (maximumJitter + 1L);
		return saturatingAdd(delayMillis, jitterMillis);
	}

	private void arm(String accountHash, long delayMillis)
	{
		cancelTimerLocked();
		long taskGeneration = generation;
		pendingAccountHash = accountHash;
		pending = timer.schedule(() -> fire(accountHash, taskGeneration), delayMillis);
	}

	private void fire(String accountHash, long taskGeneration)
	{
		synchronized (this)
		{
			if (taskGeneration != generation
				|| !accountHash.equals(pendingAccountHash)
				|| !accountHash.equals(activeAccountHash))
			{
				return;
			}
			pending = null;
			pendingAccountHash = null;
			generation++;
		}
		retryAction.retry(accountHash);
	}

	private void cancelTimerLocked()
	{
		generation++;
		if (pending != null)
		{
			pending.cancel();
		}
		pending = null;
		pendingAccountHash = null;
	}

	private static long saturatingAdd(long left, long right)
	{
		try
		{
			return Math.addExact(left, right);
		}
		catch (ArithmeticException ignored)
		{
			return Long.MAX_VALUE;
		}
	}
}
