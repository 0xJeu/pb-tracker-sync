package com.pbtracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Bosses" tab - pick a boss (raids collapse into one entry with a
 * mode/team-size drill-down, matching the website's boss picker), then see
 * its leaderboard.
 */
class BossesTab extends JPanel
{
	private final SyncClient syncClient;
	private final Consumer<String> onPlayerClick;

	private final DefaultListModel<PickerEntry> listModel = new DefaultListModel<>();
	private final JList<PickerEntry> pickerList = new JList<>(listModel);
	private final JPanel drillDownPanel = new JPanel();
	private final JPanel leaderboardRows = new JPanel();
	private final JLabel leaderboardTitle = new JLabel(" ");

	private List<String> allBosses = List.of();
	private String pendingHighlight;

	private static final class PickerEntry
	{
		final String label;
		final boolean isRaidBase;
		final String key; // exact boss key if flat, or raid base string if isRaidBase

		PickerEntry(String label, boolean isRaidBase, String key)
		{
			this.label = label;
			this.isRaidBase = isRaidBase;
			this.key = key;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	BossesTab(SyncClient syncClient, Consumer<String> onPlayerClick)
	{
		this.syncClient = syncClient;
		this.onPlayerClick = onPlayerClick;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JTextField filterField = new JTextField();
		filterField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		filterField.setForeground(Color.WHITE);
		filterField.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		filterField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				applyFilter(filterField.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				applyFilter(filterField.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				applyFilter(filterField.getText());
			}
		});

		pickerList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		pickerList.setForeground(Color.WHITE);
		pickerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		pickerList.setVisibleRowCount(6);
		pickerList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting() && pickerList.getSelectedValue() != null)
			{
				onPickerSelection(pickerList.getSelectedValue());
			}
		});
		JScrollPane pickerScroll = new JScrollPane(pickerList);
		pickerScroll.setPreferredSize(new Dimension(0, 140));

		top.add(filterField, BorderLayout.NORTH);
		top.add(pickerScroll, BorderLayout.CENTER);

		drillDownPanel.setLayout(new BoxLayout(drillDownPanel, BoxLayout.Y_AXIS));
		drillDownPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(drillDownPanel, BorderLayout.SOUTH);

		add(top, BorderLayout.NORTH);

		leaderboardTitle.setForeground(PbListPanel.GOLD);
		leaderboardTitle.setFont(FontManager.getRunescapeBoldFont());
		leaderboardTitle.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

		leaderboardRows.setLayout(new BoxLayout(leaderboardRows, BoxLayout.Y_AXIS));
		leaderboardRows.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel leaderboardContainer = new JPanel(new BorderLayout());
		leaderboardContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		leaderboardContainer.add(leaderboardTitle, BorderLayout.NORTH);
		leaderboardContainer.add(leaderboardRows, BorderLayout.CENTER);

		JScrollPane leaderboardScroll = new JScrollPane(leaderboardContainer);
		leaderboardScroll.setBorder(null);
		leaderboardScroll.getVerticalScrollBar().setUnitIncrement(16);
		add(leaderboardScroll, BorderLayout.CENTER);
	}

	/** Called once bosses are fetched (on panel open) and whenever the picker needs repopulating. */
	void setBosses(List<String> bosses)
	{
		this.allBosses = bosses;
		applyFilter("");
	}

	private void applyFilter(String filterText)
	{
		listModel.clear();
		String filter = filterText.trim().toLowerCase();

		List<PickerEntry> entries = new ArrayList<>();
		for (BossGroups.RaidBase base : BossGroups.getRaidBases(allBosses))
		{
			entries.add(new PickerEntry(base.label, true, base.base));
		}
		List<String> flatKeys = new ArrayList<>();
		for (String boss : allBosses)
		{
			if (!BossGroups.isGroupedVariant(boss))
			{
				flatKeys.add(boss);
			}
		}
		for (String key : flatKeys)
		{
			entries.add(new PickerEntry(PbTrackerPlugin.titleCase(key), false, key));
		}
		entries.sort(Comparator.comparing(en -> en.label));

		for (PickerEntry entry : entries)
		{
			if (filter.isEmpty() || entry.label.toLowerCase().contains(filter))
			{
				listModel.addElement(entry);
			}
		}
	}

	private void onPickerSelection(PickerEntry entry)
	{
		drillDownPanel.removeAll();

		if (!entry.isRaidBase)
		{
			loadLeaderboard(entry.key, entry.label);
			drillDownPanel.revalidate();
			drillDownPanel.repaint();
			return;
		}

		List<BossGroups.RaidMode> modes = BossGroups.getRaidModes(allBosses, entry.key);
		if (modes.isEmpty())
		{
			return;
		}

		JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		modeRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		sizeRow.setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (BossGroups.RaidMode mode : modes)
		{
			JLabel modeLabel = clickableLabel(mode.modeLabel, () -> selectMode(mode, entry.label, sizeRow));
			modeRow.add(modeLabel);
		}

		drillDownPanel.add(modeRow);
		drillDownPanel.add(sizeRow);
		drillDownPanel.revalidate();
		drillDownPanel.repaint();

		// Auto-select the first mode so a leaderboard shows immediately.
		selectMode(modes.get(0), entry.label, sizeRow);
	}

	private void selectMode(BossGroups.RaidMode mode, String raidLabel, JPanel sizeRow)
	{
		sizeRow.removeAll();
		for (BossGroups.KeyLabel variant : mode.variants)
		{
			JLabel sizeLabel = clickableLabel(variant.label,
				() -> loadLeaderboard(variant.key, raidLabel + " - " + mode.modeLabel + " - " + variant.label));
			sizeRow.add(sizeLabel);
		}
		sizeRow.revalidate();
		sizeRow.repaint();

		if (!mode.variants.isEmpty())
		{
			BossGroups.KeyLabel first = mode.variants.get(0);
			loadLeaderboard(first.key, raidLabel + " - " + mode.modeLabel + " - " + first.label);
		}
	}

	private JLabel clickableLabel(String text, Runnable onClick)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		label.setOpaque(true);
		label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}
		});
		return label;
	}

	private void loadLeaderboard(String bossKey, String title)
	{
		leaderboardTitle.setText(title);
		leaderboardRows.removeAll();
		JLabel loading = new JLabel("Loading...");
		loading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		leaderboardRows.add(loading);
		leaderboardRows.revalidate();
		leaderboardRows.repaint();

		String highlight = pendingHighlight;
		pendingHighlight = null;

		new Thread(() ->
		{
			List<SyncClient.LeaderboardRow> rows = syncClient.getLeaderboard(bossKey, highlight != null ? 500 : 25);
			SwingUtilities.invokeLater(() -> renderLeaderboard(rows, highlight));
		}, "pbtracker-leaderboard-lookup").start();
	}

	private void renderLeaderboard(List<SyncClient.LeaderboardRow> rows, String highlight)
	{
		leaderboardRows.removeAll();
		if (rows == null)
		{
			JLabel error = new JLabel("Lookup failed - try again later.");
			error.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			leaderboardRows.add(error);
		}
		else if (rows.isEmpty())
		{
			JLabel empty = new JLabel("No synced PBs for this boss yet.");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			leaderboardRows.add(empty);
		}
		else
		{
			for (int i = 0; i < rows.size(); i++)
			{
				SyncClient.LeaderboardRow row = rows.get(i);
				boolean isHighlighted = highlight != null && row.displayName.equalsIgnoreCase(highlight);
				leaderboardRows.add(buildLeaderboardRow(i + 1, row, isHighlighted));
			}
		}
		leaderboardRows.revalidate();
		leaderboardRows.repaint();
	}

	private JPanel buildLeaderboardRow(int rank, SyncClient.LeaderboardRow row, boolean isHighlighted)
	{
		JPanel panel = new JPanel(new BorderLayout(8, 0));
		panel.setBackground(isHighlighted ? new Color(0x3A, 0x2A, 0x10) : ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 10, 6, 10)
		));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getMaximumSize().height));
		panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel rankAndName = new JLabel("#" + rank + "  " + row.displayName);
		rankAndName.setForeground(isHighlighted ? PbListPanel.GOLD : Color.WHITE);

		JLabel time = new JLabel(PbTrackerPlugin.formatTime(row.timeSeconds));
		time.setForeground(PbListPanel.GOLD_LIGHT);

		panel.add(rankAndName, BorderLayout.WEST);
		panel.add(time, BorderLayout.EAST);

		panel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onPlayerClick.accept(row.displayName);
			}
		});
		return panel;
	}

	/** Called from the "jump to leaderboard, scrolled to this player" flow (rank click on a PB row). */
	void showBossHighlighted(String bossKey, String highlightPlayerName)
	{
		pendingHighlight = highlightPlayerName;
		loadLeaderboard(bossKey, PbTrackerPlugin.titleCase(bossKey));
	}
}
