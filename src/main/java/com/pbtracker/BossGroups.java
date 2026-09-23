package com.pbtracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ports frontend/src/lib/bossGroups.ts so the side panel can offer the same
 * "collapse raids into one row, then pick mode/team-size" experience as the
 * website, instead of a flat 200+ entry boss list. Kept behaviourally
 * identical on purpose - if the website's grouping logic changes, this
 * should change to match.
 */
final class BossGroups
{
	private BossGroups()
	{
	}

	enum Category
	{
		RAIDS("Raids"),
		SLAYER_MONSTERS("Slayer Monsters"),
		MINIGAMES("Minigames & Challenges"),
		BOSSES("Bosses"),
		OTHER("Other");

		final String label;

		Category(String label)
		{
			this.label = label;
		}
	}

	private static final List<String> RAID_PREFIXES = List.of("chambers of xeric", "theatre of blood", "tombs of amascut");
	private static final List<String> GROUPED_BOSS_PREFIXES = List.of(
		"chambers of xeric", "theatre of blood", "tombs of amascut", "the nightmare"
	);

	// The 6 Tzhaar-Ket-Rak challenges are separate boss keys entirely (no
	// " - "-delimited mode/size suffix like a raid variant), so they get
	// their own grouping path rather than going through parseRaidVariant's
	// raid-heading logic - collapsed into one "Tzhaar-Ket-Rak's Challenges"
	// heading instead of 6 flat rows cluttering the list.
	private static final String TZHAAR_CHALLENGE_PREFIX = "tzhaar-ket-rak's";
	private static final String TZHAAR_CHALLENGE_BASE = "tzhaar-ket-rak's challenges";
	// Built via titleCase() rather than hand-capitalized, so it matches what
	// getRaidModes() independently recomputes as titleCase(base) when the
	// user drills into this "raid base" from the picker.
	private static final String TZHAAR_CHALLENGES_HEADING = PbTrackerPlugin.titleCase(TZHAAR_CHALLENGE_BASE);
	private static final Map<String, Integer> TZHAAR_CHALLENGE_ORDER = Map.of(
		"first", 1, "second", 2, "third", 3, "fourth", 4, "fifth", 5, "sixth", 6
	);

	// Only the exact tracked delve tiers are grouped (TrackedBosses'
	// pattern), so an unsupported key like the bare "doom of mokhaiotl"
	// stays a visible flat row instead of becoming a bogus tier.
	private static final String DOOM_BASE = "doom of mokhaiotl";
	private static final String DOOM_HEADING = PbTrackerPlugin.titleCase(DOOM_BASE);
	private static final String DOOM_DELVE_PREFIX = DOOM_BASE + " - delve ";

	private static final List<String> SLAYER_MONSTERS = List.of(
		"kraken", "cerberus", "thermonuclear smoke devil", "alchemical hydra", "abyssal sire",
		"grotesque guardians", "araxxor", "shellbane gryphon", "skotizo", "kalphite queen"
	);

	private static final List<String> MINIGAMES = List.of(
		"tempoross", "wintertodt", "barbarian assault", "guardians of the rift", "gauntlet",
		"corrupted gauntlet", "inferno", "tzhaar fight cave", "fortis colosseum", "hallowed sepulchre",
		"shayzien basic agility course", "tzhaar-ket-rak's first challenge", "tzhaar-ket-rak's second challenge",
		"tzhaar-ket-rak's third challenge", "tzhaar-ket-rak's fourth challenge"
	);

