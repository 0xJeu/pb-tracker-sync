package com.pbtracker;

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
}
