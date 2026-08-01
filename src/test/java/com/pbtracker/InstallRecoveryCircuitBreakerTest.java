package com.pbtracker;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InstallRecoveryCircuitBreakerTest
{
	private static SyncClient.SyncErrorResponse response(String json)
	{
		return SyncClient.parseSyncErrorBody(new Gson(), json);
	}

	@Test
	public void parsesStructuredRecoveryResponse()
	{
		SyncClient.SyncErrorResponse parsed = response(
			"{\"code\":\"RECOVERY_PENDING\",\"recoveryId\":42,\"retryAfterSeconds\":900}"
		);

		assertEquals("RECOVERY_PENDING", parsed.code);
		assertEquals(Integer.valueOf(42), parsed.recoveryId);
		assertEquals(Long.valueOf(900), parsed.retryAfterSeconds);
	}

	@Test
	public void malformedOrUnsafeRecoveryValuesFallBackSafely()
	{
		SyncClient.SyncErrorResponse malformed = response("not json");
		assertEquals("UNKNOWN", malformed.code);
		assertNull(malformed.recoveryId);
		assertNull(malformed.retryAfterSeconds);

		SyncClient.SyncErrorResponse invalidValues = response(
			"{\"code\":\" recovery_pending \",\"recoveryId\":-2,\"retryAfterSeconds\":0}"
		);
		assertEquals("RECOVERY_PENDING", invalidValues.code);
		assertNull(invalidValues.recoveryId);
		assertNull(invalidValues.retryAfterSeconds);
	}

	@Test
	public void blocksAutomaticAttemptsUntilOneProbeIsDue()
	{
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		SyncClient.SyncErrorResponse pending = response(
			"{\"code\":\"RECOVERY_PENDING\",\"recoveryId\":7,\"retryAfterSeconds\":900}"
		);

		assertTrue(breaker.beginAutomaticAttempt("account", 1_000L).allowed);
		breaker.recordMismatch("account", pending, 1_000L);
		assertFalse(breaker.beginAutomaticAttempt("account", 900_999L).allowed);

		assertTrue(breaker.beginAutomaticAttempt("account", 901_000L).allowed);
		assertFalse("only one recovery probe may be in flight", breaker.beginAutomaticAttempt("account", 901_000L).allowed);
	}

	@Test
	public void mismatchWithoutRetryAfterBlocksForTheClientSession()
	{
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch("account", response("{\"code\":\"INSTALL_SECRET_MISMATCH\"}"), 10L);

		assertFalse(breaker.beginAutomaticAttempt("account", Long.MAX_VALUE - 1).allowed);
	}

	@Test
	public void contestedRecoveryDoesNotKeepRetryingAutomatically()
	{
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch(
			"account",
			response("{\"code\":\"RECOVERY_CONTESTED\",\"recoveryId\":8,\"retryAfterSeconds\":900}"),
			1_000L
		);

		assertFalse(breaker.beginAutomaticAttempt("account", Long.MAX_VALUE - 1).allowed);
	}

	@Test
	public void invalidationPendingGetsOneTimedProbeButInvalidationFailureDoesNot()
	{
		InstallRecoveryCircuitBreaker pendingBreaker = new InstallRecoveryCircuitBreaker();
		pendingBreaker.recordMismatch(
			"account",
			response("{\"code\":\"RECOVERY_INVALIDATION_PENDING\",\"retryAfterSeconds\":2}"),
			1_000L);

		assertFalse(pendingBreaker.beginAutomaticAttempt("account", 2_999L).allowed);
		assertTrue(pendingBreaker.beginAutomaticAttempt("account", 3_000L).allowed);

		InstallRecoveryCircuitBreaker failedBreaker = new InstallRecoveryCircuitBreaker();
		failedBreaker.recordMismatch(
			"account",
			response("{\"code\":\"RECOVERY_INVALIDATION_FAILED\",\"retryAfterSeconds\":2}"),
			1_000L);

		assertFalse(failedBreaker.beginAutomaticAttempt("account", Long.MAX_VALUE - 1).allowed);
	}

	@Test
	public void successfulManualOrAutomaticSyncClearsTheBlock()
	{
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch(
			"account",
			response("{\"code\":\"RECOVERY_PENDING\",\"recoveryId\":7,\"retryAfterSeconds\":900}"),
			1_000L
		);

		breaker.recordSuccess("account");
		assertTrue(breaker.beginAutomaticAttempt("account", 1_001L).allowed);
	}

	@Test
	public void formatsRecoveryStateWithoutCredentialMaterial()
	{
		String status = PbTrackerPlugin.formatInstallRecoveryStatus(response(
			"{\"code\":\"RECOVERY_CONTESTED\",\"recoveryId\":19,\"retryAfterSeconds\":900}"
		));

		assertEquals("Install recovery needs review (#19). Automatic sync paused for this client session.", status);
		assertFalse(status.contains("installSecret"));
	}

	@Test
	public void formatsCurrentBackendInvalidationStates()
	{
		assertEquals(
			"Install recovery safety check pending (#20). Automatic sync paused for 30 seconds.",
			PbTrackerPlugin.formatInstallRecoveryStatus(response(
				"{\"code\":\"RECOVERY_INVALIDATION_PENDING\",\"recoveryId\":20,\"retryAfterSeconds\":30}")));
		assertEquals(
			"Install recovery safety check failed (#21). Automatic sync paused for this client session.",
			PbTrackerPlugin.formatInstallRecoveryStatus(response(
				"{\"code\":\"RECOVERY_INVALIDATION_FAILED\",\"recoveryId\":21,\"retryAfterSeconds\":30}")));
	}

	@Test
	public void rejectedRecoveryGateReleasesTheClaimedPayloadFingerprint()
	{
		PbTrackerPlugin.AutomaticSyncDeduplicator deduplicator =
			new PbTrackerPlugin.AutomaticSyncDeduplicator(30_000L);
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		SyncClient.SyncErrorResponse pending = response(
			"{\"code\":\"RECOVERY_PENDING\",\"recoveryId\":7,\"retryAfterSeconds\":900}"
		);
		breaker.recordMismatch("account", pending, 1_000L);

		assertTrue(deduplicator.tryStart("payload", 1_001L));
		InstallRecoveryCircuitBreaker.AttemptDecision blocked =
			breaker.beginAutomaticAttempt("account", 1_001L);
		assertFalse(blocked.allowed);

		// Mirrors PbTrackerPlugin: recovery rejection must release, not mark
		// successful, the fingerprint claimed immediately before it.
		deduplicator.finish("payload", false, 1_001L);
		assertTrue(deduplicator.tryStart("payload", 1_002L));
	}

	@Test
	public void mismatchAndSuccessReleaseBothAutomaticGuardsCorrectly()
	{
		PbTrackerPlugin.AutomaticSyncDeduplicator deduplicator =
			new PbTrackerPlugin.AutomaticSyncDeduplicator(30_000L);
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		SyncClient.SyncErrorResponse pending = response(
			"{\"code\":\"RECOVERY_PENDING\",\"recoveryId\":7,\"retryAfterSeconds\":1}"
		);

		assertTrue(deduplicator.tryStart("payload", 1_000L));
		assertTrue(breaker.beginAutomaticAttempt("account", 1_000L).allowed);
		breaker.recordMismatch("account", pending, 1_000L);
		deduplicator.finish("payload", false, 1_000L);

		// At the server's retry time, both guards permit exactly one probe.
		assertTrue(deduplicator.tryStart("payload", 2_000L));
		assertTrue(breaker.beginAutomaticAttempt("account", 2_000L).allowed);
		breaker.recordSuccess("account");
		deduplicator.finish("payload", true, 2_000L);

		// Recovery is clear, while exact-payload success dedup still applies.
		assertFalse(deduplicator.tryStart("payload", 2_001L));
		assertTrue(deduplicator.tryStart("payload", 32_000L));
		assertTrue(breaker.beginAutomaticAttempt("account", 32_000L).allowed);
	}

	@Test
	public void failedRecoveryProbeDoesNotLeaveEitherGuardInFlight()
	{
		PbTrackerPlugin.AutomaticSyncDeduplicator deduplicator =
			new PbTrackerPlugin.AutomaticSyncDeduplicator(30_000L);
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch(
			"account",
			response("{\"code\":\"RECOVERY_PENDING\",\"recoveryId\":7,\"retryAfterSeconds\":1}"),
			1_000L
		);

		assertTrue(deduplicator.tryStart("payload", 2_000L));
		assertTrue(breaker.beginAutomaticAttempt("account", 2_000L).allowed);
		deduplicator.finish("payload", false, 2_001L);
		breaker.completeUnsuccessfulAutomaticAttempt("account", 2_001L);

		assertTrue("payload claim is released", deduplicator.tryStart("payload", 2_002L));
		assertFalse("recovery probe is released into short backoff",
			breaker.beginAutomaticAttempt("account", 2_002L).allowed);
	}

	@Test
	public void failedProbeBackoffSaturatesInsteadOfOverflowing()
	{
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch(
			"account",
			response("{\"code\":\"RECOVERY_PENDING\",\"retryAfterSeconds\":1}"),
			Long.MAX_VALUE - 2_000L);

		assertTrue(breaker.beginAutomaticAttempt("account", Long.MAX_VALUE - 1_000L).allowed);
		breaker.completeUnsuccessfulAutomaticAttempt("account", Long.MAX_VALUE - 1_000L);

		assertFalse(breaker.beginAutomaticAttempt("account", Long.MAX_VALUE - 1L).allowed);
	}
}
