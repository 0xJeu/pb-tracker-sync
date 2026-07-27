package com.pbtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
