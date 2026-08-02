package com.pbtracker;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class PersistedRecoveryRetryStoreTest
{
	@Test
	public void storesOnlyDeadlineUnderOpaqueAccountSpecificKey()
	{
		FakeConfigStore config = new FakeConfigStore();
		PersistedRecoveryRetryStore store = new PersistedRecoveryRetryStore(config);

		store.set("123456789", 900_000L);
		String key = PersistedRecoveryRetryStore.configKeyFor("123456789");

		assertFalse(key.contains("123456789"));
		assertEquals("900000", config.values.get(key));
		assertEquals(Long.valueOf(900_000L), store.get("123456789"));
		assertNull(store.get("different-account"));
	}

	@Test
	public void malformedDeadlineFailsOpenAndSuccessCanClearIt()
	{
		FakeConfigStore config = new FakeConfigStore();
		PersistedRecoveryRetryStore store = new PersistedRecoveryRetryStore(config);
		String key = PersistedRecoveryRetryStore.configKeyFor("account-a");
		config.values.put(key, "RECOVERY_PENDING:42:secret");

		assertNull(store.get("account-a"));
		store.clear("account-a");
		assertFalse(config.values.containsKey(key));
	}

	private static final class FakeConfigStore implements PersistedFingerprintStore.ConfigStore
	{
		final Map<String, String> values = new HashMap<>();

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
}