	private static final List<String> KNOWN_BOSSES = List.of(
		"nex", "zulrah", "vorkath", "sarachnis", "vardorvis", "duke sucellus", "leviathan", "whisperer",
		"general graardor", "kree'arra", "commander zilyana", "k'ril tsutsaroth", "callisto", "artio",
		"venenatis", "spindel", "vet'ion", "calvar'ion", "chaos elemental", "chaos fanatic",
		"crazy archaeologist", "deranged archaeologist", "king black dragon", "giant mole", "zalcano",
		"obor", "bryophyta", "dagannoth rex", "dagannoth prime", "dagannoth supreme", "corporeal beast",
		"nightmare", "phosani's nightmare", "scorpia", "tztok-jad", "amoxliatl", "brutus",
		"fragment of seren", "galvek", "hespori", "maggot king", "mimic", "phantom muspah",
		"royal titans", "scurrius", "shellbane gryphon", "hueycoatl", "yama"
	);

	// Entry < Normal < Hard/Challenge Mode/Expert - see bossGroups.ts's
	// MODE_PRIORITY for why they share a rank.
	private static final Map<String, Integer> MODE_PRIORITY = Map.of(
		"entry", 1,
		"", 2,
		"hard", 3,
		"challenge mode", 3,
		"expert", 3
	);

	static String normalize(String key)
	{
		String lower = key.trim().toLowerCase();
		return lower.startsWith("the ") ? lower.substring(4) : lower;
	}

	private static boolean matchesCurated(String key, List<String> curated)
	{
		String n = normalize(key);
		for (String name : curated)
		{
			if (n.equals(name) || n.startsWith(name + " (") || n.startsWith(name + " -"))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isRaid(String key)
	{
		String lower = key.trim().toLowerCase();
		for (String p : RAID_PREFIXES)
		{
			if (lower.equals(p) || lower.startsWith(p + " -") || lower.startsWith(p + " "))
			{
				return true;
			}
		}
		return false;
	}

	static boolean isGroupedVariant(String key)
	{
		String lower = key.trim().toLowerCase();
		if (lower.startsWith(TZHAAR_CHALLENGE_PREFIX) || TrackedBosses.isDoomTimedDelve(lower))
		{
			return true;
		}
		for (String p : GROUPED_BOSS_PREFIXES)
		{
			if (lower.equals(p) || lower.startsWith(p + " -"))
			{
				return true;
			}
		}
		return false;
	}

	static Category categorize(String bossKey)
	{
		if (isRaid(bossKey))
		{
			return Category.RAIDS;
		}
		if (matchesCurated(bossKey, SLAYER_MONSTERS))
		{
			return Category.SLAYER_MONSTERS;
		}
		if (matchesCurated(bossKey, MINIGAMES))
		{
			return Category.MINIGAMES;
		}
		if (TrackedBosses.isDoomTimedDelve(bossKey) || matchesCurated(bossKey, KNOWN_BOSSES))
		{
			return Category.BOSSES;
		}
		if (normalize(bossKey).startsWith("nightmare "))
		{
			return Category.BOSSES;
		}
		return Category.OTHER;
	}

	static final class RaidVariant
	{
		final String key;
		final String base;
		final String mode;
		final String heading;
		final String subLabel;

		RaidVariant(String key, String base, String mode, String heading, String subLabel)
		{
			this.key = key;
			this.base = base;
			this.mode = mode;
			this.heading = heading;
			this.subLabel = subLabel;
		}
	}

	private static RaidVariant parseRaidVariant(String bossKey)
	{
		String lower = bossKey.trim().toLowerCase();
		if (lower.startsWith(TZHAAR_CHALLENGE_PREFIX))
		{
			String subLabel = PbTrackerPlugin.titleCase(lower.substring(TZHAAR_CHALLENGE_PREFIX.length()).trim());
			return new RaidVariant(bossKey, TZHAAR_CHALLENGE_BASE, "", TZHAAR_CHALLENGES_HEADING, subLabel);
		}
		if (TrackedBosses.isDoomTimedDelve(lower))
		{
			String tier = lower.substring(DOOM_DELVE_PREFIX.length());
			return new RaidVariant(bossKey, DOOM_BASE, "", DOOM_HEADING, "Delve " + tier);
		}

		String[] segments = bossKey.trim().toLowerCase().split(" - ");
		for (int i = 0; i < segments.length; i++)
		{
			segments[i] = segments[i].trim();
		}
		String base = segments[0];
		String mode = segments.length > 2
			? String.join(" - ", java.util.Arrays.copyOfRange(segments, 1, segments.length - 1))
			: "";
		String heading = mode.isEmpty() ? PbTrackerPlugin.titleCase(base) : PbTrackerPlugin.titleCase(base) + " - " + PbTrackerPlugin.titleCase(mode);
		boolean isBareOverall = segments.length == 1;
		String subLabel = isBareOverall ? "Overall" : PbTrackerPlugin.titleCase(segments[segments.length - 1]);
		return new RaidVariant(bossKey, base, mode, heading, subLabel);
	}

	private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)(\\+)?");

	/** Also the delve depth for Doom tiers - an open-ended "N+" tier (Delve 8+, 6+ Players) sorts right after N. */
	private static double teamSizeRank(String subLabel)
	{
		String lower = subLabel.toLowerCase();
		if (lower.contains("solo"))
		{
			return 1;
		}
		for (Map.Entry<String, Integer> ordinal : TZHAAR_CHALLENGE_ORDER.entrySet())
		{
			if (lower.startsWith(ordinal.getKey()))
			{
				return ordinal.getValue();
			}
		}
		Matcher m = DIGIT_PATTERN.matcher(lower);
		if (!m.find())
		{
			return 999;
		}
		return Integer.parseInt(m.group(1)) + (m.group(2) != null ? 0.5 : 0);
	}

	private static int variantRank(String subLabel)
	{
		return subLabel.toLowerCase().startsWith("fastest overall") ? 0 : 1;
	}

	private static final Map<Integer, String> SIZE_NICKNAMES = Map.of(1, "Solo", 2, "Duo", 3, "Trio");
	private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)(\\+)?\\s*players?");
	private static final Pattern SOLO_PATTERN = Pattern.compile("\\bsolo\\b");

