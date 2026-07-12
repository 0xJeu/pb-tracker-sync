package com.pbtracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Renders a player's synced PBs as a vertical list of rows, grouped by raid
 * (collapsed to one summary row with a drill-down, matching the website's
 * boss picker) with everything else shown flat - reused by both the "My
 * PBs" and "Player Search" tabs, since they display the same kind of data.
 */
class PbListPanel extends JPanel
{
	// Matches the website's --gold / --gold-light (frontend/src/theme.css),
	// not RuneLite's own ColorScheme.BRAND_ORANGE, so the panel reads as part
	// of the same product as the site rather than a generic RuneLite plugin.
	static final Color GOLD = new Color(0xFF, 0x98, 0x1F);
	static final Color GOLD_LIGHT = new Color(0xFF, 0xB8, 0x4D);

	private final JPanel rowsContainer = new JPanel();

	PbListPanel()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		rowsContainer.setLayout(new BoxLayout(rowsContainer, BoxLayout.Y_AXIS));
		rowsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(rowsContainer, BorderLayout.NORTH);
	}

	void showMessage(String text)
	{
		rowsContainer.removeAll();
		JLabel label = new JLabel("<html><center>" + text + "</center></html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(BorderFactory.createEmptyBorder(16, 8, 16, 8));
		rowsContainer.add(label);
		revalidate();
		repaint();
	}

	/**
	 * @param onBossClick called with (bossKey, displayName) when a row is
	 *                     clicked - callers use this to jump to that boss's
	 *                     leaderboard, highlighted on the clicked player.
	 */
	void showPlayer(SyncClient.PlayerLookupResponse player, BiConsumer<String, String> onBossClick)
	{
		rowsContainer.removeAll();

		if (player.pbs == null || player.pbs.isEmpty())
		{
			showMessage(player.displayName + " has synced, but has no recorded PBs yet.");
			return;
		}

		List<BossGroups.PlayerPb> pbs = new java.util.ArrayList<>();
		for (SyncClient.PbEntryDto pb : player.pbs)
		{
			pbs.add(new BossGroups.PlayerPb(pb.boss, pb.timeSeconds, pb.rank, pb.updatedAt));
		}

		BossGroups.GroupedPlayerPbs grouped = BossGroups.groupPlayerRaidPbs(pbs);

		addHeaderRow(player.displayName + " - " + player.pbs.size() + " PB(s)");

		for (BossGroups.PlayerRaidGroup group : grouped.groups)
		{
			addRaidGroupRow(group, player.displayName, onBossClick);
		}

		List<BossGroups.PlayerPb> flatSorted = new java.util.ArrayList<>(grouped.flat);
		flatSorted.sort(java.util.Comparator.comparing(pb -> PbTrackerPlugin.titleCase(pb.boss)));
		for (BossGroups.PlayerPb pb : flatSorted)
		{
			addFlatRow(pb, player.displayName, onBossClick);
		}

		revalidate();
		repaint();
	}

	private void addHeaderRow(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(GOLD);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		rowsContainer.add(label);
	}

	private void addRaidGroupRow(BossGroups.PlayerRaidGroup group, String displayName, BiConsumer<String, String> onBossClick)
	{
		JPanel row = buildRowContainer();

		JLabel heading = new JLabel(group.heading);
		heading.setForeground(Color.WHITE);
		heading.setFont(FontManager.getRunescapeBoldFont());

		JLabel time = new JLabel(PbTrackerPlugin.formatTime(group.summary.timeSeconds) + "  (" + group.summary.label + ")");
		time.setForeground(GOLD_LIGHT);

		JLabel rank = new JLabel("Rank #" + group.summary.rank);
		rank.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		row.add(heading);
		row.add(time);
		row.add(rank);
		makeClickable(row, () -> onBossClick.accept(group.summary.key, displayName));
		rowsContainer.add(row);
	}

	private void addFlatRow(BossGroups.PlayerPb pb, String displayName, BiConsumer<String, String> onBossClick)
	{
		JPanel row = buildRowContainer();

		JLabel heading = new JLabel(PbTrackerPlugin.titleCase(pb.boss));
		heading.setForeground(Color.WHITE);
		heading.setFont(FontManager.getRunescapeBoldFont());

		JLabel time = new JLabel(PbTrackerPlugin.formatTime(pb.timeSeconds));
		time.setForeground(GOLD_LIGHT);

		JLabel rank = new JLabel("Rank #" + pb.rank);
		rank.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		row.add(heading);
		row.add(time);
		row.add(rank);
		makeClickable(row, () -> onBossClick.accept(pb.boss, displayName));
		rowsContainer.add(row);
	}

	private JPanel buildRowContainer()
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)
		));
		row.setAlignmentX(0);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getMaximumSize().height));
		return row;
	}

	private void makeClickable(JPanel row, Runnable onClick)
	{
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				onClick.run();
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});
	}
}
