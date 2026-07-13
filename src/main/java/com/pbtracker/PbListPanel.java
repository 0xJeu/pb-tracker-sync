package com.pbtracker;

import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Renders a player's synced PBs as: a headline "Overall" stat (their single
 * best hiscore rank across everything they track), a "Top Bosses" section
 * (their 5 best-ranked performances), and a collapsible "All Bosses" section
 * listing every boss this plugin knows about - with a dash for anything the
 * player hasn't recorded a time for yet, not just the ones they have.
 * Reused by both the "My PBs" and "Player Search" tabs.
 * <p>
 * Implements Scrollable so the enclosing JScrollPane's JViewport clamps this
 * panel's width to the actual visible width instead of using its raw (and,
 * because of the wrapped JTextArea labels, misleadingly wide) preferred
 * size - without this, a plain JPanel isn't Scrollable-aware, so the
 * viewport just takes the view's inflated preferred width verbatim, which is
 * what let long headings force the whole panel wider instead of wrapping.
 */
class PbListPanel extends JPanel implements Scrollable
{
	private final SpriteManager spriteManager;
	private final JPanel rowsContainer = new JPanel();

	private List<String> allBosses = List.of();
	private final Set<String> expandedHeadings = new HashSet<>();
	private boolean allBossesSectionExpanded = true;

	private SyncClient.PlayerLookupResponse lastPlayer;
	private BiConsumer<String, String> lastOnBossClick;

	PbListPanel(SpriteManager spriteManager)
	{
		this.spriteManager = spriteManager;
		setLayout(new BorderLayout());
		setBackground(PbTrackerTheme.BG);

		rowsContainer.setLayout(new BoxLayout(rowsContainer, BoxLayout.Y_AXIS));
		rowsContainer.setBackground(PbTrackerTheme.BG);
		add(rowsContainer, BorderLayout.NORTH);
	}

	/** Every boss key this plugin knows about system-wide - used to show a dash row for anything this player hasn't done yet. */
	void setAllBosses(List<String> allBosses)
	{
		this.allBosses = allBosses;
	}

	@Override
	public Dimension getPreferredScrollableViewportSize()
	{
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return 16;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return visibleRect.height;
	}

	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight()
	{
		return false;
	}

	void showMessage(String text)
	{
		lastPlayer = null;
		rowsContainer.removeAll();
		JLabel label = new JLabel("<html><center>" + text + "</center></html>");
		label.setForeground(PbTrackerTheme.TEXT_DIM);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(BorderFactory.createEmptyBorder(16, 8, 16, 8));
		rowsContainer.add(label);
		revalidate();
		repaint();
	}

	/** One row's worth of display data, merging the global "every known boss" template with this player's actual recorded times. */
	private static final class DisplayRow
	{
		final String heading;
		final String iconKey;
		final String primaryName;
		final String subtitle;
		final boolean hasData;
		final double timeSeconds;
		final int rank;
		final String clickKey;
		final List<BossGroups.PlayerRaidVariant> variants;

		DisplayRow(String heading, String iconKey, String primaryName, String subtitle, boolean hasData,
			double timeSeconds, int rank, String clickKey, List<BossGroups.PlayerRaidVariant> variants)
		{
			this.heading = heading;
			this.iconKey = iconKey;
			this.primaryName = primaryName;
			this.subtitle = subtitle;
			this.hasData = hasData;
			this.timeSeconds = timeSeconds;
			this.rank = rank;
			this.clickKey = clickKey;
			this.variants = variants;
		}
	}

