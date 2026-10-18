package com.pbtracker;

import java.util.Map;
import net.runelite.api.ChatMessageType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DelveChatCaptureTest
{
	private static final String NEW_PB = "Delve level: 4 duration: 1:35.40 (new personal best)";
	private static final String REPEATED_PB = "Delve level: 4 duration: 1:50.40. Personal best: 1:35.40";
	private static final Map<String, Double> DELVE_4 = Map.of("Doom of Mokhaiotl - Delve 4", 95.4);

	@Test
	public void sendsANewDelvePbFromTheGame()
	{
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		assertEquals(DELVE_4, capture.pbToSync(true, ChatMessageType.GAMEMESSAGE, NEW_PB, 1L));
	}

	@Test
	public void acceptsSpamFilteredGameMessagesToo()
	{
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		assertEquals(DELVE_4, capture.pbToSync(true, ChatMessageType.SPAM, NEW_PB, 1L));
	}

	@Test
	public void sendsNothingWhenAutoSyncIsOff()
	{
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		assertNull(capture.pbToSync(false, ChatMessageType.GAMEMESSAGE, NEW_PB, 1L));
		// Turning auto-sync back on still sends it: nothing was recorded while off.
		assertEquals(DELVE_4, capture.pbToSync(true, ChatMessageType.GAMEMESSAGE, NEW_PB, 1L));
	}

	@Test
	public void ignoresTheSameTextTypedByAPlayer()
	{
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		assertNull(capture.pbToSync(true, ChatMessageType.PUBLICCHAT, NEW_PB, 1L));
		assertNull(capture.pbToSync(true, ChatMessageType.FRIENDSCHAT, NEW_PB, 1L));
		assertNull(capture.pbToSync(true, ChatMessageType.CLAN_CHAT, NEW_PB, 1L));
		assertNull(capture.pbToSync(true, ChatMessageType.PRIVATECHAT, NEW_PB, 1L));
	}

	@Test
	public void sendsAnUnchangedPbOnlyOncePerSession()
	{
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		assertEquals(DELVE_4, capture.pbToSync(true, ChatMessageType.GAMEMESSAGE, NEW_PB, 1L));
		// Every later delve 4 repeats the same PB - no further sends.
		assertNull(capture.pbToSync(true, ChatMessageType.GAMEMESSAGE, REPEATED_PB, 1L));
		assertNull(capture.pbToSync(true, ChatMessageType.GAMEMESSAGE, REPEATED_PB, 1L));
	}

	@Test
	public void sendsAgainWhenThePbImproves()
	{
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		capture.pbToSync(true, ChatMessageType.GAMEMESSAGE, NEW_PB, 1L);
		assertEquals(Map.of("Doom of Mokhaiotl - Delve 4", 90.0), capture.pbToSync(true, ChatMessageType.GAMEMESSAGE,
			"Delve level: 4 duration: 1:30.00 (new personal best)", 1L));
	}

	@Test
	public void tracksEachTierAndAccountSeparately()
	{
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		capture.pbToSync(true, ChatMessageType.GAMEMESSAGE, NEW_PB, 1L);
		// Same tier and time on another account on this client still goes out.
		assertEquals(DELVE_4, capture.pbToSync(true, ChatMessageType.GAMEMESSAGE, NEW_PB, 2L));
		// A different tier on the first account is unaffected by delve 4's entry.
		assertEquals(Map.of("Doom of Mokhaiotl - Delve 5", 95.4), capture.pbToSync(true, ChatMessageType.GAMEMESSAGE,
			"Delve level: 5 duration: 1:35.40 (new personal best)", 1L));
	}

	@Test
	public void ignoresUnrelatedAndUntrustedMessages()
	{
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		assertNull(capture.pbToSync(true, ChatMessageType.GAMEMESSAGE, "Your Doom of Mokhaiotl kill count is: 12.", 1L));
		assertNull(capture.pbToSync(true, ChatMessageType.GAMEMESSAGE,
			"Delve level: 4 duration: 1:35 (new personal best)", 1L));
		// A rejected line records nothing, so the precise PB still goes out afterwards.
		assertEquals(DELVE_4, capture.pbToSync(true, ChatMessageType.GAMEMESSAGE, NEW_PB, 1L));
	}

	@Test
	public void flagsABossPbThatEqualsADelveTimeSeenThisSession()
	{
		// RuneLite's Chat Commands keeps the delve PB and applies it to the next
		// boss killed, so that boss's "new PB" equals the delve time exactly.
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		capture.noteDelveTime(ChatMessageType.GAMEMESSAGE, "Delve level: 7 duration: 1:05.40. Personal best: 1:01.20", 1L);
		assertTrue(capture.isLikelyDelveLeak(1L, "phosani's nightmare", 61.2));
		// The run's own duration is not what RuneLite keeps from a non-PB line.
		assertFalse(capture.isLikelyDelveLeak(1L, "phosani's nightmare", 65.4));
	}

	@Test
	public void flagsLeaksOfWholeSecondDelveTimesToo()
	{
		// We never sync whole-second delve times, but RuneLite still leaks them.
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		capture.noteDelveTime(ChatMessageType.GAMEMESSAGE, "Delve level: 4 duration: 0:46 (new personal best)", 1L);
		assertTrue(capture.isLikelyDelveLeak(1L, "yama", 46.0));
	}

	@Test
	public void onlyFlagsTheSameAccountAndNonDoomBosses()
	{
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		capture.noteDelveTime(ChatMessageType.GAMEMESSAGE, NEW_PB, 1L);
		assertFalse(capture.isLikelyDelveLeak(2L, "yama", 95.4));
		assertFalse(capture.isLikelyDelveLeak(1L, "Doom of Mokhaiotl - Delve 4", 95.4));
		assertFalse(capture.isLikelyDelveLeak(1L, "yama", 95.6));
	}

	@Test
	public void ignoresDelveTextTypedByPlayers()
	{
		PbTrackerPlugin.DelveChatCapture capture = new PbTrackerPlugin.DelveChatCapture();
		capture.noteDelveTime(ChatMessageType.PUBLICCHAT, NEW_PB, 1L);
		assertFalse(capture.isLikelyDelveLeak(1L, "yama", 95.4));
	}
}
