# Persistent Successful-Sync Fingerprint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implements Workstream B ("Persistent Successful-Sync Fingerprints") of the primary app repository's `docs/superpowers/specs/2026-07-24-neon-compute-wake-reduction-design.md`. Stop the plugin from re-sending an automatic login-sync POST after a RuneLite restart when nothing has actually changed since the last successful sync. The existing `AutomaticSyncDeduplicator` already prevents duplicate automatic syncs *within one RuneLite session*, but it's purely in-memory and resets on every restart - this plan adds a second, persisted layer on top.

**Repository Note:** This plan lives in and targets `0xJeu/pb-tracker-sync` (branch `plugin-hub-submission`), reached in this worktree at `OSRS Stuff/worktrees/pb-tracker-sync-fingerprint`. All file paths below are relative to this worktree's root (i.e. `src/main/java/com/pbtracker/...`, not `plugin/src/...`).

**Current state, verified by reading the actual current code (2026-07-28):**
- `buildSyncFingerprint(accountHash, pbs)` (in `PbTrackerPlugin.java`) already exists and computes a deterministic, order-independent identity from `accountHash` + the sorted `{boss: seconds}` map - but it's a raw canonical string, not a SHA-256 digest, and it deliberately excludes display name. Nothing about it is persisted; it's only used as the key into the in-memory `AutomaticSyncDeduplicator`.
- `AutomaticSyncDeduplicator` (in `PbTrackerPlugin.java`) is a purely in-memory `HashSet`/`HashMap`-backed class with `tryStart`/`finish` methods, keyed by fingerprint string, with a 30-second "recently successful" window. It resets on every plugin/RuneLite restart.
- `syncPbs(Map<String,Double> pbs, String statusNote, boolean force, String startedStatus)` is the single choke point every sync path (login-triggered, live-PB, Adventure Log, DT2 scoreboard, manual "Sync all PBs now") funnels through. `force` is `true` only for manual sync; every other caller passes `force=false`.
- `getOrCreateInstallSecret()` is the existing pattern for a per-install opaque persisted value via `configManager.getConfiguration(SETTINGS_GROUP, KEY)`/`setConfiguration(...)`, bypassing the `PbTrackerConfig` `@ConfigItem` interface entirely (i.e., not a user-visible settings-panel field).
- No per-account-keyed persisted config value exists yet in this codebase - this plan establishes that pattern for the first time (a single RuneLite install can, over time, sync more than one account).
- No existing test mocks/fakes `ConfigManager` - `PbTrackerPluginUnitTest.java`'s existing fingerprint/deduplicator tests exercise pure static/instantiable logic directly, with no RuneLite dependency. This plan should preserve that testability by keeping the new persistence-decision logic in a small pure class, injecting the persisted-value store rather than depending on a real `ConfigManager` in tests.

**Architecture:** A new pure-Java `PersistedSyncFingerprint` value type (SHA-256 hex digest, not the existing raw canonical string - see Task 1 for why) computed from account identity + display name + boss keys + PB values, matching the spec's exact field list (this is a deliberately different, stricter fingerprint than `buildSyncFingerprint`, which the design intentionally excludes display name from for an unrelated reason - the two coexist for different purposes, do not try to unify them). A small persistence helper reads/writes one opaque `ConfigManager` value per account (keyed by account hash), storing the fingerprint plus a schema version. `syncPbs` consults this before sending an automatic (non-`force`) sync, and updates it only after a genuinely successful (`response.isSuccessful()`) accepted response.

