package com.pbtracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyncClientUrlTest
{
	@Test
	public void buildsOrdinaryLeaderboardUrl()
	{
		assertEquals(
			"https://api.example/api/leaderboard/zulrah?limit=25",
			SyncClient.buildLeaderboardUrl("https://api.example/", "zulrah", 25, null));
	}

	@Test
	public void includesEncodedHighlightForDeepRankLookup()
	{
		assertEquals(
			"https://api.example/api/leaderboard/theatre%20of%20blood?limit=25&highlight=Player%20One",
			SyncClient.buildLeaderboardUrl("https://api.example", "theatre of blood", 25, "Player One"));
	}
}
