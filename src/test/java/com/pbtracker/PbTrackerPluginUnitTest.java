package com.pbtracker;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
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
	public void syncNowOnlyTriggersForUserCheckingTheBox()
	{
		assertTrue(PbTrackerPlugin.shouldTriggerSyncNow("true"));
		assertFalse(PbTrackerPlugin.shouldTriggerSyncNow("false"));
	}
}
