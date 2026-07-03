package com.pbtracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a real RuneLite client with PbTrackerPlugin pre-loaded, exactly
 * like RuneLite's official example-plugin template does. Run via:
 *
 *   ./gradlew run
 *
 * from the plugin/ directory. No need to clone the RuneLite client source.
 */
public class PbTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PbTrackerPlugin.class);
		RuneLite.main(args);
	}
}
