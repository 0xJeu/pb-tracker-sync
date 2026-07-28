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
	public void failsOpenOnAValueWrittenByAFutureSchemaVersion()
	{
		FakeConfigStore raw = new FakeConfigStore();
		// Simulate what a hypothetical future v2 codebase would have
		// written, stashed directly under a raw "v2" key (bypassing
		// today's configKeyFor, which is pinned to v1). A
		// PersistedFingerprintStore built against today's code looks under
		// the v1-prefixed key, won't find this, and must fail open (return
		// false, i.e. sync normally) rather than misreading a differently
		// shaped value - this is the actual cross-version isolation
		// property the versioned key prefix exists to provide.
		raw.set("syncFingerprint.v2.acct-a", "fingerprint-1");
		PersistedFingerprintStore store = new PersistedFingerprintStore(raw);
		assertFalse(store.matches("acct-a", "fingerprint-1"));
	}
}
