package com.pbtracker;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Ports the core cases of frontend/test/bossGroups.test.ts - not exhaustive,
 * but covers the same behaviors that test suite locks in.
 */
public class BossGroupsTest
{
	@Test
	public void bucketsRaidKeysRegardlessOfVariantSuffix()
	{
		assertEquals(BossGroups.Category.RAIDS, BossGroups.categorize("chambers of xeric"));
		assertEquals(BossGroups.Category.RAIDS, BossGroups.categorize("theatre of blood - hard - fastest overall (5 player hard mode)"));
		assertEquals(BossGroups.Category.RAIDS, BossGroups.categorize("tombs of amascut expert mode 4 players"));
	}

	@Test
	public void bucketsCuratedSlayerMonsters()
	{
		assertEquals(BossGroups.Category.SLAYER_MONSTERS, BossGroups.categorize("cerberus"));
		assertEquals(BossGroups.Category.SLAYER_MONSTERS, BossGroups.categorize("araxxor"));
	}

	@Test
	public void bucketsCuratedMinigames()
	{
		assertEquals(BossGroups.Category.MINIGAMES, BossGroups.categorize("tzhaar fight cave"));
		assertEquals(BossGroups.Category.MINIGAMES, BossGroups.categorize("the corrupted gauntlet"));
	}

	@Test
	public void bucketsKnownStandaloneBosses()
	{
		assertEquals(BossGroups.Category.BOSSES, BossGroups.categorize("zulrah"));
		assertEquals(BossGroups.Category.BOSSES, BossGroups.categorize("vorkath"));
	}

	@Test
	public void fallsBackToOtherForUnrecognizedKeys()
	{
		assertEquals(BossGroups.Category.OTHER, BossGroups.categorize("some brand new boss"));
	}

	@Test
	public void bucketsRunOnPhraseNightmareUnderBosses()
	{
		assertEquals(BossGroups.Category.BOSSES, BossGroups.categorize("nightmare 6+ players"));
	}

	@Test
	public void isGroupedVariantTrueForRaidsAndNightmareOnly()
	{
		assertTrue(BossGroups.isGroupedVariant("chambers of xeric"));
		assertTrue(BossGroups.isGroupedVariant("the nightmare - fastest overall (solo)"));
		assertFalse(BossGroups.isGroupedVariant("zulrah"));
	}

