package com.pbtracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InstallRecoveryRetryCoordinatorTest
{
	@Test
	public void dueTimerInvokesExactlyOneRetryAndPersistsOnlyDeadline()
	{
		Harness harness = new Harness();
		harness.coordinator.schedule("account-a", 900_000L);

		assertEquals(Long.valueOf(901_000L), harness.store.get("account-a"));
		assertEquals(1, harness.timer.tasks.size());
		assertEquals(900_000L, harness.timer.tasks.get(0).delayMillis);

		harness.timer.tasks.get(0).runEvenIfCancelled();
		assertEquals(1, harness.retriedAccounts.size());
		assertEquals("account-a", harness.retriedAccounts.get(0));

		// Re-running a ScheduledFuture's callback cannot duplicate the probe.
		harness.timer.tasks.get(0).runEvenIfCancelled();
		assertEquals(1, harness.retriedAccounts.size());
	}

	@Test
	public void rescheduleKeepsOneLiveTimerAndStaleCallbackCannotFire()
	{
		Harness harness = new Harness();
		harness.coordinator.schedule("account-a", 900_000L);
		FakeTask oldTask = harness.timer.tasks.get(0);
		harness.coordinator.schedule("account-a", 60_000L);

		assertTrue(oldTask.cancelled);
		assertEquals(2, harness.timer.tasks.size());
		oldTask.runEvenIfCancelled();
		assertTrue(harness.retriedAccounts.isEmpty());

		harness.timer.tasks.get(1).runEvenIfCancelled();
		assertEquals(java.util.Collections.singletonList("account-a"), harness.retriedAccounts);
	}

	@Test
	public void accountSwitchLogoutAndShutdownCancellationPreserveDurableRetry()
	{
		Harness harness = new Harness();
		harness.coordinator.schedule("account-a", 900_000L);
		FakeTask accountATask = harness.timer.tasks.get(0);

		harness.coordinator.activateAccount("account-b");
		assertTrue(accountATask.cancelled);
		assertEquals(Long.valueOf(901_000L), harness.store.get("account-a"));
		accountATask.runEvenIfCancelled();
		assertTrue(harness.retriedAccounts.isEmpty());

		// Returning to the account restores the same deadline even when the
		// ordinary sync-on-login preference is disabled by the caller.
		harness.clock.now = 101_000L;
		harness.coordinator.activateAccount("account-a");
		FakeTask restored = harness.timer.tasks.get(1);
		assertEquals(800_000L, restored.delayMillis);

		harness.coordinator.cancelTimer(); // logout/shutdown
		assertTrue(restored.cancelled);
		assertEquals(Long.valueOf(901_000L), harness.store.get("account-a"));
		restored.runEvenIfCancelled();
		assertTrue(harness.retriedAccounts.isEmpty());
	}

	@Test
	public void successClearsDeadlineAndCancelsPendingTimer()
	{
		Harness harness = new Harness();
		harness.coordinator.schedule("account-a", 60_000L);
		FakeTask pending = harness.timer.tasks.get(0);

		harness.coordinator.clear("account-a");
		assertTrue(pending.cancelled);
		assertNull(harness.store.get("account-a"));
		pending.runEvenIfCancelled();
		assertTrue(harness.retriedAccounts.isEmpty());
	}

	@Test
	public void ensureScheduledDoesNotPushAValidDeadlineForward()
	{
		Harness harness = new Harness();
		harness.coordinator.schedule("account-a", 900_000L);
		FakeTask original = harness.timer.tasks.get(0);

		harness.clock.now = 2_000L;
		harness.coordinator.ensureScheduled("account-a", 900_000L);
		assertFalse(original.cancelled);
		assertEquals(1, harness.timer.tasks.size());
		assertEquals(Long.valueOf(901_000L), harness.store.get("account-a"));
	}

	@Test
	public void overduePersistedDeadlineRestoresAsImmediateAutomaticProbe()
	{
		Harness harness = new Harness();
		harness.store.set("account-a", 500L);
		harness.clock.now = 1_000L;

		harness.coordinator.restore("account-a");
		assertEquals(0L, harness.timer.tasks.get(0).delayMillis);
		harness.timer.tasks.get(0).runEvenIfCancelled();
		assertEquals(java.util.Collections.singletonList("account-a"), harness.retriedAccounts);
	}

	@Test
	public void failedRestoredProbeRearmsOneBoundedTimerButOrdinaryFailureDoesNot()
	{
		FakeClock clock = new FakeClock();
		clock.now = 1_000L;
		FakeTimer timer = new FakeTimer();
		FakeStore store = new FakeStore();
		store.set("recovery-account", 500L);
		InstallRecoveryRetryCoordinator[] holder = new InstallRecoveryRetryCoordinator[1];
		holder[0] = new InstallRecoveryRetryCoordinator(
			clock,
			timer,
			store,
			account -> holder[0].scheduleFallbackIfTracked(account, 300_000L),
			(account, delay) -> delay);

		holder[0].activateAccount("recovery-account");
		assertEquals(0L, timer.tasks.get(0).delayMillis);
		timer.tasks.get(0).runEvenIfCancelled(); // reconstructed probe fails/returns 5xx

		assertEquals(2, timer.tasks.size());
		assertEquals(300_000L, timer.tasks.get(1).delayMillis);
		assertEquals(Long.valueOf(301_000L), store.get("recovery-account"));
		assertFalse(timer.tasks.get(1).cancelled);

		assertFalse(holder[0].scheduleFallbackIfTracked("ordinary-account", 300_000L));
		assertEquals("ordinary failure must not create a recovery timer", 2, timer.tasks.size());
		assertNull(store.get("ordinary-account"));
	}

	@Test
	public void lateInactiveAccountResponseCannotReplaceActiveTimerAndRestoresOnReturn()
	{
		Harness harness = new Harness();
		harness.coordinator.schedule("account-a", 900_000L);

		harness.coordinator.activateAccount("account-b");
		harness.coordinator.schedule("account-b", 60_000L);
		FakeTask accountBTimer = harness.timer.tasks.get(1);
		Long accountBDeadline = harness.store.get("account-b");
		String visibleStatus = "B recovery remains active";

		// A's late 409/network/5xx callback persists A's new retry state but
		// must not cancel or replace B's sole live timer.
		harness.coordinator.schedule("account-a", 300_000L);
		if (PbTrackerPlugin.shouldApplyAccountScopedUpdate(
			"account-a", "account-b", "account-b", true))
		{
			visibleStatus = "late A response";
		}
		assertFalse(accountBTimer.cancelled);
		assertEquals(2, harness.timer.tasks.size());
		assertEquals(accountBDeadline, harness.store.get("account-b"));
		assertEquals(Long.valueOf(301_000L), harness.store.get("account-a"));
		assertEquals("B recovery remains active", visibleStatus);

		harness.coordinator.activateAccount("account-a");
		assertTrue(accountBTimer.cancelled);
		assertEquals(3, harness.timer.tasks.size());
		assertEquals(300_000L, harness.timer.tasks.get(2).delayMillis);
	}

	@Test
	public void deterministicJitterIsStablePositiveAndBounded()
	{
		long base = 900_000L;
		long first = InstallRecoveryRetryCoordinator.boundedDeterministicJitter("account-a", base);
		long second = InstallRecoveryRetryCoordinator.boundedDeterministicJitter("account-a", base);

		assertEquals(first, second);
		assertTrue(first >= base);
		assertTrue(first <= base + 90_000L);
		assertEquals(0L, InstallRecoveryRetryCoordinator.boundedDeterministicJitter("account-a", 0L));
	}

	private static final class Harness
	{
		final FakeClock clock = new FakeClock();
		final FakeTimer timer = new FakeTimer();
		final FakeStore store = new FakeStore();
		final List<String> retriedAccounts = new ArrayList<>();
		final InstallRecoveryRetryCoordinator coordinator = new InstallRecoveryRetryCoordinator(
			clock, timer, store, retriedAccounts::add, (account, delay) -> delay);

		Harness()
		{
			clock.now = 1_000L;
			coordinator.activateAccount("account-a");
		}
	}

	private static final class FakeClock implements InstallRecoveryRetryCoordinator.Clock
	{
		long now;

		@Override
		public long nowMillis()
		{
			return now;
		}
	}

	private static final class FakeTimer implements InstallRecoveryRetryCoordinator.Timer
	{
		final List<FakeTask> tasks = new ArrayList<>();

		@Override
		public InstallRecoveryRetryCoordinator.Cancellable schedule(Runnable task, long delayMillis)
		{
			FakeTask scheduled = new FakeTask(task, delayMillis);
			tasks.add(scheduled);
			return scheduled;
		}
	}

	private static final class FakeTask implements InstallRecoveryRetryCoordinator.Cancellable
	{
		final Runnable runnable;
		final long delayMillis;
		boolean cancelled;

		FakeTask(Runnable runnable, long delayMillis)
		{
			this.runnable = runnable;
			this.delayMillis = delayMillis;
		}

		@Override
		public void cancel()
		{
			cancelled = true;
		}

		void runEvenIfCancelled()
		{
			runnable.run();
		}
	}

	private static final class FakeStore implements InstallRecoveryRetryCoordinator.DeadlineStore
	{
		final Map<String, Long> values = new HashMap<>();

		@Override
		public Long get(String accountHash)
		{
			return values.get(accountHash);
		}

		@Override
		public void set(String accountHash, long deadlineMillis)
		{
			values.put(accountHash, deadlineMillis);
		}

		@Override
		public void clear(String accountHash)
		{
			values.remove(accountHash);
		}
	}
}
