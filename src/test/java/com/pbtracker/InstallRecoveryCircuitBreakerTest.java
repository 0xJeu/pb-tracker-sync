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
		assertEquals(Long.valueOf(1L), breaker.millisUntilAutomaticRetry("account", 900_999L));
		assertFalse(breaker.beginAutomaticAttempt("account", 900_999L).allowed);

		assertTrue(breaker.beginAutomaticAttempt("account", 901_000L).allowed);
		assertFalse("only one recovery probe may be in flight",
			breaker.beginAutomaticAttempt("account", 901_000L).allowed);
		assertEquals(Long.valueOf(60_000L), breaker.millisUntilAutomaticRetry("account", 901_000L));
	}

	@Test
	public void mismatchWithoutRetryAfterUsesLowFrequencyDefault()
	{
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch("account", response("{\"code\":\"INSTALL_SECRET_MISMATCH\"}"), 10L);

		assertFalse(breaker.beginAutomaticAttempt("account", 900_009L).allowed);
		assertTrue(breaker.beginAutomaticAttempt("account", 900_010L).allowed);
	}

	@Test
	public void contestedAndInvalidationFailuresKeepProbingForAdminResolution()
	{
		String[] codes = {
			"RECOVERY_CONTESTED",
			"RECOVERY_INVALIDATION_PENDING",
			"RECOVERY_INVALIDATION_FAILED",
			"RECOVERY_REVIEW_REQUIRED"
		};
		for (String code : codes)
		{
			InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
			breaker.recordMismatch(
				"account", response("{\"code\":\"" + code + "\",\"retryAfterSeconds\":60}"), 1_000L);

			assertFalse(code, breaker.beginAutomaticAttempt("account", 60_999L).allowed);
			assertTrue(code, breaker.beginAutomaticAttempt("account", 61_000L).allowed);
		}
	}

	@Test
	public void rejectedRecoveryUsesADormantProbeInsteadOfBecomingPermanent()
	{
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch(
			"account",
			response("{\"code\":\"RECOVERY_REJECTED\",\"retryAfterSeconds\":60}"),
			1_000L);

		assertFalse(breaker.beginAutomaticAttempt("account", 3_600_999L).allowed);
		assertTrue(breaker.beginAutomaticAttempt("account", 3_601_000L).allowed);
	}

	@Test
	public void retryHintsAreClampedToAvoidStormsAndIndefiniteSilence()
	{
		assertEquals(60_000L, InstallRecoveryCircuitBreaker.retryDelayMillis(
			response("{\"code\":\"RECOVERY_PENDING\",\"retryAfterSeconds\":1}")));
		assertEquals(900_000L, InstallRecoveryCircuitBreaker.retryDelayMillis(
			response("{\"code\":\"RECOVERY_PENDING\"}")));
		assertEquals(3_600_000L, InstallRecoveryCircuitBreaker.retryDelayMillis(
			response("{\"code\":\"RECOVERY_PENDING\",\"retryAfterSeconds\":999999999}")));
		assertEquals(3_600_000L, InstallRecoveryCircuitBreaker.retryDelayMillis(
			response("{\"code\":\"RECOVERY_REJECTED\",\"retryAfterSeconds\":60}")));
		assertEquals(21_600_000L, InstallRecoveryCircuitBreaker.retryDelayMillis(
			response("{\"code\":\"RECOVERY_REJECTED\"}")));
		assertEquals(86_400_000L, InstallRecoveryCircuitBreaker.retryDelayMillis(
			response("{\"code\":\"RECOVERY_REJECTED\",\"retryAfterSeconds\":999999999}")));
	}

	@Test
	public void successfulManualOrAutomaticSyncClearsTheBlock()
	{
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch(
			"account",
			response("{\"code\":\"RECOVERY_PENDING\",\"recoveryId\":7,\"retryAfterSeconds\":900}"),
			1_000L);

		breaker.recordSuccess("account");
		assertTrue(breaker.beginAutomaticAttempt("account", 1_001L).allowed);
		assertNull(breaker.millisUntilAutomaticRetry("account", 1_001L));
	}

	@Test
	public void recoveryStateIsIsolatedByAccountAndRestartFailsOpenSafely()
	{
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch(
			"account-a",
			response("{\"code\":\"RECOVERY_PENDING\",\"retryAfterSeconds\":900}"),
			1_000L);

		assertFalse(breaker.beginAutomaticAttempt("account-a", 1_001L).allowed);
		assertTrue(breaker.beginAutomaticAttempt("account-b", 1_001L).allowed);

		// A plugin restart forgets only the in-memory throttle. The next login
		// performs one safe backend probe and reconstructs it from the response.
		InstallRecoveryCircuitBreaker restarted = new InstallRecoveryCircuitBreaker();
		assertTrue(restarted.beginAutomaticAttempt("account-a", 1_001L).allowed);
	}

	@Test
	public void formatsRecoveryStateWithoutSensitiveOrOperatorMetadata()
	{
		String contested = PbTrackerPlugin.formatInstallRecoveryStatus(response(
			"{\"code\":\"RECOVERY_CONTESTED\",\"recoveryId\":19,\"retryAfterSeconds\":900}"
		));
		assertEquals(
			"This RuneLite installation is awaiting review. Sync will resume automatically when resolved.",
			contested);
		assertFalse(contested.contains("installSecret"));
		assertFalse(contested.contains("19"));

		assertEquals(
			"Verifying this RuneLite installation. Sync will resume automatically.",
			PbTrackerPlugin.formatInstallRecoveryStatus(response(
				"{\"code\":\"RECOVERY_INVALIDATION_PENDING\",\"recoveryId\":20}")));
		assertEquals(
			"This RuneLite installation is awaiting review. Sync will resume automatically when resolved.",
			PbTrackerPlugin.formatInstallRecoveryStatus(response(
				"{\"code\":\"RECOVERY_INVALIDATION_FAILED\",\"recoveryId\":21}")));
		assertEquals(
			"This RuneLite installation was not approved. Sync will check again periodically.",
			PbTrackerPlugin.formatInstallRecoveryStatus(response(
				"{\"code\":\"RECOVERY_REJECTED\",\"recoveryId\":22}")));
	}

	@Test
	public void mismatchAndApprovalThenSuccessReleaseBothAutomaticGuards()
	{
		PbTrackerPlugin.AutomaticSyncDeduplicator deduplicator =
			new PbTrackerPlugin.AutomaticSyncDeduplicator(30_000L);
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		SyncClient.SyncErrorResponse pending = response(
			"{\"code\":\"RECOVERY_PENDING\",\"recoveryId\":7,\"retryAfterSeconds\":60}"
		);

		assertTrue(deduplicator.tryStart("payload", 1_000L));
		assertTrue(breaker.beginAutomaticAttempt("account", 1_000L).allowed);
		breaker.recordMismatch("account", pending, 1_000L);
		deduplicator.finish("payload", false, 1_000L);

		assertTrue(deduplicator.tryStart("payload", 61_000L));
		assertTrue(breaker.beginAutomaticAttempt("account", 61_000L).allowed);
		breaker.recordSuccess("account");
		deduplicator.finish("payload", true, 61_000L);

		assertFalse(deduplicator.tryStart("payload", 61_001L));
		assertTrue(deduplicator.tryStart("payload", 91_000L));
		assertTrue(breaker.beginAutomaticAttempt("account", 91_000L).allowed);
	}

	@Test
	public void failedRecoveryProbeReleasesClaimsIntoBoundedBackoff()
	{
		PbTrackerPlugin.AutomaticSyncDeduplicator deduplicator =
			new PbTrackerPlugin.AutomaticSyncDeduplicator(30_000L);
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch(
			"account",
			response("{\"code\":\"RECOVERY_PENDING\",\"retryAfterSeconds\":60}"),
			1_000L);

		assertTrue(deduplicator.tryStart("payload", 61_000L));
		assertTrue(breaker.beginAutomaticAttempt("account", 61_000L).allowed);
		deduplicator.finish("payload", false, 61_001L);
		breaker.completeUnsuccessfulAutomaticAttempt("account", 61_001L);

		assertTrue("payload claim is released", deduplicator.tryStart("payload", 61_002L));
		assertFalse("recovery probe is released into short backoff",
			breaker.beginAutomaticAttempt("account", 61_002L).allowed);
		assertEquals(Long.valueOf(59_999L), breaker.millisUntilAutomaticRetry("account", 61_002L));
	}

	@Test
	public void retryDeadlinesSaturateInsteadOfOverflowing()
	{
		InstallRecoveryCircuitBreaker breaker = new InstallRecoveryCircuitBreaker();
		breaker.recordMismatch(
			"account",
			response("{\"code\":\"RECOVERY_PENDING\",\"retryAfterSeconds\":60}"),
			Long.MAX_VALUE - 2_000L);

		assertFalse(breaker.beginAutomaticAttempt("account", Long.MAX_VALUE - 1L).allowed);
		assertNull(breaker.millisUntilAutomaticRetry("account", Long.MAX_VALUE - 1L));
	}
}
