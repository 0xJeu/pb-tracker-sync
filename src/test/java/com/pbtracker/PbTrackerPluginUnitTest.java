package com.pbtracker;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
	public void buildsProfileUrlWithUrlEncodedSpaces()
	{
		assertEquals(
			"https://osrs-pb-tracker-frontend.vercel.app/player/Blitzen+Jones",
			PbTrackerPlugin.buildProfileUrl("Blitzen Jones"));
	}
}
