package com.pbtracker;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Display-side mirror of the backend/frontend tracked-boss allowlist. The
 * backend rejects new untracked PBs, but filtering here also keeps historical
 * rows from resurfacing in the side panel.
 */
final class TrackedBosses
{
	private static final Pattern DOOM_TIMED_DELVE = Pattern.compile(
		"^doom of mokhaiotl - delve (?:[1-8]|8\\+)$"
	);

	private static final List<String> PREFIXES = List.of(
		"alchemical hydra", "amoxliatl", "araxxor", "chambers of xeric",
		"corrupted gauntlet", "gauntlet", "duke sucellus", "fortis colosseum",
		"sol heredit", "grotesque guardians", "hespori", "hueycoatl", "leviathan",
		"maggot king", "mimic", "nex", "phosani's nightmare", "nightmare",
		"phantom muspah", "royal titans", "shellbane gryphon", "theatre of blood",
		"tzhaar-ket-rak", "tzhaar fight cave", "fight caves", "tztok-jad",
		"tzkal-zuk", "inferno", "vardorvis", "vorkath", "whisperer", "yama",
		"zulrah", "tombs of amascut"
	);

	private TrackedBosses()
	{
	}

	static boolean isTracked(String boss)
	{
		if (boss == null)
		{
			return false;
		}
		String normalized = boss.trim().toLowerCase();
		if (normalized.startsWith("the "))
		{
			normalized = normalized.substring(4);
		}
		if (isDoomTimedDelve(normalized))
		{
			return true;
		}
		for (String prefix : PREFIXES)
		{
			if (normalized.startsWith(prefix))
			{
				return true;
			}
		}
		return false;
	}

	static boolean isDoomTimedDelve(String boss)
	{
		return boss != null && DOOM_TIMED_DELVE.matcher(boss.trim().toLowerCase()).matches();
	}
}