	/** Null means "no team size in this label" (e.g. legacy "(Former)" entries) - caller falls back to the full label. */
	static String sizeLabel(String label)
	{
		String lower = label.toLowerCase();
		if (SOLO_PATTERN.matcher(lower).find())
		{
			return "Solo";
		}
		Matcher m = SIZE_PATTERN.matcher(lower);
		if (!m.find())
		{
			return null;
		}
		int n = Integer.parseInt(m.group(1));
		if (m.group(2) != null)
		{
			return n + "+";
		}
		return SIZE_NICKNAMES.getOrDefault(n, n + "-Man");
	}

	enum VariantKind
	{
		OVERALL, ROOM, OTHER, LEGACY
	}

	private static VariantKind variantKind(String label)
	{
		String lower = label.toLowerCase();
		if (lower.contains("(former)"))
		{
			return VariantKind.LEGACY;
		}
		if (lower.equals("overall") || lower.startsWith("fastest overall"))
		{
			return VariantKind.OVERALL;
		}
		if (lower.startsWith("fastest room"))
		{
			return VariantKind.ROOM;
		}
		return VariantKind.OTHER;
	}

	static final class RaidGroup
	{
		final String heading;
		final String base;
		final String mode;
		final List<KeyLabel> variants;

		RaidGroup(String heading, String base, String mode, List<KeyLabel> variants)
		{
			this.heading = heading;
			this.base = base;
			this.mode = mode;
			this.variants = variants;
		}
	}

	static final class KeyLabel
	{
		final String key;
		final String label;

