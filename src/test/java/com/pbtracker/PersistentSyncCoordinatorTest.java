package com.pbtracker;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersistentSyncCoordinatorTest
{
	private static final String ACCOUNT = "acct-a";
	private static final String NAME = "Zezima";

	private static final class FakeConfigStore implements PersistedFingerprintStore.ConfigStore
	{
		private final Map<String, String> values = new HashMap<>();

		@Override
		public String get(String key)
		{
			return values.get(key);
		}

		@Override
		public void set(String key, String value)
		{
			values.put(key, value);
		}

		@Override
		public void unset(String key)
		{
			values.remove(key);
		}
	}

	private static final class SyncHarness
	{
		private final PersistentSyncCoordinator coordinator;
		private int httpCalls;

		private SyncHarness(PersistentSyncCoordinator coordinator)
		{
			this.coordinator = coordinator;
		}

		boolean attempt(
			boolean force,
			String accountHash,
			String displayName,
			Map<String, Double> pbs,
			boolean accepted,
			String currentAccountHash,
			SyncClient.SyncResponseDto outcome)
		{
			String fingerprint = SyncFingerprint.compute(accountHash, displayName, pbs);
			if (coordinator.shouldSkip(force, accountHash, fingerprint))
			{
				return false;
			}

			httpCalls++;
			return coordinator.complete(
				accepted, accountHash, currentAccountHash, fingerprint, outcome);
		}
	}

	private static Map<String, Double> pbs()
	{
		Map<String, Double> pbs = new HashMap<>();
		pbs.put("zulrah", 42.6);
		return pbs;
	}

	private static SyncClient.SyncResponseDto changedOutcome()
	{
		SyncClient.SyncResponseDto outcome = new SyncClient.SyncResponseDto();
		outcome.updated = 1;
		return outcome;
	}

	@Test
	public void unchangedAutomaticSyncSkipsTheHttpCallAcrossRestart()
	{
		FakeConfigStore persistedConfig = new FakeConfigStore();
		PersistentSyncCoordinator firstSession = new PersistentSyncCoordinator(
			new PersistedFingerprintStore(persistedConfig), new LocalProfileLoadCoordinator());
		SyncHarness initial = new SyncHarness(firstSession);
		initial.attempt(false, ACCOUNT, NAME, pbs(), true, ACCOUNT, changedOutcome());
		assertEquals(1, initial.httpCalls);

		PersistentSyncCoordinator restartedSession = new PersistentSyncCoordinator(
			new PersistedFingerprintStore(persistedConfig), new LocalProfileLoadCoordinator());
		SyncHarness restarted = new SyncHarness(restartedSession);
		restarted.attempt(false, ACCOUNT, NAME, pbs(), true, ACCOUNT, changedOutcome());
		assertEquals(0, restarted.httpCalls);
	}

	@Test
	public void manualSyncBypassesThePersistedFingerprintGate()
	{
		FakeConfigStore persistedConfig = new FakeConfigStore();
		PersistentSyncCoordinator coordinator = new PersistentSyncCoordinator(
			new PersistedFingerprintStore(persistedConfig), new LocalProfileLoadCoordinator());
		SyncHarness harness = new SyncHarness(coordinator);
		harness.attempt(false, ACCOUNT, NAME, pbs(), true, ACCOUNT, changedOutcome());

		harness.attempt(true, ACCOUNT, NAME, pbs(), true, ACCOUNT, changedOutcome());
		assertEquals(2, harness.httpCalls);
	}

	@Test
	public void successfulManualSyncPrimesTheNextAutomaticFingerprintCheck()
	{
		FakeConfigStore persistedConfig = new FakeConfigStore();
		PersistentSyncCoordinator coordinator = new PersistentSyncCoordinator(
			new PersistedFingerprintStore(persistedConfig), new LocalProfileLoadCoordinator());
		SyncHarness harness = new SyncHarness(coordinator);

		harness.attempt(true, ACCOUNT, NAME, pbs(), true, ACCOUNT, changedOutcome());
		harness.attempt(false, ACCOUNT, NAME, pbs(), true, ACCOUNT, changedOutcome());

		assertEquals(1, harness.httpCalls);
	}

	@Test
	public void networkFailureAndRejectedResponseDoNotRecordFingerprints()
	{
		FakeConfigStore persistedConfig = new FakeConfigStore();
		PersistedFingerprintStore store = new PersistedFingerprintStore(persistedConfig);
		PersistentSyncCoordinator coordinator = new PersistentSyncCoordinator(
			store, new LocalProfileLoadCoordinator());
		SyncHarness harness = new SyncHarness(coordinator);

		Map<String, Double> failedPbs = pbs();
		harness.attempt(false, "failed-account", NAME, failedPbs, false, "failed-account", null);
		assertFalse(store.matches(
			"failed-account", SyncFingerprint.compute("failed-account", NAME, failedPbs)));

		Map<String, Double> rejectedPbs = pbs();
		harness.attempt(false, "rejected-account", NAME, rejectedPbs, false, "rejected-account", null);
		assertFalse(store.matches(
			"rejected-account", SyncFingerprint.compute("rejected-account", NAME, rejectedPbs)));
		assertEquals(2, harness.httpCalls);
	}

	@Test
	public void successfulCallRecordsFingerprintAndRunsTheProfileCoordinator()
	{
		FakeConfigStore persistedConfig = new FakeConfigStore();
		PersistedFingerprintStore store = new PersistedFingerprintStore(persistedConfig);
		LocalProfileLoadCoordinator localProfiles = new LocalProfileLoadCoordinator();
		localProfiles.onLoginState(ACCOUNT, NAME, 1_000L);
		localProfiles.markLoaded();
		PersistentSyncCoordinator coordinator = new PersistentSyncCoordinator(store, localProfiles);
		SyncHarness harness = new SyncHarness(coordinator);

		boolean refreshRequested = harness.attempt(
			false, ACCOUNT, NAME, pbs(), true, ACCOUNT, changedOutcome());

		assertTrue(refreshRequested);
		assertTrue(store.matches(ACCOUNT, SyncFingerprint.compute(ACCOUNT, NAME, pbs())));
		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			localProfiles.onLoginState(ACCOUNT, NAME, 2_000L));
	}

	@Test
	public void successfulResponseForAFormerAccountRecordsWithoutRefreshingTheCurrentAccount()
	{
		FakeConfigStore persistedConfig = new FakeConfigStore();
		PersistedFingerprintStore store = new PersistedFingerprintStore(persistedConfig);
		LocalProfileLoadCoordinator localProfiles = new LocalProfileLoadCoordinator();
		localProfiles.onLoginState("current-account", "Current Player", 1_000L);
		localProfiles.markLoaded();
		PersistentSyncCoordinator coordinator = new PersistentSyncCoordinator(store, localProfiles);
		SyncHarness harness = new SyncHarness(coordinator);

		boolean refreshRequested = harness.attempt(
			false, ACCOUNT, NAME, pbs(), true, "current-account", changedOutcome());

		assertFalse(refreshRequested);
		assertTrue(store.matches(ACCOUNT, SyncFingerprint.compute(ACCOUNT, NAME, pbs())));
		assertEquals(
			LocalProfileLoadCoordinator.Decision.SKIP_ALREADY_LOADED,
			localProfiles.onLoginState("current-account", "Current Player", 2_000L));
	}

	@Test
	public void installRecoveryInvalidatesThePersistedGateBeforeItsDueProbe()
	{
		FakeConfigStore persistedConfig = new FakeConfigStore();
		PersistedFingerprintStore store = new PersistedFingerprintStore(persistedConfig);
		PersistentSyncCoordinator coordinator = new PersistentSyncCoordinator(
			store, new LocalProfileLoadCoordinator());
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		String fingerprint = SyncFingerprint.compute(ACCOUNT, NAME, pbs());

		coordinator.complete(true, ACCOUNT, ACCOUNT, fingerprint, changedOutcome());
		assertTrue(coordinator.shouldSkip(false, ACCOUNT, fingerprint));

		SyncClient.SyncErrorResponse pending = SyncClient.parseSyncErrorBody(
			new com.google.gson.Gson(),
			"{\"code\":\"RECOVERY_PENDING\",\"recoveryId\":7,\"retryAfterSeconds\":60}");
		coordinator.invalidateFingerprint(ACCOUNT);
		breaker.recordMismatch(ACCOUNT, pending, 1_000L);

		assertFalse(breaker.beginAutomaticAttempt(ACCOUNT, 60_999L).allowed);
		assertTrue(breaker.beginAutomaticAttempt(ACCOUNT, 61_000L).allowed);
		assertFalse(coordinator.shouldSkip(false, ACCOUNT, fingerprint));
	}

	@Test
	public void persistedSkipCannotLeaveARecoveryProbePermanentlyInFlight()
	{
		FakeConfigStore persistedConfig = new FakeConfigStore();
		PersistentSyncCoordinator coordinator = new PersistentSyncCoordinator(
			new PersistedFingerprintStore(persistedConfig), new LocalProfileLoadCoordinator());
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		String fingerprint = SyncFingerprint.compute(ACCOUNT, NAME, pbs());
		coordinator.complete(true, ACCOUNT, ACCOUNT, fingerprint, changedOutcome());

		SyncClient.SyncErrorResponse pending = SyncClient.parseSyncErrorBody(
			new com.google.gson.Gson(),
			"{\"code\":\"RECOVERY_PENDING\",\"retryAfterSeconds\":60}");
		breaker.recordMismatch(ACCOUNT, pending, 1_000L);
		assertTrue(breaker.beginAutomaticAttempt(ACCOUNT, 61_000L).allowed);
		assertTrue(coordinator.shouldSkip(false, ACCOUNT, fingerprint));

		// Mirrors the persisted-skip branch in PbTrackerPlugin.
		breaker.completeUnsuccessfulAutomaticAttempt(ACCOUNT, 61_000L);
		assertFalse(breaker.beginAutomaticAttempt(ACCOUNT, 120_999L).allowed);
		assertTrue(breaker.beginAutomaticAttempt(ACCOUNT, 121_000L).allowed);
	}
}
