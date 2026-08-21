package com.pbtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DelvePbParsingTest
{
	@Test
	public void parsesMinutesSecondsAndFraction()
	{
		assertEquals(160.80, PbTrackerPlugin.parseDelveTime("2:40.80"), 0.001);
		assertEquals(57.60, PbTrackerPlugin.parseDelveTime("0:57.60"), 0.001);
		assertEquals(110.0, PbTrackerPlugin.parseDelveTime("1:50"), 0.001);
		assertEquals(52.6, PbTrackerPlugin.parseDelveTime("0:52.6"), 0.001);
	}

	@Test
	public void rejectsGarbage()
	{
		assertNull(PbTrackerPlugin.parseDelveTime("not a time"));
		assertNull(PbTrackerPlugin.parseDelveTime(""));
		assertNull(PbTrackerPlugin.parseDelveTime("40.80"));
	}

	@Test
	public void keysEachLevelUnderDoomPrefix()
	{
		assertEquals("Doom of Mokhaiotl - Delve 1", PbTrackerPlugin.delveBossKey("1"));
		assertEquals("Doom of Mokhaiotl - Delve 7", PbTrackerPlugin.delveBossKey("7"));
		assertEquals("Doom of Mokhaiotl - Delve 8+", PbTrackerPlugin.delveBossKey("8+"));
	}
}