	/**
	 * @param onBossClick called with (bossKey, displayName) when a row is
	 *                     clicked - callers use this to jump to that boss's
	 *                     leaderboard, highlighted on the clicked player.
	 */
	void showPlayer(SyncClient.PlayerLookupResponse player, BiConsumer<String, String> onBossClick)
	{
		lastPlayer = player;
		lastOnBossClick = onBossClick;
		rowsContainer.removeAll();

		if (player.pbs == null || player.pbs.isEmpty())
		{
			showMessage(player.displayName + " has synced, but has no recorded PBs yet.");
			return;
		}

		List<BossGroups.PlayerPb> pbs = new ArrayList<>();
		for (SyncClient.PbEntryDto pb : player.pbs)
		{
			pbs.add(new BossGroups.PlayerPb(pb.boss, pb.timeSeconds, pb.rank, pb.updatedAt));
		}
		BossGroups.GroupedPlayerPbs grouped = BossGroups.groupPlayerRaidPbs(pbs);

		List<DisplayRow> allRows = buildAllRows(grouped);

		List<DisplayRow> ranked = new ArrayList<>();
		for (DisplayRow row : allRows)
		{
			if (row.hasData)
			{
				ranked.add(row);
			}
		}
		ranked.sort(Comparator.comparingInt(r -> r.rank));

		addHeadline(ranked.isEmpty() ? null : ranked.get(0));

		addSectionHeader("Top Bosses", false);
		for (int i = 0; i < Math.min(5, ranked.size()); i++)
		{
			addDisplayRow(ranked.get(i), player.displayName, onBossClick);
		}

		addSectionHeader("All Bosses", true);
		if (allBossesSectionExpanded)
		{
			List<DisplayRow> sorted = new ArrayList<>(allRows);
			sorted.sort(Comparator.comparing(r -> r.primaryName.toLowerCase()));
			for (DisplayRow row : sorted)
			{
				addDisplayRow(row, player.displayName, onBossClick);
			}
		}

		revalidate();
		repaint();
	}

	private void rerender()
	{
		if (lastPlayer != null)
		{
			showPlayer(lastPlayer, lastOnBossClick);
		}
	}

	/** Every raid heading + flat boss key this plugin knows about, merged with this player's actual PBs (dash if they have none). */
	private List<DisplayRow> buildAllRows(BossGroups.GroupedPlayerPbs grouped)
	{
		java.util.Map<String, BossGroups.PlayerRaidGroup> playerGroupsByHeading = new java.util.LinkedHashMap<>();
		for (BossGroups.PlayerRaidGroup g : grouped.groups)
		{
			playerGroupsByHeading.put(g.heading, g);
		}
		java.util.Map<String, BossGroups.PlayerPb> playerFlatByKey = new java.util.LinkedHashMap<>();
		for (BossGroups.PlayerPb pb : grouped.flat)
		{
			playerFlatByKey.put(pb.boss.trim().toLowerCase(), pb);
		}

		List<DisplayRow> rows = new ArrayList<>();

		List<BossGroups.RaidGroup> templateGroups = BossGroups.groupedRaidGroups(allBosses);
		Set<String> templateHeadings = new HashSet<>();
		for (BossGroups.RaidGroup template : templateGroups)
		{
			templateHeadings.add(template.heading);
			rows.add(buildRaidRow(template.heading, template.variants.get(0).key, playerGroupsByHeading.get(template.heading)));
		}
		// A player might have a PB for a heading the global list hasn't caught up to yet - don't drop it.
		for (BossGroups.PlayerRaidGroup playerGroup : grouped.groups)
		{
			if (!templateHeadings.contains(playerGroup.heading))
			{
				rows.add(buildRaidRow(playerGroup.heading, playerGroup.summary.key, playerGroup));
			}
		}

		Set<String> templateFlatKeys = new HashSet<>();
		for (String key : BossGroups.getFlatBossKeys(allBosses))
		{
			String norm = key.trim().toLowerCase();
			templateFlatKeys.add(norm);
			rows.add(buildFlatRow(key, playerFlatByKey.get(norm)));
		}
		for (BossGroups.PlayerPb pb : grouped.flat)
		{
			if (!templateFlatKeys.contains(pb.boss.trim().toLowerCase()))
			{
				rows.add(buildFlatRow(pb.boss, pb));
			}
		}

		return rows;
	}

