package com.pbtracker;

import net.runelite.client.ui.FontManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Renders a player's synced PBs as a vertical list of rows, grouped by raid
 * (collapsed to one summary row, expandable to show every team-size/mode
 * variant - matching the website's boss picker) with everything else shown
 * flat - reused by both the "My PBs" and "Player Search" tabs, since they
 * display the same kind of data.
 */
class PbListPanel extends JPanel
{
	private final JPanel rowsContainer = new JPanel();
	private final Set<String> expandedHeadings = new HashSet<>();

	private SyncClient.PlayerLookupResponse lastPlayer;
	private BiConsumer<String, String> lastOnBossClick;

	PbListPanel()
	{
		setLayout(new BorderLayout());
		setBackground(PbTrackerTheme.BG);

		rowsContainer.setLayout(new BoxLayout(rowsContainer, BoxLayout.Y_AXIS));
		rowsContainer.setBackground(PbTrackerTheme.BG);
		add(rowsContainer, BorderLayout.NORTH);
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

		List<BossGroups.PlayerPb> pbs = new java.util.ArrayList<>();
		for (SyncClient.PbEntryDto pb : player.pbs)
		{
			pbs.add(new BossGroups.PlayerPb(pb.boss, pb.timeSeconds, pb.rank, pb.updatedAt));
		}

		BossGroups.GroupedPlayerPbs grouped = BossGroups.groupPlayerRaidPbs(pbs);
		int rowCount = grouped.groups.size() + grouped.flat.size();

		addHeaderRow(player.displayName + " - " + rowCount + " boss(es), " + player.pbs.size() + " time(s) tracked");

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

	private void rerender()
	{
		if (lastPlayer != null)
		{
			showPlayer(lastPlayer, lastOnBossClick);
		}
	}

	private void addHeaderRow(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(PbTrackerTheme.GOLD);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		rowsContainer.add(label);
	}

	private void addRaidGroupRow(BossGroups.PlayerRaidGroup group, String displayName, BiConsumer<String, String> onBossClick)
	{
		boolean hasMultipleVariants = group.variants.size() > 1;
		boolean expanded = hasMultipleVariants && expandedHeadings.contains(group.heading);

		RowParts row = buildRowContainer(hasMultipleVariants, expanded);
		String timeText = PbTrackerPlugin.formatTime(group.summary.timeSeconds) + "  (" + group.summary.label + ")";
		row.content.add(buildHeadingLabel(group.heading));
		row.content.add(Box.createVerticalStrut(4));
		row.content.add(buildDetailLine(timeText, group.summary.rank));
		makeClickable(row.content, () -> onBossClick.accept(group.summary.key, displayName));
		if (row.chevron != null)
		{
			row.chevron.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					if (expandedHeadings.contains(group.heading))
					{
						expandedHeadings.remove(group.heading);
					}
					else
					{
						expandedHeadings.add(group.heading);
					}
					rerender();
				}
			});
		}
		rowsContainer.add(row.outer);

		if (expanded)
		{
			for (BossGroups.PlayerRaidVariant variant : group.variants)
			{
				addVariantSubRow(variant, displayName, onBossClick);
			}
		}
	}

	/** An indented, smaller row for one team-size/mode variant within an expanded raid group. */
	private void addVariantSubRow(BossGroups.PlayerRaidVariant variant, String displayName, BiConsumer<String, String> onBossClick)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(PbTrackerTheme.ROW_BG);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(6, 24, 6, 10)
		));
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		JLabel label = new JLabel(variant.label);
		label.setForeground(PbTrackerTheme.TEXT_DIM);

		JLabel time = new JLabel(PbTrackerPlugin.formatTime(variant.timeSeconds) + "   #" + variant.rank);
		time.setForeground(PbTrackerTheme.GOLD_LIGHT);

		row.add(label, BorderLayout.WEST);
		row.add(time, BorderLayout.EAST);
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

	private void addFlatRow(BossGroups.PlayerPb pb, String displayName, BiConsumer<String, String> onBossClick)
	{
		RowParts row = buildRowContainer(false, false);
		row.content.add(buildHeadingLabel(PbTrackerPlugin.titleCase(pb.boss)));
		row.content.add(Box.createVerticalStrut(4));
		row.content.add(buildDetailLine(PbTrackerPlugin.formatTime(pb.timeSeconds), pb.rank));
		makeClickable(row.content, () -> onBossClick.accept(pb.boss, displayName));
		rowsContainer.add(row.outer);
	}

	/**
	 * A plain JLabel never wraps and just keeps growing wider, which is what
	 * caused the sidebar's horizontal scrollbar on long headings like
	 * "Tombs Of Amascut - Expert - Fastest Overall (4 Player)". A
	 * non-editable, unstyled JTextArea wraps at whatever width its
	 * container actually gives it, like the rest of the panel.
	 */
	private JTextArea buildHeadingLabel(String text)
	{
		JTextArea heading = new JTextArea(text);
		heading.setEditable(false);
		heading.setFocusable(false);
		heading.setLineWrap(true);
		heading.setWrapStyleWord(true);
		heading.setOpaque(false);
		heading.setForeground(PbTrackerTheme.TEXT);
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setBorder(null);
		heading.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		return heading;
	}

	/** Time (gold) on the left, rank (dim) on the right - one compact line instead of two. */
	private JPanel buildDetailLine(String timeText, int rank)
	{
		JPanel line = new JPanel(new BorderLayout());
		line.setBackground(PbTrackerTheme.PANEL);
		line.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

		JLabel time = new JLabel(timeText);
		time.setForeground(PbTrackerTheme.GOLD_LIGHT);

		JLabel rankLabel = new JLabel("Rank #" + rank);
		rankLabel.setForeground(PbTrackerTheme.TEXT_DIM);

		line.add(time, BorderLayout.WEST);
		line.add(rankLabel, BorderLayout.EAST);
		// Measured after both labels are added, unlike the bug this whole
		// row layout is fixing - see buildRowContainer()'s comment.
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		return line;
	}

	/**
	 * outer = the whole row (border, background); content = where
	 * headings/detail lines go and where the "go to leaderboard" click
	 * listener attaches; chevron = the separate expand/collapse toggle
	 * (null when there's nothing to expand), so clicking the row body and
	 * clicking the chevron do two different things.
	 */
	private static final class RowParts
	{
		final JPanel outer;
		final JPanel content;
		final JLabel chevron;

		RowParts(JPanel outer, JPanel content, JLabel chevron)
		{
			this.outer = outer;
			this.content = content;
			this.chevron = chevron;
		}
	}

	private RowParts buildRowContainer(boolean expandable, boolean expanded)
	{
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(PbTrackerTheme.PANEL);
		content.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		JLabel chevron = null;
		JPanel outer = new JPanel(new BorderLayout());
		outer.setBackground(PbTrackerTheme.PANEL);
		outer.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)
		));
		outer.add(content, BorderLayout.CENTER);

		if (expandable)
		{
			// Plain ASCII rather than a Unicode arrow - RuneLite's bitmap
			// OSRS font doesn't have a glyph for "›" and silently falls
			// back to a "tofu" box character instead.
			chevron = new JLabel(expanded ? "v" : ">");
			chevron.setForeground(PbTrackerTheme.TEXT_DIM);
			chevron.setFont(FontManager.getRunescapeBoldFont());
			chevron.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
			chevron.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			outer.add(chevron, BorderLayout.EAST);
		}

		outer.setAlignmentX(0);
		// Only constrain width (let the row stretch to fill rowsContainer),
		// not height - this used to read row.getMaximumSize().height before
		// any labels had been added, which for an empty BoxLayout panel is
		// ~0, silently clamping every row to zero height and causing all
		// three labels (heading/time/rank) to render stacked on top of each
		// other instead of stacked vertically.
		outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return new RowParts(outer, content, chevron);
	}

	private void makeClickable(JPanel row, Runnable onClick)
	{
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				onClick.run();
			}
		});
	}
}
