package com.pbtracker;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SyncFingerprintTest
{
	@Test
	public void isIndependentOfMapIterationOrder()
	{
		Map<String, Double> a = new LinkedHashMap<>();
		a.put("zulrah", 42.6);
		a.put("vorkath", 78.0);

		Map<String, Double> b = new LinkedHashMap<>();
		b.put("vorkath", 78.0);
		b.put("zulrah", 42.6);

		assertEquals(
			SyncFingerprint.compute("acct-a", "Zezima", a),
			SyncFingerprint.compute("acct-a", "Zezima", b));
	}

	@Test
	public void producesA64CharacterHexDigest()
	{
		String fingerprint = SyncFingerprint.compute("acct-a", "Zezima", new TreeMap<>());
		assertEquals(64, fingerprint.length());
		assertTrue(fingerprint.matches("[0-9a-f]{64}"));
	}

	@Test
	public void changesWhenAccountHashChanges()
	{
		Map<String, Double> pbs = new TreeMap<>();
		pbs.put("zulrah", 42.6);

		assertNotEquals(
			SyncFingerprint.compute("acct-a", "Zezima", pbs),
			SyncFingerprint.compute("acct-b", "Zezima", pbs));
	}

	@Test
	public void changesWhenDisplayNameChanges()
	{
		Map<String, Double> pbs = new TreeMap<>();
		pbs.put("zulrah", 42.6);

		assertNotEquals(
			SyncFingerprint.compute("acct-a", "Zezima", pbs),
			SyncFingerprint.compute("acct-a", "ZezimaTwo", pbs));
	}

	@Test
	public void normalizesDisplayNameCaseAndWhitespace()
	{
		Map<String, Double> pbs = new TreeMap<>();
		pbs.put("zulrah", 42.6);

		assertEquals(
			SyncFingerprint.compute("acct-a", "Zezima", pbs),
			SyncFingerprint.compute("acct-a", "  ZEZIMA  ", pbs));
	}

	@Test
	public void changesWhenAPbValueChanges()
	{
		Map<String, Double> a = new TreeMap<>();
		a.put("zulrah", 42.6);

		Map<String, Double> b = new TreeMap<>();
		b.put("zulrah", 42.5);

		assertNotEquals(
			SyncFingerprint.compute("acct-a", "Zezima", a),
			SyncFingerprint.compute("acct-a", "Zezima", b));
	}

	@Test
	public void changesWhenABossKeyIsAddedOrRemoved()
	{
		Map<String, Double> a = new TreeMap<>();
		a.put("zulrah", 42.6);

		Map<String, Double> b = new TreeMap<>();
		b.put("zulrah", 42.6);
		b.put("vorkath", 78.0);

		assertNotEquals(
			SyncFingerprint.compute("acct-a", "Zezima", a),
			SyncFingerprint.compute("acct-a", "Zezima", b));
	}

	@Test
	public void doesNotCollideWhenDelimiterCharacterShiftsBetweenAccountHashAndDisplayName()
	{
		Map<String, Double> pbs = new TreeMap<>();
		pbs.put("zulrah", 42.6);

		assertNotEquals(
			SyncFingerprint.compute("x|y", "z", pbs),
			SyncFingerprint.compute("x", "y|z", pbs));
	}

	@Test
	public void isStableAcrossRepeatedCallsWithTheSameInput()
	{
		Map<String, Double> pbs = new TreeMap<>();
		pbs.put("zulrah", 42.6);

		assertEquals(
			SyncFingerprint.compute("acct-a", "Zezima", pbs),
			SyncFingerprint.compute("acct-a", "Zezima", pbs));
	}
}
