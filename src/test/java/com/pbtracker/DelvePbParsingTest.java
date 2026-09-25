package com.pbtracker;

import org.junit.Test;

import java.util.Map;

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

	@Test
	public void readsNewPersonalBestFromChat()
	{
		assertEquals(Map.of("Doom of Mokhaiotl - Delve 6", 160.8),
			PbTrackerPlugin.parseDelveChatPb("Delve level: 6 duration: 2:40.80 (new personal best)"));
	}

	@Test
	public void readsExistingPersonalBestWhenRunIsNotAPb()
	{
		assertEquals(Map.of("Doom of Mokhaiotl - Delve 4", 95.4),
			PbTrackerPlugin.parseDelveChatPb("Delve level: 4 duration: 1:50.40. Personal best: 1:35.40"));
		// Trailing full stop after the PB time.
		assertEquals(Map.of("Doom of Mokhaiotl - Delve 4", 95.4),
			PbTrackerPlugin.parseDelveChatPb("Delve level: 4 duration: 1:50.40. Personal best: 1:35.40."));
	}

	@Test
	public void deepDelvesMapToTheGroupedEightPlusTierAndDelveEightStaysSeparate()
	{
		assertEquals(Map.of("Doom of Mokhaiotl - Delve 8+", 52.6),
			PbTrackerPlugin.parseDelveChatPb("Delve level: 8+ (16) duration: 1:48.60. Personal best: 0:52.60"));
		assertEquals(Map.of("Doom of Mokhaiotl - Delve 8", 69.6),
			PbTrackerPlugin.parseDelveChatPb("Delve level: 8 duration: 1:09.60 (new personal best)"));
	}

	@Test
	public void stripsColourTagsBeforeParsing()
	{
		assertEquals(Map.of("Doom of Mokhaiotl - Delve 1", 18.0),
			PbTrackerPlugin.parseDelveChatPb("Delve level: 1 duration: <col=ff0000>0:18.00</col> (new personal best)"));
	}

	@Test
	public void ignoresWholeSecondTimesThatCouldUndercutAPreciseRecord()
	{
		// Without the game's precise-timing setting chat rounds to whole seconds;
		// the backend keeps the lowest time, so "1:50" could overwrite a real 110.4.
		assertNull(PbTrackerPlugin.parseDelveChatPb("Delve level: 4 duration: 1:50 (new personal best)"));
		assertNull(PbTrackerPlugin.parseDelveChatPb("Delve level: 4 duration: 1:50. Personal best: 1:35"));
	}

	@Test
	public void ignoresTiersTheBackendDoesNotTrackAndUnrelatedMessages()
	{
		assertNull(PbTrackerPlugin.parseDelveChatPb("Delve level: 9 duration: 1:09.60 (new personal best)"));
		assertNull(PbTrackerPlugin.parseDelveChatPb("Delve level: 0 duration: 1:09.60 (new personal best)"));
		assertNull(PbTrackerPlugin.parseDelveChatPb("Delve level: 3 duration: 1:09.60"));
		assertNull(PbTrackerPlugin.parseDelveChatPb("Your Doom of Mokhaiotl kill count is: 12."));
		assertNull(PbTrackerPlugin.parseDelveChatPb(null));
	}
}
