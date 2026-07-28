package com.pbtracker;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersistedFingerprintStoreTest
{
	/** In-memory stand-in for RuneLite's ConfigManager, scoped to this plugin's settings group. */
	private static class FakeConfigStore implements PersistedFingerprintStore.ConfigStore
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

	@Test
	public void reportsNoStoredValueForANeverSyncedAccount()
	{
		PersistedFingerprintStore store = new PersistedFingerprintStore(new FakeConfigStore());
		assertFalse(store.matches("acct-a", "fingerprint-1"));
	}

	@Test
	public void reportsAMatchAfterRecordingTheSameFingerprint()
	{
		PersistedFingerprintStore store = new PersistedFingerprintStore(new FakeConfigStore());
		store.record("acct-a", "fingerprint-1");
		assertTrue(store.matches("acct-a", "fingerprint-1"));
	}

	@Test
	public void reportsNoMatchForADifferentFingerprint()
	{
		PersistedFingerprintStore store = new PersistedFingerprintStore(new FakeConfigStore());
		store.record("acct-a", "fingerprint-1");
		assertFalse(store.matches("acct-a", "fingerprint-2"));
	}

	@Test
	public void keepsFingerprintsSeparatePerAccount()
	{
		PersistedFingerprintStore store = new PersistedFingerprintStore(new FakeConfigStore());
		store.record("acct-a", "fingerprint-1");
		assertFalse(store.matches("acct-b", "fingerprint-1"));
	}

	@Test
	public void clearRemovesTheStoredValue()
	{
		PersistedFingerprintStore store = new PersistedFingerprintStore(new FakeConfigStore());
		store.record("acct-a", "fingerprint-1");
		store.clear("acct-a");
		assertFalse(store.matches("acct-a", "fingerprint-1"));
	}

	@Test
	public void failsOpenOnAnUnparseableStoredValue()
	{
		PersistedFingerprintStore.ConfigStore raw = new FakeConfigStore();
		// Simulate a corrupted or future-schema-version value that this
		// version of the plugin can't parse - matches() must return false
		// (fail open: sync normally) rather than throw.
		raw.set(PersistedFingerprintStore.configKeyFor("acct-a"), "not-valid-json-or-schema");
		PersistedFingerprintStore store = new PersistedFingerprintStore(raw);
		assertFalse(store.matches("acct-a", "fingerprint-1"));
	}
}
