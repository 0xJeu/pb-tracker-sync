package com.pbtracker;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrackedBossesTest
{
	@Test
	public void acceptsTrackedBossesAndTheirVariants()
	{
		assertTrue(TrackedBosses.isTracked("zulrah"));
		assertTrue(TrackedBosses.isTracked("The Gauntlet"));
		assertTrue(TrackedBosses.isTracked("Theatre of Blood - Hard - Fastest Overall (5 Player Hard Mode)"));
		assertTrue(TrackedBosses.isTracked("Duke Sucellus (awakened)"));
	}

	@Test
	public void acceptsOnlySupportedDoomTimedDelves()
	{
		for (int delve = 1; delve <= 8; delve++)
		{
			assertTrue(TrackedBosses.isTracked("Doom of Mokhaiotl - Delve " + delve));
		}
		assertTrue(TrackedBosses.isTracked("Doom of Mokhaiotl - Delve 8+"));

		assertFalse(TrackedBosses.isTracked("Doom of Mokhaiotl"));
		assertFalse(TrackedBosses.isTracked("Doom of Mokhaiotl - Deepest Delve"));
		assertFalse(TrackedBosses.isTracked("Doom of Mokhaiotl - Delve 9"));
		assertFalse(TrackedBosses.isTracked("Doom of Mokhaiotl - Delve 8+ - Fastest Overall"));
	}

	@Test
	public void rejectsHistoricalUntrackedRows()
	{
		assertFalse(TrackedBosses.isTracked("cerberus"));
		assertFalse(TrackedBosses.isTracked("demonic brutus"));
		assertFalse(TrackedBosses.isTracked("fragment of seren"));
		assertFalse(TrackedBosses.isTracked(null));
	}
}
