package com.pbtracker;

import java.util.Objects;

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
	private boolean pendingRefresh;
	private long lastFailureAtMillis = -1;

	/** Called for every LOGGED_IN state the plugin observes. */
	synchronized Decision onLoginState(String currentAccountHash, String displayName, long nowMillis)
	{
		String normalized = normalize(displayName);
		boolean sameSession = isSameSession(currentAccountHash, normalized);

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
	 * loaded-session cache and the error backoff, but still coalesces a
	 * concurrent in-flight request for the same session.
	 */
	synchronized Decision onManualRefresh(String currentAccountHash, String displayName)
	{
		String normalized = normalize(displayName);
		boolean sameSession = isSameSession(currentAccountHash, normalized);
		if (sameSession && inFlight)
		{
			return Decision.SKIP_IN_FLIGHT;
		}

		if (!sameSession)
		{
			startNewSession(currentAccountHash, normalized);
		}

		inFlight = true;
		return Decision.LOAD;
	}

	/**
	 * @return true if a changed-sync refresh was owed (pendingRefresh had
	 * been set) and was just consumed here rather than being fully
	 * satisfied - the caller should immediately start another load instead
	 * of treating the session as up to date.
	 */
	synchronized boolean markLoaded()
	{
		lastFailureAtMillis = -1;
		if (pendingRefresh)
		{
			pendingRefresh = false;
			loaded = false;
			// inFlight stays true - the caller is required to immediately
			// retry, so from the coordinator's point of view a fetch is
			// still ongoing until that retry itself completes.
			return true;
		}
		inFlight = false;
		loaded = true;
		return false;
	}

	synchronized void markError(long nowMillis)
	{
		inFlight = false;
		lastFailureAtMillis = nowMillis;
		pendingRefresh = false;
	}

	/**
	 * A successful sync that changed PB data invalidates the loaded session
	 * result once. If a lookup is already in flight, deferred via
	 * pendingRefresh instead of clearing loaded directly - otherwise that
	 * unrelated in-flight lookup's own markLoaded() would stomp loaded back
	 * to true and silently swallow this refresh for the rest of the session.
	 */
	synchronized void markStaleAfterChangedSync()
	{
		if (inFlight)
		{
			pendingRefresh = true;
		}
		else
		{
			loaded = false;
		}
	}

	/** Logout / login-screen state: cancel any pending retry and clear the session entirely. */
	synchronized void reset()
	{
		accountHash = null;
		normalizedDisplayName = null;
		inFlight = false;
		loaded = false;
		pendingRefresh = false;
		lastFailureAtMillis = -1;
	}

	private void startNewSession(String currentAccountHash, String normalizedDisplayName)
	{
		this.accountHash = currentAccountHash;
		this.normalizedDisplayName = normalizedDisplayName;
		this.inFlight = false;
		this.loaded = false;
		this.pendingRefresh = false;
		this.lastFailureAtMillis = -1;
	}

	private boolean isSameSession(String currentAccountHash, String normalizedDisplayName)
	{
		return Objects.equals(currentAccountHash, accountHash)
			&& Objects.equals(normalizedDisplayName, this.normalizedDisplayName);
	}

	private static String normalize(String displayName)
	{
		return displayName == null ? "" : displayName.trim().toLowerCase();
	}
}