**Tech Stack:** Java 17, Gradle/RuneLite plugin template, JUnit 4 (matches `PbTrackerPluginUnitTest`), `java.security.MessageDigest` for SHA-256 (no new dependency needed - it's JDK-standard).

**Post-review integration amendment (2026-07-31):** After PR #16 merged, the
final implementation was updated to retain its account-aware local-profile
coordinator alongside this persisted fingerprint gate. A production-used
`PersistentSyncCoordinator` seam now owns the combined completion behavior so
tests prove automatic skip/manual bypass, failure and 409 non-recording, and a
successful response recording the fingerprint while requesting PR #16's
profile refresh. Review also hardened config keys to use a domain-separated
SHA-256 account suffix instead of exposing the raw account hash, and made
display-name normalization locale-stable with `Locale.ROOT`. Where older task
snippets below show a raw account-hash suffix or direct callback wiring, this
amendment and the final source are authoritative.

**Before starting:**
```bash
cd "OSRS Stuff/worktrees/pb-tracker-sync-fingerprint"
JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle test
```
Expected: clean baseline pass (confirmed before this plan was written).

---

### Task 1: `SyncFingerprint` — deterministic SHA-256 digest, pure decision logic

**Files:**
- Create: `src/main/java/com/pbtracker/SyncFingerprint.java`
- Test: `src/test/java/com/pbtracker/SyncFingerprintTest.java`

The spec requires the fingerprint built from: normalized account identity key, normalized display name, canonical boss keys sorted lexicographically, normalized numeric PB values - and to be a SHA-256 digest specifically (not a raw string like the existing `buildSyncFingerprint`, which is a different mechanism serving the in-memory deduplicator and deliberately excludes display name "without putting a secret or display name into the key"). This task's fingerprint is used only for the *persisted* comparison and needs to detect a display-name change as "changed" too (per spec rule: "A display-name change produces a new fingerprint and syncs normally"), which is exactly why it can't reuse `buildSyncFingerprint` as-is.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/pbtracker/SyncFingerprintTest.java
package com.pbtracker;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SyncFingerprintTest
{
	@Test
	public void isIndependentOfMapIterationOrder()
	{
		Map<String, Double> a = new LinkedHashMap<>();
		a.put("zulrah", 42.6);
		a.put("vorkath", 78.0);

		Map<String, Double> b = new LinkedHashMap<>();
		b.put("vorkath", 78.0);
		b.put("zulrah", 42.6);

		assertEquals(
			SyncFingerprint.compute("acct-a", "Zezima", a),
			SyncFingerprint.compute("acct-a", "Zezima", b));
	}

	@Test
	public void producesA64CharacterHexDigest()
	{
		String fingerprint = SyncFingerprint.compute("acct-a", "Zezima", new TreeMap<>());
		assertEquals(64, fingerprint.length());
		assertTrue(fingerprint.matches("[0-9a-f]{64}"));
	}

	@Test
	public void changesWhenAccountHashChanges()
	{
		Map<String, Double> pbs = new TreeMap<>();
		pbs.put("zulrah", 42.6);

		assertNotEquals(
			SyncFingerprint.compute("acct-a", "Zezima", pbs),
			SyncFingerprint.compute("acct-b", "Zezima", pbs));
	}

	@Test
	public void changesWhenDisplayNameChanges()
	{
		Map<String, Double> pbs = new TreeMap<>();
		pbs.put("zulrah", 42.6);

		assertNotEquals(
			SyncFingerprint.compute("acct-a", "Zezima", pbs),
			SyncFingerprint.compute("acct-a", "ZezimaTwo", pbs));
	}

	@Test
	public void normalizesDisplayNameCaseAndWhitespace()
	{
		Map<String, Double> pbs = new TreeMap<>();
		pbs.put("zulrah", 42.6);

		assertEquals(
			SyncFingerprint.compute("acct-a", "Zezima", pbs),
			SyncFingerprint.compute("acct-a", "  ZEZIMA  ", pbs));
	}

	@Test
	public void changesWhenAPbValueChanges()
	{
		Map<String, Double> a = new TreeMap<>();
		a.put("zulrah", 42.6);

		Map<String, Double> b = new TreeMap<>();
		b.put("zulrah", 42.5);

		assertNotEquals(
			SyncFingerprint.compute("acct-a", "Zezima", a),
			SyncFingerprint.compute("acct-a", "Zezima", b));
	}

	@Test
	public void changesWhenABossKeyIsAddedOrRemoved()
	{
		Map<String, Double> a = new TreeMap<>();
		a.put("zulrah", 42.6);

		Map<String, Double> b = new TreeMap<>();
		b.put("zulrah", 42.6);
		b.put("vorkath", 78.0);

		assertNotEquals(
			SyncFingerprint.compute("acct-a", "Zezima", a),
			SyncFingerprint.compute("acct-a", "Zezima", b));
	}

	@Test
	public void isStableAcrossRepeatedCallsWithTheSameInput()
	{
		Map<String, Double> pbs = new TreeMap<>();
		pbs.put("zulrah", 42.6);

		assertEquals(
			SyncFingerprint.compute("acct-a", "Zezima", pbs),
			SyncFingerprint.compute("acct-a", "Zezima", pbs));
	}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle test --tests "com.pbtracker.SyncFingerprintTest"`
Expected: FAIL with "cannot find symbol: class SyncFingerprint"

- [ ] **Step 3: Write the implementation**

```java
// src/main/java/com/pbtracker/SyncFingerprint.java
package com.pbtracker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * A SHA-256 digest identifying an exact (account, display name, PB set)
 * combination, used to decide whether an automatic sync payload has
 * actually changed since the last successful sync - including across
 * RuneLite restarts, unlike PbTrackerPlugin's in-memory
 * AutomaticSyncDeduplicator (which this class does not replace; the two
 * serve different lifetimes and PbTrackerPlugin.buildSyncFingerprint
 * deliberately excludes display name for its own, unrelated reason).
 */
final class SyncFingerprint
{
	private SyncFingerprint()
	{
	}

	static String compute(String accountHash, String displayName, Map<String, Double> pbs)
	{
		StringBuilder canonical = new StringBuilder();
		canonical.append(accountHash == null ? "" : accountHash).append('|');
		canonical.append(normalizeDisplayName(displayName)).append('|');
		for (Map.Entry<String, Double> entry : new TreeMap<>(pbs).entrySet())
		{
			String key = entry.getKey();
			canonical.append(key.length())
				.append(':')
				.append(key)
				.append('=')
				.append(Long.toHexString(Double.doubleToLongBits(entry.getValue())))
				.append(';');
		}

		return sha256Hex(canonical.toString());
	}

	private static String normalizeDisplayName(String displayName)
	{
		return displayName == null ? "" : displayName.trim().toLowerCase();
	}

	private static String sha256Hex(String input)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash)
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			// SHA-256 is a mandatory JDK algorithm (JLS/JCA guarantee) - this
			// is unreachable in practice, but fail loudly rather than
			// returning a bogus fingerprint that could silently disable the
			// skip-unchanged-sync optimization forever.
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle test --tests "com.pbtracker.SyncFingerprintTest"`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pbtracker/SyncFingerprint.java src/test/java/com/pbtracker/SyncFingerprintTest.java
git commit -m "feat: add persisted-sync SHA-256 fingerprint"
```

---

### Task 2: `PersistedFingerprintStore` — pure decision logic over an injected key-value store

**Files:**
- Create: `src/main/java/com/pbtracker/PersistedFingerprintStore.java`
- Test: `src/test/java/com/pbtracker/PersistedFingerprintStoreTest.java`

Keep the "does the stored value match, and should we skip" decision logic testable without a real RuneLite `ConfigManager`, by defining a tiny storage interface this class depends on, and wiring it to real `ConfigManager` calls only in Task 3 (`PbTrackerPlugin.java`).

Per spec:
- Automatic sync with the same fingerprint: skip the POST.
- A live PB change, or a display-name change, produces a new fingerprint and syncs normally (this falls out naturally, since `SyncFingerprint.compute` already changes for either case, per Task 1).
- Manual "Sync all PBs now" bypasses the fingerprint check entirely (this is already true today via the existing `force` flag - Task 3 just needs to not call this class at all when `force` is true, same pattern as the existing `AutomaticSyncDeduplicator` check).
- A failed or rejected request never updates the stored fingerprint.
- A future fingerprint schema version change must fail open (treat as no stored value, i.e. sync normally) rather than crash or permanently block syncing.
- Install-recovery flows bypass or clear the fingerprint when required (this plan does not know the exact install-recovery mechanism in this codebase - Task 3 investigates and wires this up; this task just needs a `clear` method available for that purpose).

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/pbtracker/PersistedFingerprintStoreTest.java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle test --tests "com.pbtracker.PersistedFingerprintStoreTest"`
Expected: FAIL - class doesn't exist yet.

- [ ] **Step 3: Write the implementation**

```java
// src/main/java/com/pbtracker/PersistedFingerprintStore.java
package com.pbtracker;

/**
 * Persists the last successful automatic-sync SyncFingerprint per account,
 * so an unchanged payload doesn't repeat the POST across RuneLite restarts
 * (the in-memory AutomaticSyncDeduplicator only covers duplicates within a
 * single running session).
 * <p>
 * Stores through a small ConfigStore seam instead of RuneLite's real
 * ConfigManager directly, so this decision logic is unit-testable without
 * timing or mocking real RuneLite infrastructure.
 */
class PersistedFingerprintStore
{
	private static final String KEY_PREFIX = "syncFingerprint.v1.";

	/** Minimal storage seam this class needs - implemented against RuneLite's real ConfigManager in PbTrackerPlugin. */
	interface ConfigStore
	{
		String get(String key);

		void set(String key, String value);

		void unset(String key);
	}

	private final ConfigStore configStore;

	PersistedFingerprintStore(ConfigStore configStore)
	{
		this.configStore = configStore;
	}

	static String configKeyFor(String accountHash)
	{
		// accountHash is itself an opaque numeric-ish identifier (not a
		// secret, not a display name), safe to use directly as a config key
		// suffix - matches how the rest of this plugin already treats it
		// (e.g. logged at debug level elsewhere without redaction concerns).
		return KEY_PREFIX + accountHash;
	}

	/** True if the given fingerprint matches what's stored for this account - i.e. nothing has changed since the last successful sync. */
	boolean matches(String accountHash, String fingerprint)
	{
		String stored = configStore.get(configKeyFor(accountHash));
		// A schema-version bump changes KEY_PREFIX, so an old value under a
		// different prefix simply won't be found here - this get() call
		// itself is the fail-open mechanism for a version change. A
		// same-prefix but otherwise-unparseable value also fails open,
		// since the stored format is a bare fingerprint string with no
		// further parsing to fail - any non-null, non-matching value just
		// falls through to the final inequality check below.
		return stored != null && stored.equals(fingerprint);
	}

	/** Records a fingerprint as the current known-successful state for this account. */
	void record(String accountHash, String fingerprint)
	{
		configStore.set(configKeyFor(accountHash), fingerprint);
	}

	/** Clears any stored fingerprint for this account (e.g. for install-recovery flows). */
	void clear(String accountHash)
	{
		configStore.unset(configKeyFor(accountHash));
	}
}
```

Note on the "fails open on an unparseable stored value" test: since the stored value is just a bare fingerprint string (not JSON, no further structure), an "unparseable" value is simply a non-matching string, which `matches()` already handles correctly via the final `.equals()` check - there's no separate parsing step that could throw. If you find yourself wanting to store something more structured (e.g. JSON with a schema version field), stop and reconsider: the simpler bare-string-under-a-versioned-key-prefix design already satisfies every spec requirement (schema version changes are handled by the key prefix itself) without needing a JSON library dependency. Don't add one.

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle test --tests "com.pbtracker.PersistedFingerprintStoreTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pbtracker/PersistedFingerprintStore.java src/test/java/com/pbtracker/PersistedFingerprintStoreTest.java
git commit -m "feat: add persisted per-account fingerprint store with fail-open versioning"
```

---

### Task 3: Wire the persisted fingerprint into `syncPbs`

**Files:**
- Modify: `src/main/java/com/pbtracker/PbTrackerPlugin.java`

**Before starting this task:** read the actual current state of `syncPbs`, `startUp()`, and the class's field declarations yourself (line numbers below are from the worktree's current `HEAD` at plan-writing time and may have drifted slightly - the surrounding structure should still match closely since this branch has had no other changes since Tasks 1-2 of this plan, which don't touch this file).

**Current code** (`syncPbs`, in `PbTrackerPlugin.java`):

```java
private void syncPbs(Map<String, Double> pbs, String statusNote, boolean force, String startedStatus)
{
	if (pbs.isEmpty() || client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
	{
		return;
	}

	String name = client.getLocalPlayer().getName();
	String hash = accountHash != null ? accountHash : String.valueOf(client.getAccountHash());
	String suffix = statusNote != null ? " " + statusNote : "";
	String fingerprint = force ? null : buildSyncFingerprint(hash, pbs);

	if (fingerprint != null && !automaticSyncDeduplicator.tryStart(fingerprint, System.currentTimeMillis()))
	{
		log.debug("Suppressing duplicate automatic PB sync for {} PB(s)", pbs.size());
		return;
	}
	if (startedStatus != null)
	{
		setStatus(startedStatus);
	}

	try
	{
		syncClient.sync(hash, name, pbs, installSecret, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				if (fingerprint != null)
				{
					automaticSyncDeduplicator.finish(fingerprint, false, System.currentTimeMillis());
				}
				log.warn("PB sync failed", e);
				setStatus("Sync failed: " + e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				boolean successful = response.isSuccessful();
				try
				{
					if (successful)
					{
						setStatus("Last updated: " + TIMESTAMP_FORMAT.format(LocalDateTime.now()) + suffix);
						// ... (Workstream A's markStaleAfterChangedSync/loadLocalPlayerPanelWhenReady logic - untouched by this task)
					}
					else if (response.code() == 409)
					{
						setStatus("Sync rejected: this account is already synced from a different install.");
					}
					else
					{
						setStatus("Server responded with error " + response.code());
					}
				}
				finally
				{
					if (fingerprint != null)
					{
						automaticSyncDeduplicator.finish(fingerprint, successful, System.currentTimeMillis());
					}
					response.close();
				}
			}
		});
	}
	catch (RuntimeException ex)
	{
		if (fingerprint != null)
		{
			automaticSyncDeduplicator.finish(fingerprint, false, System.currentTimeMillis());
		}
		throw ex;
	}
}
```

- [ ] **Step 1: Add the persisted store field and its real `ConfigStore` adapter**

Near the existing `automaticSyncDeduplicator` field declaration, add:

```java
	private final PersistedFingerprintStore persistedFingerprintStore =
		new PersistedFingerprintStore(new PersistedFingerprintStore.ConfigStore()
		{
			@Override
			public String get(String key)
			{
				return configManager.getConfiguration(SETTINGS_GROUP, key);
			}

			@Override
			public void set(String key, String value)
			{
				configManager.setConfiguration(SETTINGS_GROUP, key, value);
			}

			@Override
			public void unset(String key)
			{
				configManager.unsetConfiguration(SETTINGS_GROUP, key);
			}
		});
```

Confirm `ConfigManager` actually has an `unsetConfiguration(String group, String key)` method in the RuneLite API version this project depends on (check an existing usage elsewhere in the RuneLite client API, or the RuneLite client jar directly via `javap` the way Task 4 of the earlier local-profile-load-coordinator plan verified `FontManager.getRunescapeSmallFont()` - don't guess). If no such method exists, use `setConfiguration(group, key, "")` as the "unset" behavior instead and adjust `PersistedFingerprintStore.clear()`'s contract/tests accordingly (an empty string will never equal a real 64-character hex fingerprint, so this is behaviorally equivalent for `matches()`'s purposes) - note which approach you used in your task report.

- [ ] **Step 2: Consult the store before sending an automatic sync**

Change the fingerprint computation and gating logic:

```java
	String name = client.getLocalPlayer().getName();
	String hash = accountHash != null ? accountHash : String.valueOf(client.getAccountHash());
	String suffix = statusNote != null ? " " + statusNote : "";
	String fingerprint = force ? null : buildSyncFingerprint(hash, pbs);
	String persistedFingerprint = force ? null : SyncFingerprint.compute(hash, name, pbs);

	if (fingerprint != null && !automaticSyncDeduplicator.tryStart(fingerprint, System.currentTimeMillis()))
	{
		log.debug("Suppressing duplicate automatic PB sync for {} PB(s)", pbs.size());
		return;
	}
	if (persistedFingerprint != null && persistedFingerprintStore.matches(hash, persistedFingerprint))
	{
		log.debug("Skipping automatic PB sync for {} PB(s): unchanged since last successful sync", pbs.size());
		if (fingerprint != null)
		{
			// Release the in-memory dedup slot claimed just above - this
			// sync isn't actually happening, so tryStart's reservation
			// must not linger and block a later, genuinely different
			// automatic sync attempt within the same session.
			automaticSyncDeduplicator.finish(fingerprint, false, System.currentTimeMillis());
		}
		setStatus("No PB changes since last successful sync" + suffix);
		return;
	}
	if (startedStatus != null)
	{
		setStatus(startedStatus);
	}
```

Note the ordering: the in-memory `automaticSyncDeduplicator.tryStart` check runs first (unchanged from today), and the NEW persisted-fingerprint check runs second, only if the first check didn't already bail. This preserves 100% of today's within-session behavior and adds the persisted, cross-restart check as an additional gate. The `automaticSyncDeduplicator.finish(..., false, ...)` call in the new branch is important - without it, `tryStart`'s reservation of the fingerprint slot would never be released for this call, since normally only the network callback's `onFailure`/`onResponse`/the outer `catch` releases it, none of which run on this early-return path.

- [ ] **Step 3: Record the fingerprint only after a genuinely successful, accepted response**

In `onResponse`'s `successful` branch, after the existing `setStatus(...)` call (and alongside Workstream A's `markStaleAfterChangedSync`/reload logic, which this task must not disturb - just add to the same branch):

```java
if (successful)
{
	setStatus("Last updated: " + TIMESTAMP_FORMAT.format(LocalDateTime.now()) + suffix);
	if (persistedFingerprint != null)
	{
		persistedFingerprintStore.record(hash, persistedFingerprint);
	}
	SyncClient.SyncResponseDto outcome = syncClient.parseSyncResponse(response);
	// ... existing markStaleAfterChangedSync/loadLocalPlayerPanelWhenReady logic, untouched
}
```

Do NOT record on the `409` branch or the generic `else` (error) branch, and do NOT record on `onFailure` or the outer `catch (RuntimeException ex)` - only the true `successful` path. This matches the spec's "A failed or rejected request never updates the stored fingerprint" requirement, and falls out naturally from placing the `record(...)` call only inside the existing `if (successful)` block.

- [ ] **Step 4: Manual sync already bypasses this correctly - verify, don't re-implement**

Confirm (by tracing, not by adding new code) that `force=true` (manual "Sync all PBs now") already makes `persistedFingerprint` `null` via the ternary in Step 2, which skips both the `matches(...)` check and the `record(...)` call inside `onResponse` (guarded by `if (persistedFingerprint != null)`). This means a manual sync's *result* never overwrites the persisted fingerprint either - re-check this against the spec: *"Manual 'Sync all PBs now' bypasses the fingerprint check"* (confirmed - it skips the check) but says nothing about whether a manual sync should also *update* the stored fingerprint for future automatic syncs to compare against. Consider both readings and pick the one that best serves the feature's purpose (skipping *unnecessary* automatic syncs) - a manual sync that succeeds represents exactly the same "known good state" an automatic sync's success would, so leaving the persisted fingerprint stale after a successful manual sync would cause the *next* automatic sync to redundantly re-send data that manual sync just confirmed is correct. If you agree, extend Step 3's `record(...)` call to *also* run when `force` was true and the sync succeeded (i.e., always record the current fingerprint - computed via `SyncFingerprint.compute(hash, name, pbs)` regardless of `force` - on any successful response, not just automatic ones). Recompute a non-null `persistedFingerprint` unconditionally for this purpose (separately from the `force`-gated one used for the skip-check), or restructure slightly - use your judgment, but document your reasoning in the commit message either way, and add a test (Task 4) covering whichever behavior you chose.

- [ ] **Step 5: Compile and run the full test suite**

Run:
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle compileJava
JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle test
```
Expected: BUILD SUCCESSFUL, no regressions in any existing test.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/pbtracker/PbTrackerPlugin.java
git commit -m "feat: skip an unchanged automatic sync payload across RuneLite restarts"
```

---

### Task 4: Integration-level tests for the wiring

**Files:**
- Modify: `src/test/java/com/pbtracker/PbTrackerPluginUnitTest.java` (or a new focused test file if that one is already large - check its current line count first and use judgment)

Task 3 wires real behavior into `PbTrackerPlugin.syncPbs`, which (per prior work on this branch/repo) has no existing test scaffolding for constructing a real `PbTrackerPlugin` with mocked `client`/`configManager`/`executor` fields. Check whether that's still true before starting this task (grep for any `new PbTrackerPlugin(` in test files, and check if a mocking library like Mockito is already a test dependency in `build.gradle`).

- [ ] **Step 1: If real integration testing is impractical, test at the boundary instead**

If no scaffolding exists and building it would require disproportionate effort (the standard this project has applied consistently - e.g. earlier stale-account-hash and in-flight-guard fixes on this same repo accepted this same limitation), it's acceptable to skip full `PbTrackerPlugin`-level integration tests. Instead, ensure the following is true and write a short explanation in your task report:
- `SyncFingerprintTest` (Task 1) and `PersistedFingerprintStoreTest` (Task 2) together fully cover the decision logic in isolation.
- Manually trace (in your report, not as a test) the exact call sequence for: (a) two identical automatic syncs across a simulated "restart" (construct two separate `PersistedFingerprintStore` instances backed by the same `FakeConfigStore` instance, simulating persistence surviving a restart, and confirm the second `matches()` call returns `true` for the same fingerprint) - this can actually be a real, valuable test without touching `PbTrackerPlugin` at all:

```java
@Test
public void simulatesAnUnchangedFingerprintSurvivingARestart()
{
	PersistedFingerprintStore.ConfigStore sharedBackingStore = new FakeConfigStore();

	// First "session"
	PersistedFingerprintStore sessionOne = new PersistedFingerprintStore(sharedBackingStore);
	String fingerprint = SyncFingerprint.compute("acct-a", "Zezima", singleBossPbMap("zulrah", 42.6));
	sessionOne.record("acct-a", fingerprint);

	// Simulated RuneLite restart: a brand new PersistedFingerprintStore
	// instance, but backed by the same persisted config values.
	PersistedFingerprintStore sessionTwo = new PersistedFingerprintStore(sharedBackingStore);
	assertTrue(sessionTwo.matches("acct-a", fingerprint));
}
```

Add this to `PersistedFingerprintStoreTest.java` (it fits naturally there - it's still testing the store's contract, just emphasizing the restart-survival property explicitly called out in the spec's own required test list: "unchanged fingerprint skips automatic POST across restart").

- [ ] **Step 2: Run the full suite**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle test`

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/pbtracker/PersistedFingerprintStoreTest.java
git commit -m "test: verify a persisted fingerprint survives a simulated restart"
```

---

### Task 5: Full validation and PR

**Files:** none (verification only)

- [ ] **Step 1: Run the full required test/build suite**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle test
JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle build
```
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 2: Manual live check (optional but recommended given this touches real sync behavior)**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle run`. Log in, let an automatic sync complete once, then trigger a second automatic sync path (e.g. toggle a setting that forces a re-sync attempt, or restart the plugin if that's easier than restarting the whole client) without any PB changes - confirm via debug logs that the second attempt is skipped with the "unchanged since last successful sync" status, rather than re-POSTing.

- [ ] **Step 3: Push and open a PR against `plugin-hub-submission`**

```bash
git push -u origin persistent-sync-fingerprint
gh pr create --repo 0xJeu/pb-tracker-sync --base plugin-hub-submission --title "Skip unchanged automatic sync payloads across RuneLite restarts" --body "Implements Workstream B of 0xJeu/osrs-pb-tracker's docs/superpowers/specs/2026-07-24-neon-compute-wake-reduction-design.md. Adds a persisted per-account SHA-256 fingerprint (SyncFingerprint + PersistedFingerprintStore) so an automatic login sync whose payload hasn't changed since the last successful sync is skipped entirely, rather than repeating a no-op POST after every RuneLite restart. Manual 'Sync all PBs now' continues to bypass this check via the existing force flag. A failed or rejected sync never updates the stored fingerprint."
```

Per the rollout plan, publishing this to end users additionally requires, **after this PR merges** (and independently of Workstream A's PR #16, which can merge before or after this one - they touch different, non-overlapping parts of the same files where they do overlap, e.g. `syncPbs`, so rebase/merge conflicts are possible and expected if both are open simultaneously; resolve by re-reading both diffs rather than blindly taking one side): bumping the pinned commit hash in the `plugin-hub` fork's `plugins/pb-tracker-sync` manifest, then opening a PR against `runelite/plugin-hub`.

---

## Self-Review Notes

- **Spec coverage (Validation → Plugin, required automated cases relevant to this workstream):** unchanged fingerprint skips automatic POST across restart (✓ Task 2 wiring, Task 4 restart-simulation test), failed POST does not persist the fingerprint (✓ Task 3 Step 3, only records inside the `successful` branch), manual sync bypasses fingerprint state (✓ Task 3 Step 2's `force` ternary, verified not re-implemented in Step 4). The other required cases from the full 12-item list (repeated LOGGED_IN, world hop, account switch, in-flight coalescing, name change, error backoff, manual refresh coalescing, changed-sync refresh) belong to Workstream A, already shipped separately (PR #16).
- **Deliberately separate from `buildSyncFingerprint`:** this plan introduces `SyncFingerprint` as a new, distinct class rather than modifying or reusing the existing `buildSyncFingerprint`/`AutomaticSyncDeduplicator`, because they serve different purposes (in-session duplicate suppression vs. cross-restart no-op suppression) and the existing one deliberately excludes display name for a reason unrelated to this workstream's needs (a display-name change must be treated as "changed" here, per spec, but the existing fingerprint doesn't include it at all). Do not attempt to unify them without re-reading both design rationales first.
- **No placeholders:** all steps contain complete code except one deliberate open judgment call (Task 3 Step 4, manual sync also updating the persisted fingerprint) which is intentionally left for the implementer to reason through and document, rather than mandated outright, since the spec text is genuinely ambiguous on this point.
- **Type/API risk flagged for the implementer:** `ConfigManager.unsetConfiguration`'s exact existence/signature (Task 3 Step 1) should be verified against the real RuneLite client jar, not assumed.
