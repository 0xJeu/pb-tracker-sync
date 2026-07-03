package com.pbtracker;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PbTrackerPluginUnitTest
{
	@Test
	public void pluginClassLoads()
	{
		assertNotNull(PbTrackerPlugin.class);
	}
}
