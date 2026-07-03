package com.pbtracker;

import java.util.Arrays;
import java.util.List;

/**
 * Best-effort list of bosses/activities that RuneLite's built-in Chat Commands
 * plugin tracks a "personal best" time for (stored under the RSProfile config
 * group "personalbest", keyed by the boss name lowercased).
 * <p>
 * This list is not guaranteed to be 100% complete or byte-for-byte accurate to
 * RuneLite's internal key names -- Jagex adds new bosses regularly, and a few
 * keys are irregular (e.g. raids, multi-mode bosses). If a boss isn't showing
 * up after syncing, check what key RuneLite actually stored by hovering the
 * "Personal Best" line in the collection log plugin panel, or by running
 * "!pb <boss name>" in game, and adjust the string here to match.
 */
final class BossList
{
	private BossList()
	{
	}

	static final List<String> BOSSES = Arrays.asList(
		"Abyssal Sire",
		"Alchemical Hydra",
		"Amoxliatl",
		"Araxxor",
		"Artio",
		"Barrows Chests",
		"Bryophyta",
		"Callisto",
		"Calvar'ion",
		"Cerberus",
		"Chaos Elemental",
		"Chaos Fanatic",
		"Commander Zilyana",
		"Corporeal Beast",
		"Crazy Archaeologist",
		"Dagannoth Prime",
		"Dagannoth Rex",
		"Dagannoth Supreme",
		"Deranged Archaeologist",
		"Duke Sucellus",
		"General Graardor",
		"Giant Mole",
		"Grotesque Guardians",
		"Hespori",
		"The Hueycoatl",
		"Kalphite Queen",
		"King Black Dragon",
		"Kraken",
		"Kree'arra",
		"K'ril Tsutsaroth",
		"The Leviathan",
		"Moons of Peril",
		"Nex",
		"The Nightmare",
		"Phosani's Nightmare",
		"Obor",
		"Phantom Muspah",
		"Royal Titans",
		"Sarachnis",
		"Scorpia",
		"Scurrius",
		"Skotizo",
		"Sol Heredit",
		"Spindel",
		"Tempoross",
		"The Corrupted Gauntlet",
		"The Gauntlet",
		"Thermonuclear Smoke Devil",
		"TzKal-Zuk",
		"TzTok-Jad",
		"Vardorvis",
		"Venenatis",
		"Vet'ion",
		"Vorkath",
		"Wintertodt",
		"Yama",
		"Zalcano",
		"Zulrah",
		"Chambers of Xeric",
		"Chambers of Xeric: Challenge Mode",
		"Theatre of Blood",
		"Theatre of Blood: Hard Mode",
		"Tombs of Amascut",
		"Tombs of Amascut: Expert Mode"
	);
}
