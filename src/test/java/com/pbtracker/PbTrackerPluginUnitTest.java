package com.pbtracker;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PbTrackerPluginUnitTest
{
	@Test
	public void pluginClassLoads()
	{
		assertNotNull(PbTrackerPlugin.class);
	}

	@Test
	public void syncNowTriggersForEitherToggleDirection()
	{
		assertTrue(PbTrackerPlugin.shouldTriggerSyncNow("true"));
		assertTrue(PbTrackerPlugin.shouldTriggerSyncNow("false"));
	}

	@Test
	public void canonicalizesAwakenedDt2RawKeys()
	{
		assertEquals("Duke Sucellus (awakened)", PbTrackerPlugin.canonicalBossKey("duke sucellus awakened"));
		assertEquals("Leviathan (awakened)", PbTrackerPlugin.canonicalBossKey("the leviathan awakened"));
		assertEquals("Whisperer (awakened)", PbTrackerPlugin.canonicalBossKey("wisp awakened"));
		assertEquals("Vardorvis (awakened)", PbTrackerPlugin.canonicalBossKey("vard awakened"));
	}

	@Test
	public void pbrUsesTheVisibleChatSenderForSharedCommands()
	{
		assertEquals("Autisrick", PbTrackerPlugin.resolvePbrPlayerName("<img=13>Autisrick", false, "0xSteph"));
	}

	@Test
	public void pbrUsesLocalPlayerForOutgoingPrivateMessages()
	{
		assertEquals("0xSteph", PbTrackerPlugin.resolvePbrPlayerName("Recipient", true, "0xSteph"));
	}

	@Test
	public void dt2ScoreboardsDistinguishNormalAndAwakenedRecords()
	{
		assertEquals("Leviathan", PbTrackerPlugin.dt2ScoreboardBossKey("Leviathan", "Leviathan Statistics"));
		assertEquals("Duke Sucellus (awakened)", PbTrackerPlugin.dt2ScoreboardBossKey(
			"Duke Sucellus", "Duke Sucellus (Awakened) Statistics"));
		assertNull(PbTrackerPlugin.dt2ScoreboardBossKey("Vardorvis", "Duke Sucellus Statistics"));
	}

	@Test
	public void keepsNonRaidKeysThatContainDigits()
	{
		assertTrue(PbTrackerPlugin.shouldSyncRawPersonalBest("hallowed sepulchre floor 5"));
		assertTrue(PbTrackerPlugin.shouldSyncRawPersonalBest("TzHaar-Ket-Rak's First Challenge"));
		assertTrue(PbTrackerPlugin.shouldSyncRawPersonalBest("duke sucellus awakened"));
	}

	@Test
	public void skipsRaidTeamSizeVariants()
	{
		assertFalse(PbTrackerPlugin.shouldSyncRawPersonalBest("chambers of xeric 3 players"));
		assertFalse(PbTrackerPlugin.shouldSyncRawPersonalBest("chambers of xeric challenge mode solo"));
		assertFalse(PbTrackerPlugin.shouldSyncRawPersonalBest("theatre of blood entry mode solo"));
		assertFalse(PbTrackerPlugin.shouldSyncRawPersonalBest("tombs of amascut expert mode 4 players"));
	}

	@Test
	public void skipsNightmareTeamSizeVariants()
	{
		// Bare "nightmare" is gated separately via KNOWN_DUPLICATE_RAW_KEYS
		// (different internal name than its Adventure Log heading); its
		// team-size suffixed forms need their own pattern, same shape as
		// the CoX/ToB/ToA team-size variants above.
		assertFalse(PbTrackerPlugin.shouldSyncRawPersonalBest("nightmare 6+ players"));
		assertFalse(PbTrackerPlugin.shouldSyncRawPersonalBest("nightmare 2 players"));
		assertFalse(PbTrackerPlugin.shouldSyncRawPersonalBest("nightmare solo"));
	}

	@Test
	public void rawPbReportAnnotatesOrdinaryBossAsSynced()
	{
		Map<String, Double> raw = new LinkedHashMap<>();
		raw.put("cerberus", 61.0);

		String report = PbTrackerPlugin.buildRawPbReport(raw);

		assertEquals("personalbest.cerberus = 61.0 -> synced as \"cerberus\"\n", report);
	}

	@Test
	public void rawPbReportAnnotatesAwakenedAliasAsSynced()
	{
		Map<String, Double> raw = new LinkedHashMap<>();
		raw.put("duke sucellus awakened", 353.2);

		String report = PbTrackerPlugin.buildRawPbReport(raw);

		assertEquals("personalbest.duke sucellus awakened = 353.2 -> synced as \"Duke Sucellus (awakened)\"\n", report);
	}

	@Test
	public void rawPbReportAnnotatesKnownDuplicateAsGated()
	{
		Map<String, Double> raw = new LinkedHashMap<>();
		raw.put("tztok-jad", 132.4);

		String report = PbTrackerPlugin.buildRawPbReport(raw);

		assertEquals(
			"personalbest.tztok-jad = 132.4 -> SKIPPED (gated, waiting on Adventure Log Counters -> \"TzHaar Fight Cave\")\n",
			report);
	}

	@Test
	public void rawPbReportAnnotatesUnlistedRaidVariantAsSkipped()
	{
		Map<String, Double> raw = new LinkedHashMap<>();
		raw.put("chambers of xeric 2 players", 1200.0);

		String report = PbTrackerPlugin.buildRawPbReport(raw);

		assertEquals(
			"personalbest.chambers of xeric 2 players = 1200.0 -> SKIPPED (raid/team-size variant, waiting on Adventure Log Counters)\n",
			report);
	}

	@Test
	public void rawPbReportSortsEntriesByKey()
	{
		Map<String, Double> raw = new LinkedHashMap<>();
		raw.put("zulrah", 41.0);
		raw.put("cerberus", 61.0);

		String report = PbTrackerPlugin.buildRawPbReport(raw);

		assertTrue(report.indexOf("cerberus") < report.indexOf("zulrah"));
	}

	@Test
	public void buildsProfileUrlForSimpleName()
	{
		assertEquals(
			"https://osrs-pb-tracker-frontend.vercel.app/player/Zulrah",
			PbTrackerPlugin.buildProfileUrl("Zulrah"));
	}

	@Test
	public void buildsProfileUrlWithPercentEncodedSpaces()
	{
		assertEquals(
			"https://osrs-pb-tracker-frontend.vercel.app/player/Blitzen%20Jones",
			PbTrackerPlugin.buildProfileUrl("Blitzen Jones"));
	}

	@Test
	public void resolvesCommonRaidShorthandToStoredBossKeys()
	{
		assertEquals("theatre of blood", PbTrackerPlugin.resolveBossAlias("tob"));
		assertEquals("theatre of blood", PbTrackerPlugin.resolveBossAlias("ToB"));
		assertEquals("chambers of xeric", PbTrackerPlugin.resolveBossAlias("cox"));
		assertEquals("tombs of amascut", PbTrackerPlugin.resolveBossAlias("TOA"));
		assertEquals("inferno", PbTrackerPlugin.resolveBossAlias("zuk"));
		assertEquals("tzhaar fight cave", PbTrackerPlugin.resolveBossAlias("jad"));
		assertEquals("the gauntlet", PbTrackerPlugin.resolveBossAlias("gaunt"));
		assertEquals("the corrupted gauntlet", PbTrackerPlugin.resolveBossAlias("cg"));
		assertEquals("phosani's nightmare", PbTrackerPlugin.resolveBossAlias("pnm"));
	}

	@Test
	public void resolveBossAliasFallsBackToTrimmedLowercaseInput()
	{
		assertEquals("zulrah", PbTrackerPlugin.resolveBossAlias("Zulrah"));
		assertEquals("vorkath", PbTrackerPlugin.resolveBossAlias("  vorkath  "));
		assertEquals("some brand new boss", PbTrackerPlugin.resolveBossAlias("Some Brand New Boss"));
	}

	@Test
	public void formatsTimeUnderAnHourAsMinutesAndSeconds()
	{
		assertEquals("2:05", PbTrackerPlugin.formatTime(125));
		assertEquals("0:09", PbTrackerPlugin.formatTime(9));
	}

	@Test
	public void formatsTimeOverAnHourWithHours()
	{
		assertEquals("1:00:00", PbTrackerPlugin.formatTime(3600));
		assertEquals("1:40:28", PbTrackerPlugin.formatTime(6028));
	}

	@Test
	public void formatsFractionalSecondsWhenPresent()
	{
		assertEquals("1:20.40", PbTrackerPlugin.formatTime(80.4));
	}

	@Test
	public void titleCasesEachWhitespaceSeparatedWord()
	{
		assertEquals("Theatre Of Blood", PbTrackerPlugin.titleCase("theatre of blood"));
		assertEquals(
			"Theatre Of Blood - Hard - Fastest Overall (4 Player Hard Mode)",
			PbTrackerPlugin.titleCase("theatre of blood - hard - fastest overall (4 player hard mode)"));
	}

	@Test
	public void splitsTrailingNumericTokenAsTeamSize()
	{
		assertEquals("[tob, 4, null]", Arrays.toString(PbTrackerPlugin.splitBossSizeAndMode("tob 4")));
		assertEquals("[theatre of blood, 2, null]", Arrays.toString(PbTrackerPlugin.splitBossSizeAndMode("theatre of blood 2")));
		assertEquals("[tob, solo, null]", Arrays.toString(PbTrackerPlugin.splitBossSizeAndMode("tob solo")));
	}

	@Test
	public void doesNotMistakeMultiWordBossNamesForATeamSize()
	{
		assertEquals("[fight caves, null, null]", Arrays.toString(PbTrackerPlugin.splitBossSizeAndMode("fight caves")));
		assertEquals("[zulrah, null, null]", Arrays.toString(PbTrackerPlugin.splitBossSizeAndMode("zulrah")));
	}

	@Test
	public void splitsAnExplicitModeKeywordBeforeTheSize()
	{
		assertEquals("[tob, 2, entry]", Arrays.toString(PbTrackerPlugin.splitBossSizeAndMode("tob entry 2")));
		assertEquals("[tob, 4, hard]", Arrays.toString(PbTrackerPlugin.splitBossSizeAndMode("tob hard 4")));
		assertEquals("[chambers of xeric, 3, challenge mode]", Arrays.toString(PbTrackerPlugin.splitBossSizeAndMode("chambers of xeric challenge 3")));
	}

	@Test
	public void splitsAModeKeywordWithNoSize()
	{
		assertEquals("[tob, null, entry]", Arrays.toString(PbTrackerPlugin.splitBossSizeAndMode("tob entry")));
	}

	private static SyncClient.PbEntryDto pb(String boss, double timeSeconds, int rank)
	{
		SyncClient.PbEntryDto dto = new SyncClient.PbEntryDto();
		dto.boss = boss;
		dto.timeSeconds = timeSeconds;
		dto.rank = rank;
		return dto;
	}

	// Shape of Blitzen's real synced ToB/CoX/ToA data, pulled from the live
	// site - including the stray bare "theatre of blood" entry (73s) that
	// prompted this whole redesign: RuneLite itself has that cached as the
	// account's "Theatre of Blood" personal best even though it's nowhere
	// near a real clear time, and the old exact-match-only lookup surfaced
	// it as "your ToB PB" by default. findPbrMatch must not do that.
	private static final List<SyncClient.PbEntryDto> TOB_COX_TOA_FIXTURE = Arrays.asList(
		pb("theatre of blood", 73, 1),
		pb("theatre of blood - fastest overall (3 player)", 1252, 10),
		pb("theatre of blood - fastest overall (4 player)", 1103, 10),
		pb("theatre of blood - fastest overall (5 player)", 1159, 9),
		pb("theatre of blood - entry - fastest overall (1 player entry mode)", 1159, 6),
		pb("theatre of blood - entry - fastest overall (2 player entry mode)", 2353, 12),
		pb("theatre of blood - hard - fastest overall (4 player hard mode)", 1322, 11),
		pb("theatre of blood - hard - fastest overall (5 player hard mode)", 1244, 10),
		pb("theatre of blood - fastest room (4 player)", 976, 10),
		pb("chambers of xeric", 1289, 18),
		pb("chambers of xeric - fastest overall (2 players)", 1969, 11),
		pb("chambers of xeric - challenge mode - fastest overall (2 players)", 2711, 9),
		pb("tombs of amascut - fastest overall (2 player)", 2521, 13),
		pb("tombs of amascut - expert - fastest overall (2 player)", 3607, 11),
		pb("tombs of amascut - entry - fastest overall (solo)", 2377, 6)
	);

	@Test
	public void requestingATeamSizeIgnoresTheBareUnlabeledEntry()
	{
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "theatre of blood", "4", null);
		assertNotNull(match);
		assertEquals("theatre of blood - fastest overall (4 player)", match.boss);
		assertEquals(1103, match.timeSeconds, 0.001);
	}

	@Test
	public void requestingATeamSizePrefersNormalModeOverHardMode()
	{
		// Both Normal and Hard mode have a "4 player" Fastest Overall entry,
		// and Hard mode is (implausibly, but for the sake of the test) the
		// faster of the two - "!pbr tob 4" should still mean the Normal-mode
		// one specifically, not silently pick whichever is fastest overall
		// regardless of mode.
		List<SyncClient.PbEntryDto> fixture = Arrays.asList(
			pb("theatre of blood - fastest overall (4 player)", 1103, 10),
			pb("theatre of blood - hard - fastest overall (4 player hard mode)", 900, 1)
		);
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(fixture, "theatre of blood", "4", null);
		assertEquals("theatre of blood - fastest overall (4 player)", match.boss);
	}

	@Test
	public void defaultModeNeverFallsBackToADifferentModeEvenIfThatsAllThatExists()
	{
		// Only Entry mode has a "1 player" entry - Normal mode has none. With
		// no mode requested, that means "give me Normal mode specifically",
		// so this must return null rather than silently substituting Entry
		// mode's record. Per George: "I wanna pull from normal mode, and if
		// you need the entry times I want you to do pbr tob entry".
		assertNull(PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "theatre of blood", "1", null));
	}

	@Test
	public void explicitEntryModeKeywordMatchesTheEntryModeRecord()
	{
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "theatre of blood", "1", "entry");
		assertNotNull(match);
		assertEquals("theatre of blood - entry - fastest overall (1 player entry mode)", match.boss);
	}

	@Test
	public void requestingASoloTombsOfAmascutRecordWithNoModeReturnsNullWhenOnlyEntryModeHasIt()
	{
		// Fixture's only "solo" ToA entry is Entry mode's - same rule as
		// ToB above, default (no mode given) means Normal mode specifically.
		assertNull(PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "tombs of amascut", "solo", null));
	}

	@Test
	public void requestingASoloTombsOfAmascutRecordWithEntryModeMatches()
	{
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "tombs of amascut", "solo", "entry");
		assertNotNull(match);
		assertEquals("tombs of amascut - entry - fastest overall (solo)", match.boss);
	}

	@Test
	public void requestingATeamSizeWithNoMatchReturnsNull()
	{
		assertNull(PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "theatre of blood", "6", null));
	}

	@Test
	public void noArgsGivenReturnsFastestNormalModeOverallNotTheBareEntry()
	{
		// The bare "theatre of blood" entry (73s) is the fastest raw number
		// in the fixture, but it's not a real "Fastest Overall" record - the
		// real fastest labeled Normal-mode one is the 4-player run at 1103s.
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "theatre of blood", null, null);
		assertNotNull(match);
		assertEquals("theatre of blood - fastest overall (4 player)", match.boss);
		assertEquals(1103, match.timeSeconds, 0.001);
	}

	@Test
	public void noArgsGivenNeverPicksAFasterOtherModeOverNormal()
	{
		// Hard mode is (implausibly, but for the test) faster than every
		// Normal-mode entry - a bare "!pbr tob" must still mean Normal mode,
		// not just whatever's fastest across all modes.
		List<SyncClient.PbEntryDto> fixture = Arrays.asList(
			pb("theatre of blood - fastest overall (4 player)", 1103, 10),
			pb("theatre of blood - hard - fastest overall (4 player hard mode)", 500, 1)
		);
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(fixture, "theatre of blood", null, null);
		assertNotNull(match);
		assertEquals("theatre of blood - fastest overall (4 player)", match.boss);
	}

	@Test
	public void noSizeGivenFallsBackToBareKeyWhenNoLabeledEntriesExist()
	{
		List<SyncClient.PbEntryDto> onlyBareEntry = Arrays.asList(pb("vorkath", 34.6, 4));
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(onlyBareEntry, "vorkath", null, null);
		assertNotNull(match);
		assertEquals("vorkath", match.boss);
	}

	@Test
	public void chambersOfXericPluralPlayerWordingMatchesTeamSize()
	{
		// CoX stores "players" (plural), unlike ToB/ToA's "player" (singular)
		// - parenMatchesSize must handle both via a prefix check.
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "chambers of xeric", "2", null);
		assertNotNull(match);
		assertEquals("chambers of xeric - fastest overall (2 players)", match.boss);
	}

	@Test
	public void requiredModeOnlyMatchesThatModeIgnoringFasterEntriesElsewhere()
	{
		// "hmt" (Hard Mode Theatre of Blood) must never return the Normal or
		// Entry mode entries even though some are faster.
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "theatre of blood", null, "hard");
		assertNotNull(match);
		assertEquals("theatre of blood - hard - fastest overall (5 player hard mode)", match.boss);
	}

	@Test
	public void requiredModeCombinesWithAnExplicitTeamSize()
	{
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "theatre of blood", "4", "hard");
		assertNotNull(match);
		assertEquals("theatre of blood - hard - fastest overall (4 player hard mode)", match.boss);
	}

	@Test
	public void requiredModeWithNoMatchingEntriesReturnsNullNotABareKeyFallback()
	{
		assertNull(PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "theatre of blood", null, "challenge mode"));
	}

	@Test
	public void chambersOfXericChallengeModeRequiredModeMatches()
	{
		SyncClient.PbEntryDto match = PbTrackerPlugin.findPbrMatch(TOB_COX_TOA_FIXTURE, "chambers of xeric", "2", "challenge mode");
		assertNotNull(match);
		assertEquals("chambers of xeric - challenge mode - fastest overall (2 players)", match.boss);
	}

	@Test
	public void extractsOwnerNameFromTheLocalPlayersOwnCountersPage()
	{
		// Real captured value from a live gradle run session, own account's
		// Counters page.
		assertEquals("Blitzen", PbTrackerPlugin.extractAdventureLogOwnerName("Blitzen"));
	}

	@Test
	public void extractsOwnerNameFromAFriendsPohCountersPage()
	{
		// Real captured value visiting a friend's POH and reading their
		// Adventure Log - confirms the widget shows the HOUSE OWNER's name,
		// not the visiting player's.
		assertEquals("Dad", PbTrackerPlugin.extractAdventureLogOwnerName("Dad"));
	}

	@Test
	public void extractsOwnerNameFromTheMainMenuPageTitleForm()
	{
		// Not confirmed live (only the Counters sub-page was tested), kept
		// as defensive handling in case this code path ever sees the
		// Adventure Log's main menu page title instead.
		assertEquals("Blitzen", PbTrackerPlugin.extractAdventureLogOwnerName("The Exploits of Blitzen"));
	}

	@Test
	public void ownerNameExtractionReturnsNullForBlankOrMissingTitle()
	{
		assertNull(PbTrackerPlugin.extractAdventureLogOwnerName(null));
		assertNull(PbTrackerPlugin.extractAdventureLogOwnerName(""));
		assertNull(PbTrackerPlugin.extractAdventureLogOwnerName("   "));
	}

	@Test
	public void ownerNameExtractionReturnsNullForGenericAdventureLogPageLabels()
	{
		// If the TITLE widget ever reads one of the Adventure Log's own
		// generic page labels instead of an account name, that must not be
		// treated as evidence of a different owner - this is exactly what
		// was flagged in PR review as the risk of matching any nonempty
		// string as a name.
		assertNull(PbTrackerPlugin.extractAdventureLogOwnerName("Counters"));
		assertNull(PbTrackerPlugin.extractAdventureLogOwnerName("counters"));
		assertNull(PbTrackerPlugin.extractAdventureLogOwnerName("Adventure Log"));
		assertNull(PbTrackerPlugin.extractAdventureLogOwnerName("Collection log"));
	}
}
