package com.pbtracker;

import java.util.Objects;

/**
 * Coordinates the persisted fingerprint gate with the account-aware local
 * profile refresh state. Keeping this boundary independent of RuneLite and
 * OkHttp makes the combined PR #16/PR #17 behavior directly testable.
 */
final class PersistentSyncCoordinator
{
	private final PersistedFingerprintStore fingerprintStore;
	private final LocalProfileLoadCoordinator localProfileLoadCoordinator;

	PersistentSyncCoordinator(
		PersistedFingerprintStore fingerprintStore,
		LocalProfileLoadCoordinator localProfileLoadCoordinator)
	{
		this.fingerprintStore = fingerprintStore;
		this.localProfileLoadCoordinator = localProfileLoadCoordinator;
	}

	boolean shouldSkip(boolean force, String accountHash, String fingerprint)
	{
		return !force && fingerprintStore.matches(accountHash, fingerprint);
	}

	/**
	 * Applies a completed network attempt. Only an accepted response records
	 * the fingerprint or advances the local-profile coordinator.
	 *
	 * @return true when the plugin should start the normal local-profile load
	 */
	boolean complete(
		boolean accepted,
		String requestAccountHash,
		String currentAccountHash,
		String fingerprint,
		SyncClient.SyncResponseDto outcome)
	{
		if (!accepted)
		{
			return false;
		}

		// The stored value is derived from the request payload, not from the
		// earlier matches() result, so concurrent manual/automatic completions
		// are benign last-writer-wins rather than a read-modify-write race.
		fingerprintStore.record(requestAccountHash, fingerprint);

		return Objects.equals(requestAccountHash, currentAccountHash)
			&& PbTrackerPlugin.isNonDeduplicatedSuccessfulSyncOutcome(outcome)
			&& localProfileLoadCoordinator.requestRefreshAfterSuccessfulSync(
				PbTrackerPlugin.shouldRefreshLocalProfileAfterSync(outcome));
	}
}
