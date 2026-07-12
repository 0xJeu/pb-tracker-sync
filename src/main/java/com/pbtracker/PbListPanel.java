package com.pbtracker;

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
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Renders a player's synced PBs as a vertical list of rows, grouped by raid
 * (heading + every recorded size/mode variant shown beneath it, matching the
 * website's boss picker) with everything else shown flat - reused by both
 * the "My PBs" and "Player Search" tabs, since they display the same kind of
 * data.
 * <p>
 * Implements Scrollable so the enclosing JScrollPane's JViewport clamps this
 * panel's width to the actual visible width instead of using its raw (and,
 * because of the wrapped JTextArea headings/detail lines, misleadingly wide)
 * preferred size - without this, a plain JPanel isn't Scrollable-aware, so
 * the viewport just takes the view's inflated preferred width verbatim,
 * which is what let long headings force the whole panel wider instead of
 * wrapping.
 */
class PbListPanel extends JPanel implements Scrollable
{
	private final JPanel rowsContainer = new JPanel();

	PbListPanel()
	{
		setLayout(new BorderLayout());
		setBackground(PbTrackerTheme.BG);

		rowsContainer.setLayout(new BoxLayout(rowsContainer, BoxLayout.Y_AXIS));
		rowsContainer.setBackground(PbTrackerTheme.BG);
		add(rowsContainer, BorderLayout.NORTH);
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

		addHeaderRow(player.displayName + " - " + rowCount + " bosses, " + player.pbs.size() + " PBs");

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
		// A JLabel never wraps and gets clipped at the panel edge once the
		// scrollbar is disabled - wrap it like every other line of text here.
		JTextArea label = new JTextArea(text);
		label.setEditable(false);
		label.setFocusable(false);
		label.setLineWrap(true);
		label.setWrapStyleWord(true);
		label.setOpaque(false);
		label.setForeground(PbTrackerTheme.GOLD);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		rowsContainer.add(label);
	}

	/**
	 * Every recorded size/mode variant is always shown as its own row under
	 * the heading - a hidden expand/collapse chevron here was too easy to
	 * miss (players kept reporting "where are my other team sizes?" even
	 * though the data was there), so there's no toggle state to discover.
	 */
	private void addRaidGroupRow(BossGroups.PlayerRaidGroup group, String displayName, BiConsumer<String, String> onBossClick)
	{
		RowParts row = buildRowContainer();
		String timeText = PbTrackerPlugin.formatTime(group.summary.timeSeconds) + "  (" + group.summary.label + ")";
		row.content.add(buildHeadingLabel(group.heading));
		row.content.add(Box.createVerticalStrut(4));
		row.content.add(buildDetailLine(timeText, group.summary.rank));
		makeClickable(row.content, () -> onBossClick.accept(group.summary.key, displayName));
		rowsContainer.add(row.outer);

		for (BossGroups.PlayerRaidVariant variant : group.variants)
		{
			addVariantSubRow(variant, displayName, onBossClick);
		}
	}

	/** An indented, smaller row for one team-size/mode variant beneath its raid+mode heading. */
	private void addVariantSubRow(BossGroups.PlayerRaidVariant variant, String displayName, BiConsumer<String, String> onBossClick)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(PbTrackerTheme.ROW_BG);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(6, 24, 6, 10)
		));
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		// One wrapped line instead of a WEST/EAST label pair - see
		// buildDetailLine()'s comment for why side-by-side labels clip once
		// the horizontal scrollbar is disabled.
		JTextArea line = new JTextArea(variant.label + "   " + PbTrackerPlugin.formatTime(variant.timeSeconds) + "   #" + variant.rank);
		line.setEditable(false);
		line.setFocusable(false);
		line.setLineWrap(true);
		line.setWrapStyleWord(true);
		line.setOpaque(false);
		line.setForeground(PbTrackerTheme.TEXT_DIM);

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

	private void addFlatRow(BossGroups.PlayerPb pb, String displayName, BiConsumer<String, String> onBossClick)
	{
		RowParts row = buildRowContainer();
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

	/**
	 * Time + rank as one wrapped line of text, rather than a BorderLayout
	 * WEST/EAST pair - side-by-side labels each claim their full preferred
	 * width, and once the panel's horizontal scrollbar is disabled, anything
	 * past the visible edge is silently clipped instead of scrollable.
	 * Wrapping avoids that entirely.
	 */
	private JTextArea buildDetailLine(String timeText, int rank)
	{
		JTextArea line = new JTextArea(timeText + "   #" + rank);
		line.setEditable(false);
		line.setFocusable(false);
		line.setLineWrap(true);
		line.setWrapStyleWord(true);
		line.setOpaque(false);
		line.setForeground(PbTrackerTheme.GOLD_LIGHT);
		line.setBorder(null);
		line.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		return line;
	}

	/** outer = the whole row (border, background); content = where headings/detail lines go and where the "go to leaderboard" click listener attaches. */
	private static final class RowParts
	{
		final JPanel outer;
		final JPanel content;

		RowParts(JPanel outer, JPanel content)
		{
			this.outer = outer;
			this.content = content;
		}
	}

	private RowParts buildRowContainer()
	{
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(PbTrackerTheme.PANEL);
		content.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		JPanel outer = new JPanel(new BorderLayout());
		outer.setBackground(PbTrackerTheme.PANEL);
		outer.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)
		));
		outer.add(content, BorderLayout.CENTER);

		outer.setAlignmentX(0);
		// Only constrain width (let the row stretch to fill rowsContainer),
		// not height - this used to read row.getMaximumSize().height before
		// any labels had been added, which for an empty BoxLayout panel is
		// ~0, silently clamping every row to zero height and causing all
		// three labels (heading/time/rank) to render stacked on top of each
		// other instead of stacked vertically.
		outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return new RowParts(outer, content);
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
