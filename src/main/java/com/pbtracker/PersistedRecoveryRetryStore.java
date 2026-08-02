package com.pbtracker;

import java.util.Collections;

/** Persists only the next passive recovery-attempt timestamp under an opaque account key. */
final class PersistedRecoveryRetryStore implements InstallRecoveryRetryCoordinator.DeadlineStore
{
	private static final String KEY_PREFIX = "recoveryRetryAt.v1.";
	private final PersistedFingerprintStore.ConfigStore configStore;

	PersistedRecoveryRetryStore(PersistedFingerprintStore.ConfigStore configStore)
	{
		this.configStore = configStore;
	}

	@Override
	public Long get(String accountHash)
	{
		String stored = configStore.get(configKeyFor(accountHash));
		if (stored == null)
		{
			return null;
		}
		try
		{
			long parsed = Long.parseLong(stored);
			return parsed >= 0L ? parsed : null;
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	@Override
	public void set(String accountHash, long deadlineMillis)
	{
		configStore.set(configKeyFor(accountHash), String.valueOf(deadlineMillis));
	}

	@Override
	public void clear(String accountHash)
	{
		configStore.unset(configKeyFor(accountHash));
	}

	static String configKeyFor(String accountHash)
	{
		return KEY_PREFIX + SyncFingerprint.compute(
			accountHash, "install-recovery-retry", Collections.emptyMap());
	}
}
