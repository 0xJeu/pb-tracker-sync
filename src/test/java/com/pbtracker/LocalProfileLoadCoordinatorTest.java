package com.pbtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalProfileLoadCoordinatorTest
{
	@Test
	public void firstLoginStateForAnAccountSchedulesOneLoad()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onLoginState("acct-a", "Zezima", 1_000L));
	}

	@Test
	public void repeatedLoginStatesForTheSameAccountAndNameDoNotScheduleAnotherLookup()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markLoaded();

		assertEquals(
			LocalProfileLoadCoordinator.Decision.SKIP_ALREADY_LOADED,
			coordinator.onLoginState("acct-a", "Zezima", 2_000L));
	}

	@Test
	public void worldHopSchedulesNoAdditionalLookupOnceLoaded()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markLoaded();

		// A world hop just republishes LOGGED_IN for the same account/name.
		assertEquals(
			LocalProfileLoadCoordinator.Decision.SKIP_ALREADY_LOADED,
			coordinator.onLoginState("acct-a", "Zezima", 5_000L));
	}

	@Test
	public void accountSwitchSchedulesOneLookupForTheNewAccount()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markLoaded();

		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onLoginState("acct-b", "AltAccount", 2_000L));
	}

	@Test
	public void normalizedSameNameCoalescesAnInFlightLookup()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L); // now in flight

		assertEquals(
			LocalProfileLoadCoordinator.Decision.SKIP_IN_FLIGHT,
			coordinator.onLoginState("acct-a", "  ZEZIMA  ", 1_001L));
	}

	@Test
	public void nameChangePermitsOneNewLookup()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markLoaded();

		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onLoginState("acct-a", "ZezimaTwo", 2_000L));
	}

	@Test
	public void errorBackoffPreventsRapidRetries()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markError(1_000L);

		assertEquals(
			LocalProfileLoadCoordinator.Decision.SKIP_BACKOFF,
			coordinator.onLoginState("acct-a", "Zezima", 1_500L));
		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onLoginState("acct-a", "Zezima", 31_001L));
	}

	@Test
	public void manualRefreshBypassesLoadedStateButCoalescesConcurrentRefreshes()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markLoaded();

		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onManualRefresh("acct-a", "Zezima"));
		assertEquals(
			LocalProfileLoadCoordinator.Decision.SKIP_IN_FLIGHT,
			coordinator.onManualRefresh("acct-a", "Zezima"));
	}

	@Test
	public void manualRefreshForADifferentSessionDoesNotLeakStaleLoadedOrBackoffState()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markLoaded();
		coordinator.markError(1_000L); // simulate a stale backoff timestamp left on the old session

		// Manual refresh for a different account should not be blocked by the
		// old session's loaded/backoff state, and should not carry that state
		// forward into the new session either.
		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onManualRefresh("acct-b", "AltAccount"));

		// The new session should behave like a freshly tracked one: a
		// concurrent manual refresh for it coalesces...
		assertEquals(
			LocalProfileLoadCoordinator.Decision.SKIP_IN_FLIGHT,
			coordinator.onManualRefresh("acct-b", "AltAccount"));

		// ...and once it completes, no stale loaded=true or backoff timestamp
		// from acct-a should cause onLoginState to skip a subsequent load.
		coordinator.markError(1_500L);
		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onLoginState("acct-b", "AltAccount", 100_000L));
	}

	@Test
	public void logoutClearsTheSessionAndPermitsANewLoadOnNextLogin()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markLoaded();

		coordinator.reset();

		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onLoginState("acct-a", "Zezima", 2_000L));
	}

	@Test
	public void successfulSyncWithChangedDataAllowsOneRefresh()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markLoaded();

		coordinator.markStaleAfterChangedSync();

		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onLoginState("acct-a", "Zezima", 2_000L));
	}

	@Test
	public void unchangedOrReplayedSyncDoesNotForceARefresh()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markLoaded();

		// No markStaleAfterChangedSync() call - an unchanged/deduplicated sync
		// must leave the already-loaded profile alone.
		assertEquals(
			LocalProfileLoadCoordinator.Decision.SKIP_ALREADY_LOADED,
			coordinator.onLoginState("acct-a", "Zezima", 2_000L));
	}

	@Test
	public void changedSyncArrivingMidLoadIsNotSwallowedByThatLoadCompleting()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		// An unrelated lookup is already in flight (e.g. a world hop retry)...
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);

		// ...and a changed-data sync's onResponse fires while it's still in flight.
		coordinator.markStaleAfterChangedSync();

		// The unrelated in-flight lookup now completes. Without pendingRefresh,
		// this would set loaded=true and permanently mask the missed refresh.
		coordinator.markLoaded();

		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onLoginState("acct-a", "Zezima", 2_000L));
	}

	@Test
	public void markLoadedReturnsFalseInTheNormalNoPendingRefreshCase()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);

		assertFalse(coordinator.markLoaded());
	}

	@Test
	public void markLoadedReturnsTrueWhenARefreshWasPendingAndConsumesIt()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		// An unrelated lookup is already in flight (e.g. a world hop retry)...
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		// ...and a changed-data sync's onResponse fires while it's still in flight.
		coordinator.markStaleAfterChangedSync();

		// The in-flight lookup completes: a refresh is still owed, so this must
		// report true so the caller knows to immediately start another load
		// rather than treating the session as up to date.
		assertTrue(coordinator.markLoaded());

		// That pendingRefresh was consumed, so the caller's own re-triggered
		// load's eventual markLoaded() call sees nothing pending and reports false.
		assertFalse(coordinator.markLoaded());
	}

	@Test
	public void markLoadedClearsStaleFailureBackoffTimestamp()
	{
		LocalProfileLoadCoordinator coordinator = new LocalProfileLoadCoordinator();
		coordinator.onLoginState("acct-a", "Zezima", 1_000L);
		coordinator.markError(1_000L); // sets lastFailureAtMillis = 1_000L

		// A later load for the same session (past the 30s backoff window)
		// succeeds...
		coordinator.onLoginState("acct-a", "Zezima", 31_001L);
		coordinator.markLoaded();

		// Force loaded back to false (as markStaleAfterChangedSync() would)
		// so onLoginState re-evaluates the backoff check instead of short
		// circuiting on SKIP_ALREADY_LOADED - this isolates whether the old
		// (now stale) failure timestamp from 1_000L was actually cleared by
		// the successful markLoaded() above.
		coordinator.markStaleAfterChangedSync();

		assertEquals(
			LocalProfileLoadCoordinator.Decision.LOAD,
			coordinator.onLoginState("acct-a", "Zezima", 31_100L));
	}
}
