package com.pbtracker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A SHA-256 digest identifying an exact (account, display name, PB set)
 * combination, used to decide whether an automatic sync payload has
 * actually changed since the last successful sync - including across
 * RuneLite restarts, unlike PbTrackerPlugin's in-memory
 * AutomaticSyncDeduplicator (which this class does not replace; the two
 * serve different lifetimes and PbTrackerPlugin.buildSyncFingerprint
 * deliberately excludes display name for its own, unrelated reason).
 */
final class SyncFingerprint
{
	private SyncFingerprint()
	{
	}

	static String compute(String accountHash, String displayName, Map<String, Double> pbs)
	{
		String hash = accountHash == null ? "" : accountHash;
		String normalizedName = normalizeDisplayName(displayName);

		StringBuilder canonical = new StringBuilder();
		canonical.append(hash.length()).append(':').append(hash).append('|');
		canonical.append(normalizedName.length()).append(':').append(normalizedName).append('|');
		for (Map.Entry<String, Double> entry : new TreeMap<>(pbs).entrySet())
		{
			String key = entry.getKey();
			canonical.append(key.length())
				.append(':')
				.append(key)
				.append('=')
				.append(Long.toHexString(Double.doubleToLongBits(entry.getValue())))
				.append(';');
		}

		return sha256Hex(canonical.toString());
	}

	private static String normalizeDisplayName(String displayName)
	{
		return displayName == null ? "" : displayName.trim().toLowerCase(Locale.ROOT);
	}

	private static String sha256Hex(String input)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash)
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			// SHA-256 is a mandatory JDK algorithm (JLS/JCA guarantee) - this
			// is unreachable in practice, but fail loudly rather than
			// returning a bogus fingerprint that could silently disable the
			// skip-unchanged-sync optimization forever.
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