		KeyLabel(String key, String label)
		{
			this.key = key;
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** One RaidGroup per raid+mode heading, found across every bare boss key in `bosses`. */
	static List<RaidGroup> groupedRaidGroups(List<String> bosses)
	{
		List<String> groupedKeys = new ArrayList<>();
		for (String b : bosses)
		{
			if (isGroupedVariant(b))
			{
				groupedKeys.add(b);
			}
		}

		Map<String, List<RaidVariant>> byHeading = new LinkedHashMap<>();
		for (String key : groupedKeys)
		{
			RaidVariant variant = parseRaidVariant(key);
			byHeading.computeIfAbsent(variant.heading, h -> new ArrayList<>()).add(variant);
		}

		List<RaidGroup> groups = new ArrayList<>();
		for (List<RaidVariant> variants : byHeading.values())
		{
			List<RaidVariant> sorted = new ArrayList<>(variants);
			sorted.sort(Comparator.<RaidVariant>comparingDouble(v -> teamSizeRank(v.subLabel))
				.thenComparingInt(v -> variantRank(v.subLabel)));

			// Compact "Fastest Overall (1 Player Hard Mode)" down to just
			// "1 Player" (sizeLabel()) like the player-specific grouping
			// already does - otherwise every team-size button repeats the
			// same wall of near-identical verbose text and doesn't read as
			// a size picker at all. Two variants can still share a team
			// size (an "Overall" time and a "Room" time), so disambiguate
			// those with an "Overall"/"Room" suffix only when they collide.
			List<String> niceLabels = new ArrayList<>();
			for (RaidVariant v : sorted)
			{
				String nice = sizeLabel(v.subLabel);
				niceLabels.add(nice != null ? nice : v.subLabel);
			}
			Map<String, Integer> labelCounts = new LinkedHashMap<>();
			for (String label : niceLabels)
			{
				labelCounts.merge(label, 1, Integer::sum);
			}
			List<KeyLabel> variantList = new ArrayList<>();
			for (int i = 0; i < sorted.size(); i++)
			{
				String nice = niceLabels.get(i);
				String finalLabel = labelCounts.get(nice) > 1 ? nice + " - " + kindName(variantKind(sorted.get(i).subLabel)) : nice;
				variantList.add(new KeyLabel(sorted.get(i).key, finalLabel));
			}
			groups.add(new RaidGroup(variants.get(0).heading, variants.get(0).base, variants.get(0).mode, variantList));
		}

		groups.sort(Comparator.<RaidGroup, String>comparing(g -> g.base)
			.thenComparingInt(g -> MODE_PRIORITY.getOrDefault(g.mode, g.mode.isEmpty() ? 0 : 50)));
		return groups;
	}

	static final class RaidBase
	{
		final String base;
		final String label;

		RaidBase(String base, String label)
		{
			this.base = base;
			this.label = label;
		}
	}

	/** Every non-raid boss key in `bosses`, deduplicated - for enumerating "every boss that could show up" regardless of whether this player has PBs for it. */
	static List<String> getFlatBossKeys(List<String> bosses)
	{
		List<String> flat = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (String b : bosses)
		{
			if (!isGroupedVariant(b) && seen.add(b.trim().toLowerCase()))
			{
				flat.add(b);
			}
		}
		return flat;
	}

	/** One row per raid base (mode-independent) - for the top-level "pick a raid" list. */
	static List<RaidBase> getRaidBases(List<String> bosses)
	{
		Map<String, RaidBase> seen = new LinkedHashMap<>();
		for (RaidGroup group : groupedRaidGroups(bosses))
		{
			String label = group.heading.contains(" - ") ? group.heading.substring(0, group.heading.indexOf(" - ")) : group.heading;
			String base = label.toLowerCase();
			seen.putIfAbsent(base, new RaidBase(base, label));
		}
		List<RaidBase> result = new ArrayList<>(seen.values());
		result.sort(Comparator.comparing(b -> b.label));
		return result;
	}

	static final class RaidMode
	{
		final String modeLabel;
		final List<KeyLabel> variants;

		RaidMode(String modeLabel, List<KeyLabel> variants)
		{
			this.modeLabel = modeLabel;
			this.variants = variants;
		}
	}

	/** Every mode (Normal/Entry/Hard/Challenge Mode/Expert) for a single raid base. */
	static List<RaidMode> getRaidModes(List<String> bosses, String base)
	{
		String baseLabel = PbTrackerPlugin.titleCase(base);
		List<RaidMode> modes = new ArrayList<>();
		for (RaidGroup group : groupedRaidGroups(bosses))
		{
			if (group.heading.equals(baseLabel) || group.heading.startsWith(baseLabel + " - "))
			{
				String modeLabel = group.heading.equals(baseLabel) ? "Normal" : group.heading.substring(baseLabel.length() + 3);
				modes.add(new RaidMode(modeLabel, group.variants));
			}
		}
		return modes;
	}

	// ---- Player-PB-specific grouping (for "My PBs" / player search results) ----

	static final class PlayerPb
	{
		final String boss;
		final double timeSeconds;
		final int rank;
		final String updatedAt;

		PlayerPb(String boss, double timeSeconds, int rank, String updatedAt)
		{
			this.boss = boss;
			this.timeSeconds = timeSeconds;
			this.rank = rank;
			this.updatedAt = updatedAt;
		}
	}

	static final class PlayerRaidVariant
	{
		final String key;
		final String label;
		final VariantKind kind;
		final double timeSeconds;
		final int rank;
		final String updatedAt;

		PlayerRaidVariant(String key, String label, VariantKind kind, double timeSeconds, int rank, String updatedAt)
		{
			this.key = key;
			this.label = label;
			this.kind = kind;
			this.timeSeconds = timeSeconds;
			this.rank = rank;
			this.updatedAt = updatedAt;
		}

		PlayerRaidVariant withLabel(String newLabel)
		{
			return new PlayerRaidVariant(key, newLabel, kind, timeSeconds, rank, updatedAt);
		}
	}

	static final class PlayerRaidGroup
	{
		final String heading;
		final PlayerRaidVariant summary;
		final List<PlayerRaidVariant> variants;
		final SummaryRule summaryRule;

		PlayerRaidGroup(String heading, PlayerRaidVariant summary, List<PlayerRaidVariant> variants, SummaryRule summaryRule)
		{
			this.heading = heading;
			this.summary = summary;
			this.variants = variants;
			this.summaryRule = summaryRule;
		}
	}

	enum SummaryRule
	{
		FASTEST, DEEPEST
	}

	// Which variant a grouped boss's collapsed row summarizes. Anything not
	// listed uses the fastest-time rule. Doom's delve tiers are separate
	// progress milestones rather than interchangeable team sizes, so its row
	// shows how deep the player has got instead of their quickest (shallowest)
	// tier.
	private static final Map<String, SummaryRule> SUMMARY_RULE_BY_BASE = Map.of(
		DOOM_BASE, SummaryRule.DEEPEST
	);

	static SummaryRule summaryRuleForBase(String base)
	{
		return SUMMARY_RULE_BY_BASE.getOrDefault(base, SummaryRule.FASTEST);
	}

	static final class GroupedPlayerPbs
	{
		final List<PlayerRaidGroup> groups;
		final List<PlayerPb> flat;

		GroupedPlayerPbs(List<PlayerRaidGroup> groups, List<PlayerPb> flat)
		{
			this.groups = groups;
			this.flat = flat;
		}
	}

	private static final VariantKind[] KIND_PREFERENCE = { VariantKind.OVERALL, VariantKind.ROOM, VariantKind.OTHER, VariantKind.LEGACY };

	private static PlayerRaidVariant pickFastestSummary(List<PlayerRaidVariant> variants)
	{
		for (VariantKind kind : KIND_PREFERENCE)
		{
			PlayerRaidVariant fastest = null;
			for (PlayerRaidVariant v : variants)
			{
				if (v.kind == kind && (fastest == null || v.timeSeconds < fastest.timeSeconds))
				{
					fastest = v;
				}
			}
			if (fastest != null)
			{
				return fastest;
			}
		}
		return variants.get(0);
	}

	/** Deepest tier reached ("Delve 8+" beats "Delve 8"), with the fastest time breaking a tie between duplicate keys for one tier. */
	private static PlayerRaidVariant pickDeepestSummary(List<PlayerRaidVariant> variants, List<String> subLabels)
	{
		int best = 0;
		for (int i = 1; i < variants.size(); i++)
		{
			double depth = teamSizeRank(subLabels.get(i)) - teamSizeRank(subLabels.get(best));
			if (depth > 0 || (depth == 0 && variants.get(i).timeSeconds < variants.get(best).timeSeconds))
			{
				best = i;
			}
		}
		return variants.get(best);
	}

	static PlayerRaidVariant pickBestRanked(List<PlayerRaidVariant> variants)
	{
		return variants.stream()
			.min(Comparator.comparingInt((PlayerRaidVariant v) -> v.rank)
				.thenComparingDouble(v -> v.timeSeconds))
			.orElseThrow(() -> new IllegalArgumentException("variants must not be empty"));
	}

	/** Groups a player's own synced PBs into one row per raid+mode heading, for display. */
	static GroupedPlayerPbs groupPlayerRaidPbs(List<PlayerPb> pbs)
	{
		List<PlayerPb> flat = new ArrayList<>();
		Map<String, List<Object[]>> byHeading = new LinkedHashMap<>(); // [PlayerRaidVariant, subLabel]
		Map<String, String> baseByHeading = new LinkedHashMap<>();

		for (PlayerPb pb : pbs)
		{
			if (!isGroupedVariant(pb.boss))
			{
				flat.add(pb);
				continue;
			}
			RaidVariant parsed = parseRaidVariant(pb.boss);
			String niceLabel = sizeLabel(parsed.subLabel);
			PlayerRaidVariant entry = new PlayerRaidVariant(
				pb.boss, niceLabel != null ? niceLabel : parsed.subLabel, variantKind(parsed.subLabel),
				pb.timeSeconds, pb.rank, pb.updatedAt
			);
			byHeading.computeIfAbsent(parsed.heading, h -> new ArrayList<>()).add(new Object[] { entry, parsed.subLabel });
			baseByHeading.put(parsed.heading, parsed.base);
		}

		List<PlayerRaidGroup> groups = new ArrayList<>();
		for (Map.Entry<String, List<Object[]>> entry : byHeading.entrySet())
		{
			List<Object[]> raw = entry.getValue();
			raw.sort(Comparator.<Object[]>comparingDouble(o -> teamSizeRank((String) o[1]))
				.thenComparingInt(o -> variantRank((String) o[1])));

			List<PlayerRaidVariant> sorted = new ArrayList<>();
			List<String> subLabels = new ArrayList<>();
			for (Object[] o : raw)
			{
				sorted.add((PlayerRaidVariant) o[0]);
				subLabels.add((String) o[1]);
			}

			SummaryRule rule = summaryRuleForBase(baseByHeading.get(entry.getKey()));
			PlayerRaidVariant summary = rule == SummaryRule.DEEPEST
				? pickDeepestSummary(sorted, subLabels)
				: pickFastestSummary(sorted);

			Map<String, Integer> labelCounts = new LinkedHashMap<>();
			for (PlayerRaidVariant v : sorted)
			{
				labelCounts.merge(v.label, 1, Integer::sum);
			}
			List<PlayerRaidVariant> labeled = new ArrayList<>();
			for (PlayerRaidVariant v : sorted)
			{
				labeled.add(labelCounts.get(v.label) > 1 ? v.withLabel(v.label + " - " + kindName(v.kind)) : v);
			}

			groups.add(new PlayerRaidGroup(entry.getKey(), summary, labeled, rule));
		}

		groups.sort(Comparator.comparing(g -> g.heading));
		return new GroupedPlayerPbs(groups, flat);
	}

	private static String kindName(VariantKind kind)
	{
		switch (kind)
		{
			case OVERALL:
				return "Overall";
			case ROOM:
				return "Room";
			case LEGACY:
				return "Legacy";
			default:
				return "Other";
		}
	}
}
