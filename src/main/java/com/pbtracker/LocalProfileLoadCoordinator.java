package com.pbtracker;

/**
 * Decides whether the "My PBs" sidebar should issue a fresh
 * GET /api/players/:name lookup, independent of the syncOnLogin-gated
 * automatic sync scheduler. RuneLite can publish LOGGED_IN more than once
 * per account session (startup, world hops, reconnects), and without this
 * gate every one of those re-triggers a lookup thread.
 * <p>
 * Deliberately has no RuneLite/Swing dependency so it can be unit-tested
 * without timing real UI threads - mirrors PbTrackerPlugin.LoginSyncSession.
 */
class LocalProfileLoadCoordinator
{
	static final long ERROR_BACKOFF_MILLIS = 30_000;

	enum Decision
	{
		LOAD,
		SKIP_ALREADY_LOADED,
		SKIP_IN_FLIGHT,
		SKIP_BACKOFF
	}

	private String accountHash;
	private String normalizedDisplayName;
	private boolean inFlight;
	private boolean loaded;
	private long lastFailureAtMillis = -1;

	/** Called for every LOGGED_IN state the plugin observes. */
	synchronized Decision onLoginState(String currentAccountHash, String displayName, long nowMillis)
	{
		String normalized = normalize(displayName);
		boolean sameSession = currentAccountHash.equals(accountHash) && normalized.equals(normalizedDisplayName);

		if (!sameSession)
		{
			startNewSession(currentAccountHash, normalized);
		}

		if (inFlight)
		{
			return Decision.SKIP_IN_FLIGHT;
		}
		if (loaded)
		{
			return Decision.SKIP_ALREADY_LOADED;
		}
		if (lastFailureAtMillis >= 0 && nowMillis - lastFailureAtMillis < ERROR_BACKOFF_MILLIS)
		{
			return Decision.SKIP_BACKOFF;
		}

		inFlight = true;
		return Decision.LOAD;
	}

	/**
	 * Called by the explicit "Refresh My PBs" action. Bypasses the
	 * loaded-session cache but still coalesces a concurrent in-flight request.
	 */
	synchronized Decision onManualRefresh(String currentAccountHash, String displayName)
	{
		String normalized = normalize(displayName);
		boolean sameSession = currentAccountHash.equals(accountHash) && normalized.equals(normalizedDisplayName);
		if (sameSession && inFlight)
		{
			return Decision.SKIP_IN_FLIGHT;
		}

		accountHash = currentAccountHash;
		normalizedDisplayName = normalized;
		inFlight = true;
		return Decision.LOAD;
	}

	synchronized void markLoaded()
	{
		inFlight = false;
		loaded = true;
	}

	synchronized void markError(long nowMillis)
	{
		inFlight = false;
		lastFailureAtMillis = nowMillis;
	}

	/** A successful sync that changed PB data invalidates the loaded session result once. */
	synchronized void markStaleAfterChangedSync()
	{
		loaded = false;
	}

	/** Logout / login-screen state: cancel any pending retry and clear the session entirely. */
	synchronized void reset()
	{
		accountHash = null;
		normalizedDisplayName = null;
		inFlight = false;
		loaded = false;
		lastFailureAtMillis = -1;
	}

	private void startNewSession(String currentAccountHash, String normalizedDisplayName)
	{
		this.accountHash = currentAccountHash;
		this.normalizedDisplayName = normalizedDisplayName;
		this.inFlight = false;
		this.loaded = false;
		this.lastFailureAtMillis = -1;
	}

	private static String normalize(String displayName)
	{
		return displayName == null ? "" : displayName.trim().toLowerCase();
	}
}
