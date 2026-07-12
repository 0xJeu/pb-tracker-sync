package com.pbtracker;

import javax.swing.ImageIcon;
import java.awt.Image;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real OSRS Wiki boss art (fetched once via the Wiki's page-image API and
 * bundled under resources/com/pbtracker/icons), scaled down to row-icon size.
 * Raid PB keys carry their mode/team-size as a " - "-delimited suffix (e.g.
 * "theatre of blood - hard - fastest overall (5 player hard mode)"), so only
 * the base name before the first " - " is used to look up the art - every
 * mode/size of a raid shares one icon, same as the website.
 */
final class BossIcons
{
	private BossIcons()
	{
	}

	private static final int SIZE = 26;
	private static final Map<String, ImageIcon> CACHE = new ConcurrentHashMap<>();
	private static final ImageIcon MISSING = new ImageIcon();

	/**
	 * Slugs with no art of their own that should fall back to a close
	 * relative's icon instead of rendering blank: the "awakened" DT2 bosses
	 * share their base form's icon, Demonic Brutus shares Brutus's, and the
	 * Tzhaar-Ket-Rak challenges (no dedicated Wiki art) borrow Jad's.
	 */
	private static final Map<String, String> ALIASES = Map.of(
		"duke_sucellus_(awakened)", "duke_sucellus",
		"leviathan_(awakened)", "leviathan",
		"vardorvis_(awakened)", "vardorvis",
		"whisperer_(awakened)", "whisperer",
		"demonic_brutus", "brutus",
		"tzhaar_ket_raks_fifth_challenge", "tztok_jad",
		"tzhaar_ket_raks_sixth_challenge", "tztok_jad"
	);

	/** Returns null if there's no art for this boss - callers should render without an icon in that case. */
	static ImageIcon get(String bossKey)
	{
		String slug = slugFor(bossKey);
		if (slug == null)
		{
			return null;
		}
		slug = ALIASES.getOrDefault(slug, slug);
		ImageIcon cached = CACHE.get(slug);
		if (cached != null)
		{
			return cached == MISSING ? null : cached;
		}

		URL url = BossIcons.class.getResource("icons/" + slug + ".png");
		if (url == null)
		{
			CACHE.put(slug, MISSING);
			return null;
		}

		ImageIcon full = new ImageIcon(url);
		Image scaled = full.getImage().getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH);
		ImageIcon icon = new ImageIcon(scaled);
		CACHE.put(slug, icon);
		return icon;
	}

	private static String slugFor(String bossKey)
	{
		if (bossKey == null || bossKey.trim().isEmpty())
		{
			return null;
		}
		String base = bossKey.trim().toLowerCase();
		int dash = base.indexOf(" - ");
		if (dash >= 0)
		{
			base = base.substring(0, dash);
		}
		// "the nightmare"/"the gauntlet"/"the corrupted gauntlet"/"the
		// hueycoatl" are stored with a "the " prefix the bundled art doesn't
		// have - mirrors BossGroups.normalize()'s existing convention.
		if (base.startsWith("the "))
		{
			base = base.substring(4);
		}
		return base.replace("'", "").replace(" ", "_").replace("-", "_");
	}
}
