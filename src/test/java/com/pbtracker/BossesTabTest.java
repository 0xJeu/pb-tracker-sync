package com.pbtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BossesTabTest
{
	@Test
	public void requestsFiftyLeaderboardRows()
	{
		assertEquals(50, BossesTab.LEADERBOARD_LIMIT);
	}
}