	@Test
	public void getRaidModesOrdersEntryNormalHardForTheatreOfBlood()
	{
		List<String> bosses = List.of(
			"theatre of blood - entry - fastest overall (1 player entry mode)",
			"theatre of blood - fastest overall (3 player)",
			"theatre of blood - hard - fastest overall (4 player hard mode)"
		);
		List<BossGroups.RaidMode> modes = BossGroups.getRaidModes(bosses, "theatre of blood");
		List<String> modeLabels = modes.stream().map(m -> m.modeLabel).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("Entry", "Normal", "Hard"), modeLabels);
	}

	@Test
	public void getRaidModesOrdersNormalChallengeModeForChambersOfXeric()
	{
		List<String> bosses = List.of(
			"chambers of xeric - fastest overall (solo)",
			"chambers of xeric - challenge mode - fastest overall (solo)"
		);
		List<BossGroups.RaidMode> modes = BossGroups.getRaidModes(bosses, "chambers of xeric");
		List<String> modeLabels = modes.stream().map(m -> m.modeLabel).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("Normal", "Challenge Mode"), modeLabels);
	}

	@Test
	public void getRaidModesFindsNormalForTheNightmareUnderBosses()
	{
		List<String> bosses = List.of("the nightmare - fastest overall (solo)");
		List<BossGroups.RaidMode> modes = BossGroups.getRaidModes(bosses, "the nightmare");
		assertEquals(1, modes.size());
		assertEquals("Normal", modes.get(0).modeLabel);
	}

	@Test
	public void getRaidBasesIncludesNonRaidGroupedBossesLikeTheNightmare()
	{
		List<String> bosses = List.of(
			"chambers of xeric - fastest overall (solo)",
			"the nightmare - fastest overall (solo)"
		);
		List<BossGroups.RaidBase> bases = BossGroups.getRaidBases(bosses);
		List<String> labels = bases.stream().map(b -> b.label).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("Chambers Of Xeric", "The Nightmare"), labels);
	}

	private static BossGroups.PlayerPb pb(String boss, double timeSeconds, int rank)
	{
		return new BossGroups.PlayerPb(boss, timeSeconds, rank, "2026-07-08T00:00:00.000Z");
	}

	@Test
	public void summarizesAModeWithOnlyOverallVariantsByItsFastestOne()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("chambers of xeric - challenge mode - fastest overall (solo)", 2000, 3),
			pb("chambers of xeric - challenge mode - fastest overall (3 players)", 1000, 1)
		);
		BossGroups.GroupedPlayerPbs result = BossGroups.groupPlayerRaidPbs(pbs);
		BossGroups.PlayerRaidGroup group = findGroup(result, "Chambers Of Xeric - Challenge Mode");
		assertEquals("chambers of xeric - challenge mode - fastest overall (3 players)", group.summary.key);
		assertEquals("Trio", group.summary.label);
		assertEquals(BossGroups.VariantKind.OVERALL, group.summary.kind);
		assertEquals(1000, group.summary.timeSeconds, 0.001);
	}

	@Test
	public void picksBestRankedVariantIndependentlyOfFastestSummary()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("chambers of xeric - challenge mode - fastest overall (solo)", 2000, 2),
			pb("chambers of xeric - challenge mode - fastest overall (3 players)", 1000, 100)
		);
		BossGroups.GroupedPlayerPbs result = BossGroups.groupPlayerRaidPbs(pbs);
		BossGroups.PlayerRaidGroup group = findGroup(result, "Chambers Of Xeric - Challenge Mode");

		assertEquals("chambers of xeric - challenge mode - fastest overall (3 players)", group.summary.key);
		assertEquals("chambers of xeric - challenge mode - fastest overall (solo)",
			BossGroups.pickBestRanked(group.variants).key);
	}

	@Test
	public void ignoresFasterRoomTimesWhenOverallVariantPresent()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("theatre of blood - fastest room (3 player)", 500, 1),
			pb("theatre of blood - fastest overall (3 player)", 1200, 2)
		);
		BossGroups.GroupedPlayerPbs result = BossGroups.groupPlayerRaidPbs(pbs);
		BossGroups.PlayerRaidGroup group = findGroup(result, "Theatre Of Blood");
		assertEquals("theatre of blood - fastest overall (3 player)", group.summary.key);
		assertEquals(BossGroups.VariantKind.OVERALL, group.summary.kind);
	}

	@Test
	public void fallsBackToFastestAvailableKindWhenNoOverallVariant()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("tombs of amascut - fastest room (2 player)", 900, 4),
			pb("tombs of amascut - fastest room (solo)", 1100, 2)
		);
		BossGroups.GroupedPlayerPbs result = BossGroups.groupPlayerRaidPbs(pbs);
		BossGroups.PlayerRaidGroup group = findGroup(result, "Tombs Of Amascut");
		assertEquals(BossGroups.VariantKind.ROOM, group.summary.kind);
		assertEquals("tombs of amascut - fastest room (2 player)", group.summary.key);
	}

	@Test
	public void passesNonGroupedBossesThroughUntouchedAsFlatEntries()
	{
		List<BossGroups.PlayerPb> pbs = List.of(pb("zulrah", 80, 1), pb("vorkath", 143, 5));
		BossGroups.GroupedPlayerPbs result = BossGroups.groupPlayerRaidPbs(pbs);
		assertTrue(result.groups.isEmpty());
		assertEquals(2, result.flat.size());
	}

	@Test
	public void disambiguatesRoomVsOverallLabelsForSameTeamSize()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("tombs of amascut - expert - fastest overall (4 player)", 2002, 8),
			pb("tombs of amascut - expert - fastest room (4 player)", 1745, 8)
		);
		BossGroups.GroupedPlayerPbs result = BossGroups.groupPlayerRaidPbs(pbs);
		BossGroups.PlayerRaidGroup group = findGroup(result, "Tombs Of Amascut - Expert");
		List<String> labels = group.variants.stream().map(v -> v.label).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("4-Man - Overall", "4-Man - Room"), labels);
		assertEquals("4-Man", group.summary.label);
	}

	@Test
	public void doesNotAddKindSuffixWhenModeHasOnlyOneKind()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("chambers of xeric - challenge mode - fastest overall (solo)", 2000, 3),
			pb("chambers of xeric - challenge mode - fastest overall (3 players)", 1000, 1)
		);
		BossGroups.GroupedPlayerPbs result = BossGroups.groupPlayerRaidPbs(pbs);
		BossGroups.PlayerRaidGroup group = findGroup(result, "Chambers Of Xeric - Challenge Mode");
		List<String> labels = group.variants.stream().map(v -> v.label).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("Solo", "Trio"), labels);
	}

	@Test
	public void doesNotSuffixLabelsThatDoNotActuallyCollide()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("theatre of blood", 1200, 1),
			pb("theatre of blood - fastest overall (3 player)", 1100, 2),
			pb("theatre of blood - fastest room (3 player)", 500, 1),
			pb("theatre of blood - fastest room (former)", 900, 3)
		);
		BossGroups.GroupedPlayerPbs result = BossGroups.groupPlayerRaidPbs(pbs);
		BossGroups.PlayerRaidGroup group = findGroup(result, "Theatre Of Blood");
		List<String> labels = group.variants.stream().map(v -> v.label).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("Trio - Overall", "Trio - Room", "Overall", "Fastest Room (Former)"), labels);
	}

	@Test
	public void isGroupedVariantTrueForTzhaarChallenges()
	{
		assertTrue(BossGroups.isGroupedVariant("tzhaar-ket-rak's first challenge"));
		assertTrue(BossGroups.isGroupedVariant("tzhaar-ket-rak's sixth challenge"));
	}

	@Test
	public void collapsesTzhaarChallengesIntoOneRaidBase()
	{
		List<String> bosses = List.of(
			"tzhaar-ket-rak's first challenge", "tzhaar-ket-rak's second challenge",
			"tzhaar-ket-rak's third challenge", "tzhaar-ket-rak's fourth challenge",
			"tzhaar-ket-rak's fifth challenge", "tzhaar-ket-rak's sixth challenge",
			"zulrah"
		);
		List<BossGroups.RaidBase> bases = BossGroups.getRaidBases(bosses);
		List<String> labels = bases.stream().map(b -> b.label).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("Tzhaar-ket-rak's Challenges"), labels);

		List<BossGroups.RaidMode> modes = BossGroups.getRaidModes(bosses, bases.get(0).base);
		assertEquals(1, modes.size());
		List<String> variantLabels = modes.get(0).variants.stream().map(v -> v.label).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("First Challenge", "Second Challenge", "Third Challenge",
			"Fourth Challenge", "Fifth Challenge", "Sixth Challenge"), variantLabels);
	}

	@Test
	public void groupsPlayerTzhaarChallengePbsUnderOneHeading()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("tzhaar-ket-rak's first challenge", 120, 5),
			pb("tzhaar-ket-rak's second challenge", 90, 2)
		);
		BossGroups.GroupedPlayerPbs result = BossGroups.groupPlayerRaidPbs(pbs);
		BossGroups.PlayerRaidGroup group = findGroup(result, "Tzhaar-ket-rak's Challenges");
		assertEquals(2, group.variants.size());
	}

	private static final List<String> DOOM_TIERS_SHUFFLED = List.of(
		"Doom of Mokhaiotl - Delve 8+", "Doom of Mokhaiotl - Delve 3", "Doom of Mokhaiotl - Delve 8",
		"Doom of Mokhaiotl - Delve 1", "Doom of Mokhaiotl - Delve 5", "Doom of Mokhaiotl - Delve 2",
		"Doom of Mokhaiotl - Delve 7", "Doom of Mokhaiotl - Delve 4", "Doom of Mokhaiotl - Delve 6"
	);

	@Test
	public void groupsOnlyTrackedDoomDelveTiersUnderBosses()
	{
		assertTrue(BossGroups.isGroupedVariant("Doom of Mokhaiotl - Delve 1"));
		assertTrue(BossGroups.isGroupedVariant("doom of mokhaiotl - delve 8+"));
		assertFalse(BossGroups.isGroupedVariant("doom of mokhaiotl"));
		assertFalse(BossGroups.isGroupedVariant("doom of mokhaiotl - delve 9"));
		assertEquals(BossGroups.Category.BOSSES, BossGroups.categorize("Doom of Mokhaiotl - Delve 8+"));
	}

	@Test
	public void collapsesDoomDelvesIntoOnePickerOrderedOneToEightThenEightPlus()
	{
		List<String> bosses = new java.util.ArrayList<>(DOOM_TIERS_SHUFFLED);
		bosses.add("zulrah");

		List<BossGroups.RaidBase> bases = BossGroups.getRaidBases(bosses);
		assertEquals(1, bases.size());
		assertEquals("Doom Of Mokhaiotl", bases.get(0).label);
		assertEquals(List.of("zulrah"), BossGroups.getFlatBossKeys(bosses));

		List<BossGroups.RaidMode> modes = BossGroups.getRaidModes(bosses, bases.get(0).base);
		assertEquals(1, modes.size());
		List<String> labels = modes.get(0).variants.stream().map(v -> v.label).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("Delve 1", "Delve 2", "Delve 3", "Delve 4", "Delve 5", "Delve 6",
			"Delve 7", "Delve 8", "Delve 8+"), labels);
		assertEquals("Doom of Mokhaiotl - Delve 8+", modes.get(0).variants.get(8).key);
	}

	@Test
	public void summarizesDoomByDeepestDelveNotFastestTime()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("Doom of Mokhaiotl - Delve 1", 49, 1),
			pb("Doom of Mokhaiotl - Delve 4", 113, 1),
			pb("Doom of Mokhaiotl - Delve 2", 55, 1),
			pb("Doom of Mokhaiotl - Delve 3", 96, 1)
		);
		BossGroups.PlayerRaidGroup group = findGroup(BossGroups.groupPlayerRaidPbs(pbs), "Doom Of Mokhaiotl");
		assertEquals(BossGroups.SummaryRule.DEEPEST, group.summaryRule);
		assertEquals("Doom of Mokhaiotl - Delve 4", group.summary.key);
		assertEquals("Delve 4", group.summary.label);
		assertEquals(113, group.summary.timeSeconds, 0.001);
		List<String> labels = group.variants.stream().map(v -> v.label).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("Delve 1", "Delve 2", "Delve 3", "Delve 4"), labels);
	}

	@Test
	public void treatsDelveEightPlusAsDeeperThanEightEvenWithGaps()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("Doom of Mokhaiotl - Delve 8+", 400, 3),
			pb("Doom of Mokhaiotl - Delve 8", 300, 2),
			pb("Doom of Mokhaiotl - Delve 5", 150, 9)
		);
		BossGroups.PlayerRaidGroup group = findGroup(BossGroups.groupPlayerRaidPbs(pbs), "Doom Of Mokhaiotl");
		assertEquals("Delve 8+", group.summary.label);
		List<String> labels = group.variants.stream().map(v -> v.label).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("Delve 5", "Delve 8", "Delve 8+"), labels);
	}

	@Test
	public void fasterTimeWinsWhenDuplicateKeysMapToTheDeepestTier()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("Doom of Mokhaiotl - Delve 3", 96, 1),
			pb("Doom of Mokhaiotl - Delve 4", 130, 2),
			pb("doom of mokhaiotl - delve 4", 113, 1)
		);
		BossGroups.PlayerRaidGroup group = findGroup(BossGroups.groupPlayerRaidPbs(pbs), "Doom Of Mokhaiotl");
		assertEquals("doom of mokhaiotl - delve 4", group.summary.key);
		assertEquals(113, group.summary.timeSeconds, 0.001);
		assertEquals(1, group.summary.rank);
		assertEquals("Delve 4", group.summary.label);
	}

	@Test
	public void usesDeepestRuleOnlyForDoom()
	{
		assertEquals(BossGroups.SummaryRule.DEEPEST, BossGroups.summaryRuleForBase("doom of mokhaiotl"));
		assertEquals(BossGroups.SummaryRule.FASTEST, BossGroups.summaryRuleForBase("chambers of xeric"));
		assertEquals(BossGroups.SummaryRule.FASTEST, BossGroups.summaryRuleForBase("the nightmare"));
		assertEquals(BossGroups.SummaryRule.FASTEST, BossGroups.summaryRuleForBase("tzhaar-ket-rak's challenges"));
	}

	@Test
	public void summarizesAPlayerWithOnlyOneDoomTier()
	{
		BossGroups.PlayerRaidGroup group = findGroup(
			BossGroups.groupPlayerRaidPbs(List.of(pb("Doom of Mokhaiotl - Delve 2", 55, 4))), "Doom Of Mokhaiotl");
		assertEquals("Delve 2", group.summary.label);
		assertEquals(1, group.variants.size());
	}

	@Test
	public void raidsKeepFastestTimeSummaryWithoutTierLabel()
	{
		List<BossGroups.PlayerPb> pbs = List.of(
			pb("chambers of xeric - fastest overall (solo)", 2000, 3),
			pb("chambers of xeric - fastest overall (3 players)", 1000, 1)
		);
		BossGroups.PlayerRaidGroup group = findGroup(BossGroups.groupPlayerRaidPbs(pbs), "Chambers Of Xeric");
		assertEquals(BossGroups.SummaryRule.FASTEST, group.summaryRule);
		assertEquals("chambers of xeric - fastest overall (3 players)", group.summary.key);
	}

	@Test
	public void sortsOpenEndedTeamSizeRightAfterItsBase()
	{
		List<String> bosses = List.of(
			"the nightmare - fastest overall (6+ players)",
			"the nightmare - fastest overall (6 players)",
			"the nightmare - fastest overall (5 players)"
		);
		List<BossGroups.RaidMode> modes = BossGroups.getRaidModes(bosses, "the nightmare");
		List<String> labels = modes.get(0).variants.stream().map(v -> v.label).collect(java.util.stream.Collectors.toList());
		assertEquals(List.of("5-Man", "6-Man", "6+"), labels);
	}

	private static BossGroups.PlayerRaidGroup findGroup(BossGroups.GroupedPlayerPbs result, String heading)
	{
		return result.groups.stream().filter(g -> g.heading.equals(heading)).findFirst()
			.orElseThrow(() -> new AssertionError("No group found for heading: " + heading));
	}
}
