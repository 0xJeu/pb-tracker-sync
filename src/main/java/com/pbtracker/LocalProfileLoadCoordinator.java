package com.pbtracker;

import java.util.Locale;
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
	private boolean profileMayChangeAfterSuccessfulSync;
	private boolean successfulSyncObservedDuringLoad;
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
		return markLoaded(false);
	}

	/**
	 * Records a successful lookup.
	 *
	 * @param profileMayChangeAfterSuccessfulSync true when this response
	 * indicates that a later successful sync could make the lookup resolve
	 * differently (for example NOT_FOUND or a stale returned display name)
	 * @return true when the caller must immediately retry the lookup
	 */
	synchronized boolean markLoaded(boolean profileMayChangeAfterSuccessfulSync)
	{
		lastFailureAtMillis = -1;
		boolean retry = pendingRefresh
			|| successfulSyncObservedDuringLoad && profileMayChangeAfterSuccessfulSync;
		pendingRefresh = false;
		successfulSyncObservedDuringLoad = false;
		if (retry)
		{
			loaded = false;
			this.profileMayChangeAfterSuccessfulSync = false;
			// inFlight stays true - the caller is required to immediately
			// retry, so from the coordinator's point of view a fetch is
			// still ongoing until that retry itself completes.
			return true;
		}
		inFlight = false;
		loaded = true;
		this.profileMayChangeAfterSuccessfulSync = profileMayChangeAfterSuccessfulSync;
		return false;
	}

	/**
	 * @return true when a successful sync arrived during this failed lookup
	 * and the caller must immediately make the one deferred retry
	 */
	synchronized boolean markError(long nowMillis)
	{
		boolean retry = pendingRefresh || successfulSyncObservedDuringLoad;
		pendingRefresh = false;
		successfulSyncObservedDuringLoad = false;
		if (retry)
		{
			loaded = false;
			profileMayChangeAfterSuccessfulSync = false;
			lastFailureAtMillis = -1;
			// Keep inFlight held across the immediate retry hand-off.
			return true;
		}
		inFlight = false;
		lastFailureAtMillis = nowMillis;
		return false;
	}

	/**
	 * Applies a non-replayed successful sync to the current lookup state.
	 * Unchanged syncs retain a valid loaded profile, but can refresh a prior
	 * NOT_FOUND/stale-name result or retry one failed/in-flight lookup.
	 *
	 * @return true when the caller should ask the normal load path to refresh
	 */
	synchronized boolean requestRefreshAfterSuccessfulSync(boolean responseIndicatesProfileChange)
	{
		if (inFlight)
		{
			if (responseIndicatesProfileChange)
			{
				pendingRefresh = true;
			}
			else
			{
				successfulSyncObservedDuringLoad = true;
			}
			return responseIndicatesProfileChange;
		}

		if (!responseIndicatesProfileChange
			&& !profileMayChangeAfterSuccessfulSync
			&& lastFailureAtMillis < 0)
		{
			return false;
		}

		loaded = false;
		profileMayChangeAfterSuccessfulSync = false;
		lastFailureAtMillis = -1;
		return true;
	}

	/** Compatibility helper for the coordinator's changed-data unit tests. */
	synchronized void markStaleAfterChangedSync()
	{
		requestRefreshAfterSuccessfulSync(true);
	}

	/** Logout / login-screen state: cancel any pending retry and clear the session entirely. */
	synchronized void reset()
	{
		accountHash = null;
		normalizedDisplayName = null;
		inFlight = false;
		loaded = false;
		pendingRefresh = false;
		profileMayChangeAfterSuccessfulSync = false;
		successfulSyncObservedDuringLoad = false;
		lastFailureAtMillis = -1;
	}

	private void startNewSession(String currentAccountHash, String normalizedDisplayName)
	{
		this.accountHash = currentAccountHash;
		this.normalizedDisplayName = normalizedDisplayName;
		this.inFlight = false;
		this.loaded = false;
		this.pendingRefresh = false;
		this.profileMayChangeAfterSuccessfulSync = false;
		this.successfulSyncObservedDuringLoad = false;
		this.lastFailureAtMillis = -1;
	}

	private boolean isSameSession(String currentAccountHash, String normalizedDisplayName)
	{
		return Objects.equals(currentAccountHash, accountHash)
			&& Objects.equals(normalizedDisplayName, this.normalizedDisplayName);
	}

	private static String normalize(String displayName)
	{
		return displayName == null ? "" : displayName.trim().toLowerCase(Locale.ROOT);
	}
}
