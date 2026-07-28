package com.pbtracker;

import java.util.Objects;

/**
 * Persists the last successful automatic-sync SyncFingerprint per account,
 * so an unchanged payload doesn't repeat the POST across RuneLite restarts
 * (the in-memory AutomaticSyncDeduplicator only covers duplicates within a
 * single running session).
 * <p>
 * Stores through a small ConfigStore seam instead of RuneLite's real
 * ConfigManager directly, so this decision logic is unit-testable without
 * timing or mocking real RuneLite infrastructure.
 * <p>
 * Not atomic: {@link #matches} and {@link #record} are independent calls
 * against the underlying ConfigStore, so a caller doing
 * "check matches(), sync if false, then record()" from two racing threads
 * (e.g. a UI-triggered manual sync racing an automatic sync callback) could
 * interleave. This class has no internal mutable state of its own to
 * protect - configStore is the only field and it's final - so it adds no
 * locking; a caller that needs the check-then-act sequence to be atomic
 * must synchronize around its own call site.
 */
final class PersistedFingerprintStore
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
		// Unlike SyncFingerprint.compute (which normalizes a null
		// accountHash to "" since it must always produce some digest), this
		// class's whole purpose is being an unambiguous key store keyed BY
		// account - a null here would silently collide every caller that
		// passes one under the literal key "syncFingerprint.v1.null", so
		// fail loudly instead.
		Objects.requireNonNull(accountHash, "accountHash");
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