	private DisplayRow buildRaidRow(String heading, String templateClickKey, BossGroups.PlayerRaidGroup playerGroup)
	{
		int dashIdx = heading.indexOf(" - ");
		String primaryName = dashIdx >= 0 ? heading.substring(0, dashIdx) : heading;
		String subtitle = dashIdx >= 0 ? heading.substring(dashIdx + 3) : null;

		if (playerGroup == null)
		{
			return new DisplayRow(heading, heading, primaryName, subtitle, false, 0, 0, templateClickKey, null);
		}
		List<BossGroups.PlayerRaidVariant> variants = playerGroup.variants.size() > 1 ? playerGroup.variants : null;
		return new DisplayRow(heading, heading, primaryName, subtitle, true,
			playerGroup.summary.timeSeconds, playerGroup.summary.rank, playerGroup.summary.key, variants);
	}

	private DisplayRow buildFlatRow(String key, BossGroups.PlayerPb pb)
	{
		String primaryName = PbTrackerPlugin.titleCase(key);
		if (pb == null)
		{
			return new DisplayRow(key, key, primaryName, null, false, 0, 0, key, null);
		}
		return new DisplayRow(key, key, primaryName, null, true, pb.timeSeconds, pb.rank, pb.boss, null);
	}

	private void addHeadline(DisplayRow best)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(PbTrackerTheme.BG);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		if (best == null)
		{
			JLabel none = new JLabel("No ranked PBs yet.");
			none.setForeground(PbTrackerTheme.TEXT_DIM);
			panel.add(none);
		}
		else
		{
			JLabel pbLine = new JLabel("Overall PB: " + PbTrackerPlugin.formatTime(best.timeSeconds));
			pbLine.setForeground(PbTrackerTheme.GOLD_LIGHT);
			pbLine.setFont(FontManager.getRunescapeBoldFont());
			JLabel rankLine = new JLabel("Overall Rank: #" + best.rank);
			rankLine.setForeground(PbTrackerTheme.TEXT_DIM);
			panel.add(pbLine);
			panel.add(rankLine);
		}
		rowsContainer.add(panel);
	}

	private void addSectionHeader(String text, boolean collapsible)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(PbTrackerTheme.BG);
		row.setBorder(BorderFactory.createEmptyBorder(10, 8, 6, 8));

		JLabel label = new JLabel(text);
		label.setForeground(PbTrackerTheme.TEXT);
		label.setFont(FontManager.getRunescapeBoldFont());
		row.add(label, BorderLayout.WEST);

		if (collapsible)
		{
			JLabel chevron = new JLabel(allBossesSectionExpanded ? "v" : ">");
			chevron.setForeground(PbTrackerTheme.TEXT_DIM);
			chevron.setFont(FontManager.getRunescapeBoldFont());
			row.add(chevron, BorderLayout.EAST);
			row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			row.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					allBossesSectionExpanded = !allBossesSectionExpanded;
					rerender();
				}
			});
		}

		// Without this, BoxLayout falls back to the row's own narrow
		// preferred width and its default 0.5 alignmentX, which visually
		// reads as a short bar shoved off-center instead of a full-width
		// header - see the data rows' identical treatment below for why.
		row.setAlignmentX(0);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		rowsContainer.add(row);
	}

	/**
	 * The whole row is the click target - if it has a team-size breakdown,
	 * clicking toggles that breakdown open beneath it in place (matching
	 * "collapsable to see the other PBs under the boss"); otherwise it jumps
	 * straight to that boss's leaderboard, same as before.
	 */
	private void addDisplayRow(DisplayRow row, String displayName, BiConsumer<String, String> onBossClick)
	{
		boolean expandable = row.variants != null;
		boolean expanded = expandable && expandedHeadings.contains(row.heading);

		JPanel outer = new JPanel(new BorderLayout(6, 0));
		outer.setBackground(PbTrackerTheme.PANEL);
		outer.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)
		));
		outer.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(20, 20));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		BossIcons.get(spriteManager, row.iconKey, icon::setIcon);
		outer.add(icon, BorderLayout.WEST);

		JPanel textBlock = new JPanel();
		textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
		textBlock.setBackground(PbTrackerTheme.PANEL);
		JTextArea name = wrappedLabel(PbTrackerPlugin.wrapFriendly(row.primaryName), PbTrackerTheme.TEXT, true);
		textBlock.add(name);
		if (row.subtitle != null)
		{
			JTextArea subtitle = wrappedLabel(PbTrackerPlugin.wrapFriendly(row.subtitle), PbTrackerTheme.TEXT_DIM, false);
			textBlock.add(subtitle);
		}
		outer.add(textBlock, BorderLayout.CENTER);

		JPanel statsBlock = new JPanel();
		statsBlock.setLayout(new BoxLayout(statsBlock, BoxLayout.Y_AXIS));
		statsBlock.setBackground(PbTrackerTheme.PANEL);
		if (row.hasData)
		{
			JLabel time = new JLabel(PbTrackerPlugin.formatTime(row.timeSeconds));
			time.setForeground(PbTrackerTheme.GOLD_LIGHT);
			time.setFont(FontManager.getRunescapeBoldFont());
			time.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);
			JLabel rank = new JLabel("#" + row.rank);
			rank.setForeground(PbTrackerTheme.TEXT_DIM);
			rank.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);
			statsBlock.add(time);
			statsBlock.add(rank);
		}
		else
		{
			JLabel dash = new JLabel("–");
			dash.setForeground(PbTrackerTheme.TEXT_DIM);
			statsBlock.add(dash);
		}
		if (expandable)
		{
			JPanel eastWrapper = new JPanel(new BorderLayout(6, 0));
			eastWrapper.setBackground(PbTrackerTheme.PANEL);
			eastWrapper.add(statsBlock, BorderLayout.WEST);
			// A visible arrow, not just an unmarked clickable row - a hidden
			// affordance here was the exact "where are my other team sizes?"
			// problem from before; this time the row is obviously expandable.
			JLabel chevron = new JLabel(expanded ? "v" : ">");
			chevron.setForeground(PbTrackerTheme.GOLD_LIGHT);
			chevron.setFont(FontManager.getRunescapeBoldFont());
			eastWrapper.add(chevron, BorderLayout.EAST);
			outer.add(eastWrapper, BorderLayout.EAST);
		}
		else
		{
			outer.add(statsBlock, BorderLayout.EAST);
		}

		outer.setAlignmentX(0);
		outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		outer.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				if (expandable)
				{
					if (expandedHeadings.contains(row.heading))
					{
						expandedHeadings.remove(row.heading);
					}
					else
					{
						expandedHeadings.add(row.heading);
					}
					rerender();
				}
				else
				{
					onBossClick.accept(row.clickKey, displayName);
				}
			}
		});
		rowsContainer.add(outer);

		if (expanded)
		{
			for (BossGroups.PlayerRaidVariant variant : row.variants)
			{
				addVariantSubRow(variant, displayName, onBossClick);
			}
		}
	}

	/** An indented, smaller row for one team-size/mode variant beneath its expanded raid+mode heading. */
	private void addVariantSubRow(BossGroups.PlayerRaidVariant variant, String displayName, BiConsumer<String, String> onBossClick)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(PbTrackerTheme.ROW_BG);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(6, 20, 6, 8)
		));
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		JTextArea line = wrappedLabel(variant.label + "   " + PbTrackerPlugin.formatTime(variant.timeSeconds) + "   #" + variant.rank, PbTrackerTheme.TEXT_DIM, false);
		row.add(line, BorderLayout.CENTER);
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				onBossClick.accept(variant.key, displayName);
			}
		});
		rowsContainer.add(row);
	}

	/**
	 * A plain JLabel never wraps and just keeps growing wider, which is what
	 * caused the sidebar's horizontal scrollbar on long headings. A
	 * non-editable, unstyled JTextArea wraps at whatever width its
	 * container actually gives it, like the rest of the panel.
	 */
	private JTextArea wrappedLabel(String text, java.awt.Color color, boolean bold)
	{
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setFocusable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setOpaque(false);
		area.setForeground(color);
		area.setFont(bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		area.setBorder(null);
		area.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		return area;
	}
}
