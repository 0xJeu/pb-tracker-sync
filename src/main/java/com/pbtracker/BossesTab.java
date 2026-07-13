package com.pbtracker;

import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
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
 * <p>
 * The picker (search box + list) collapses into a single "change boss" bar
 * once something is selected, matching the website's own collapsed-combobox
 * pattern - keeps the sidebar's limited vertical space for the leaderboard
 * instead of a permanently-open list.
 */
class BossesTab extends JPanel
{
	private static final String PLACEHOLDER = "Search bosses...";

	private final SyncClient syncClient;
	private final SpriteManager spriteManager;
	private final Consumer<String> onPlayerClick;

	private final DefaultListModel<PickerEntry> listModel = new DefaultListModel<>();
	private final JList<PickerEntry> pickerList = new JList<>(listModel);
	private final JTextField filterField = new JTextField(PLACEHOLDER);
	private final JScrollPane pickerScroll;
	private final JPanel pickerSection = new JPanel(new BorderLayout());

	private final JPanel selectedBossBar = new JPanel(new BorderLayout(8, 0));
	private final JLabel selectedBossNameLabel = new JLabel();
	private final JPanel drillDownPanel = new JPanel();
	private final JPanel leaderboardRows = new JPanel();
	private final javax.swing.JTextArea leaderboardTitle = new javax.swing.JTextArea(" ");

	private List<String> allBosses = List.of();
	private String pendingHighlight;
	private String selectedBossIconKey;

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
	}

	BossesTab(SyncClient syncClient, SpriteManager spriteManager, Consumer<String> onPlayerClick)
	{
		this.syncClient = syncClient;
		this.spriteManager = spriteManager;
		this.onPlayerClick = onPlayerClick;
		setLayout(new BorderLayout());
		setBackground(PbTrackerTheme.BG);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(PbTrackerTheme.BG);

		setUpFilterField();
		setUpPickerList();
		pickerScroll = new JScrollPane(pickerList);
		pickerScroll.setBorder(BorderFactory.createEmptyBorder());
		pickerScroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		pickerScroll.setPreferredSize(new Dimension(0, 160));

		pickerSection.setBackground(PbTrackerTheme.BG);
		pickerSection.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
		pickerSection.add(filterField, BorderLayout.NORTH);
		pickerSection.add(pickerScroll, BorderLayout.CENTER);

		setUpSelectedBossBar();

		top.add(pickerSection, BorderLayout.NORTH);
		top.add(selectedBossBar, BorderLayout.CENTER);

		drillDownPanel.setLayout(new BoxLayout(drillDownPanel, BoxLayout.Y_AXIS));
		drillDownPanel.setBackground(PbTrackerTheme.BG);
		drillDownPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
		top.add(drillDownPanel, BorderLayout.SOUTH);

		add(top, BorderLayout.NORTH);
		add(buildLeaderboardScroll(), BorderLayout.CENTER);

		showPicker();
	}

	private void setUpFilterField()
	{
		filterField.setBackground(PbTrackerTheme.PANEL);
		filterField.setForeground(PbTrackerTheme.TEXT_DIM);
		filterField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)
		));
		// Vanilla Swing has no built-in placeholder text support - fake it
		// by swapping the text/color in and out on focus, same idea as a
		// browser input's placeholder attribute.
		filterField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				if (filterField.getText().equals(PLACEHOLDER))
				{
					filterField.setText("");
					filterField.setForeground(PbTrackerTheme.TEXT);
				}
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				if (filterField.getText().isEmpty())
				{
					filterField.setText(PLACEHOLDER);
					filterField.setForeground(PbTrackerTheme.TEXT_DIM);
				}
			}
		});
		filterField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				applyFilter(currentFilterText());
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				applyFilter(currentFilterText());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				applyFilter(currentFilterText());
			}
		});
	}

	private String currentFilterText()
	{
		String text = filterField.getText();
		return text.equals(PLACEHOLDER) ? "" : text;
	}

	private void setUpPickerList()
	{
		pickerList.setBackground(PbTrackerTheme.PANEL);
		pickerList.setForeground(PbTrackerTheme.TEXT);
		pickerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		pickerList.setSelectionBackground(PbTrackerTheme.HIGHLIGHT_BG);
		pickerList.setSelectionForeground(PbTrackerTheme.GOLD_LIGHT);
		pickerList.setFixedCellHeight(36);
		pickerList.setCellRenderer(new PickerEntryRenderer());
		pickerList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting() && pickerList.getSelectedValue() != null)
			{
				onPickerSelection(pickerList.getSelectedValue());
			}
		});
	}

	/**
	 * Bold + a "raid" marker for raid bases, plain for everything else,
	 * proper padding either way. The icon label is a shared, transient
	 * component JList reuses across every row it paints - a sprite that
	 * finishes loading asynchronously can't safely be applied to it
	 * directly (it may already be rendering a different row), so an
	 * uncached icon triggers a fetch that just repaints the list once
	 * ready, and the next render pass picks it up from the cache.
	 */
	private final class PickerEntryRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			PickerEntry entry = (PickerEntry) value;
			label.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
			javax.swing.ImageIcon icon = BossIcons.getCached(entry.key);
			if (icon != null)
			{
				label.setIcon(icon);
			}
			else
			{
				label.setIcon(null);
				BossIcons.get(spriteManager, entry.key, loaded -> list.repaint());
			}
			label.setIconTextGap(8);
			if (entry.isRaidBase)
			{
				// Plain ASCII rather than a Unicode arrow - see the chevron
				// comment in PbListPanel for why RuneLite's bitmap font can't
				// render "▸".
				label.setText(entry.label + "  >");
				label.setFont(FontManager.getRunescapeBoldFont());
			}
			else
			{
				label.setText(entry.label);
				label.setFont(FontManager.getRunescapeFont());
			}
			return label;
		}
	}

	private void setUpSelectedBossBar()
	{
		selectedBossBar.setBackground(PbTrackerTheme.BG);
		selectedBossBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		selectedBossNameLabel.setForeground(PbTrackerTheme.GOLD_LIGHT);
		java.awt.Font nameFont = FontManager.getRunescapeBoldFont();
		selectedBossNameLabel.setFont(nameFont.deriveFont(nameFont.getSize2D() + 2f));
		selectedBossBar.add(selectedBossNameLabel, BorderLayout.CENTER);

		// A real bordered button, not just "(change)" tacked onto the boss
		// name as plain text - same clickableLabel style as the mode/size
		// buttons below it, so it actually reads as something to click.
		JLabel changeButton = clickableLabel("Change", this::showPicker);
		selectedBossBar.add(changeButton, BorderLayout.EAST);
	}

	private void showPicker()
	{
		pickerSection.setVisible(true);
		selectedBossBar.setVisible(false);
		revalidate();
		repaint();
	}

	private void showSelectedBossBar(String label, String iconKey)
	{
		selectedBossNameLabel.setText(label);
		selectedBossNameLabel.setIcon(null);
		selectedBossIconKey = iconKey;
		// Guards against a slow-loading icon from a previous selection
		// landing after the user has already picked a different boss.
		BossIcons.get(spriteManager, iconKey, icon ->
		{
			if (iconKey.equals(selectedBossIconKey))
			{
				selectedBossNameLabel.setIcon(icon);
			}
		});
		selectedBossNameLabel.setIconTextGap(8);
		pickerSection.setVisible(false);
		selectedBossBar.setVisible(true);
		revalidate();
		repaint();
	}

	private JScrollPane buildLeaderboardScroll()
	{
		leaderboardTitle.setEditable(false);
		leaderboardTitle.setFocusable(false);
		leaderboardTitle.setLineWrap(true);
		leaderboardTitle.setWrapStyleWord(true);
		leaderboardTitle.setOpaque(false);
		leaderboardTitle.setForeground(PbTrackerTheme.GOLD);
		leaderboardTitle.setFont(FontManager.getRunescapeBoldFont());
		leaderboardTitle.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		leaderboardRows.setLayout(new BoxLayout(leaderboardRows, BoxLayout.Y_AXIS));
		leaderboardRows.setBackground(PbTrackerTheme.BG);

		// Scrollable so the JViewport clamps this to the actual visible
		// width instead of using its raw preferred size - see PbListPanel's
		// class doc for why a plain JPanel here lets wrapped text force the
		// whole panel wider instead of wrapping.
		JPanel leaderboardContainer = new ScrollableWidthPanel(new BorderLayout());
		leaderboardContainer.setBackground(PbTrackerTheme.BG);
		leaderboardContainer.add(leaderboardTitle, BorderLayout.NORTH);
		leaderboardContainer.add(leaderboardRows, BorderLayout.CENTER);

		JScrollPane leaderboardScroll = new JScrollPane(leaderboardContainer);
		leaderboardScroll.setBorder(null);
		leaderboardScroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		leaderboardScroll.getVerticalScrollBar().setUnitIncrement(16);
		return leaderboardScroll;
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
		showSelectedBossBar(entry.label, entry.key);

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
		modeRow.setBackground(PbTrackerTheme.BG);
		JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		sizeRow.setBackground(PbTrackerTheme.BG);

		List<JLabel> modeLabels = new ArrayList<>();
		for (BossGroups.RaidMode mode : modes)
		{
			JLabel modeLabel = clickableLabel(mode.modeLabel, () ->
			{
				setActiveLabel(modeLabels, modeLabels.get(modes.indexOf(mode)));
				selectMode(mode, entry.label, sizeRow);
			});
			modeLabels.add(modeLabel);
			modeRow.add(modeLabel);
		}

		drillDownPanel.add(modeRow);
		drillDownPanel.add(sizeRow);
		drillDownPanel.revalidate();
		drillDownPanel.repaint();

		// Auto-select the first mode so a leaderboard shows immediately.
		setActiveLabel(modeLabels, modeLabels.get(0));
		selectMode(modes.get(0), entry.label, sizeRow);
	}

	/**
	 * A dropdown rather than a row of pill buttons - a raid+mode can have a
	 * dozen-plus size/kind variants (1-5 players x Overall/Room, sometimes
	 * legacy entries too), and a wall of buttons either wraps into a mess or
	 * gets visually lost. A combo box scales to however many there are
	 * without crowding anything else in the sidebar.
	 */
	private void selectMode(BossGroups.RaidMode mode, String raidLabel, JPanel sizeRow)
	{
		sizeRow.removeAll();
		sizeRow.setLayout(new BorderLayout());

		if (mode.variants.isEmpty())
		{
			sizeRow.revalidate();
			sizeRow.repaint();
			return;
		}

		JComboBox<BossGroups.KeyLabel> sizeCombo = new JComboBox<>(mode.variants.toArray(new BossGroups.KeyLabel[0]));
		sizeCombo.setBackground(PbTrackerTheme.PANEL);
		sizeCombo.setForeground(PbTrackerTheme.TEXT);
		java.awt.Font comboFont = FontManager.getRunescapeFont();
		sizeCombo.setFont(comboFont.deriveFont(comboFont.getSize2D() + 2f));
		sizeCombo.setBorder(BorderFactory.createLineBorder(PbTrackerTheme.PANEL_BORDER));
		sizeCombo.setPreferredSize(new Dimension(0, 32));
		sizeCombo.setFocusable(false);
		// Without this, the dropdown list that opens on click uses Swing's
		// default plain black-on-white renderer instead of the theme - the
		// box itself picks up setForeground, but the popup list doesn't.
		sizeCombo.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				label.setBackground(isSelected ? PbTrackerTheme.HIGHLIGHT_BG : PbTrackerTheme.PANEL);
				label.setForeground(isSelected ? PbTrackerTheme.GOLD_LIGHT : PbTrackerTheme.TEXT);
				label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
				return label;
			}
		});
		sizeCombo.addActionListener(e ->
		{
			BossGroups.KeyLabel selected = (BossGroups.KeyLabel) sizeCombo.getSelectedItem();
			if (selected != null)
			{
				loadLeaderboard(selected.key, raidLabel + " - " + mode.modeLabel + " - " + selected.label);
			}
		});
		sizeRow.add(sizeCombo, BorderLayout.CENTER);
		sizeRow.revalidate();
		sizeRow.repaint();

		// The combo box already defaults to index 0, but Swing only fires
		// the ActionListener on an actual change - load the first size's
		// leaderboard directly so one shows immediately, same as before.
		BossGroups.KeyLabel first = mode.variants.get(0);
		loadLeaderboard(first.key, raidLabel + " - " + mode.modeLabel + " - " + first.label);
	}

	/** Marks one label in the row as the active selection (gold border/text), clearing the others. */
	private void setActiveLabel(List<JLabel> labels, JLabel active)
	{
		for (JLabel label : labels)
		{
			boolean isActive = label == active;
			label.setBackground(isActive ? PbTrackerTheme.HIGHLIGHT_BG : PbTrackerTheme.PANEL);
			label.setForeground(isActive ? PbTrackerTheme.GOLD_LIGHT : PbTrackerTheme.TEXT);
			label.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(isActive ? PbTrackerTheme.GOLD : PbTrackerTheme.PANEL_BORDER),
				BorderFactory.createEmptyBorder(5, 10, 5, 10)
			));
		}
	}

	private JLabel clickableLabel(String text, Runnable onClick)
	{
		JLabel label = new JLabel(text);
		label.setForeground(PbTrackerTheme.TEXT);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(5, 10, 5, 10)
		));
		label.setOpaque(true);
		label.setBackground(PbTrackerTheme.PANEL);
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
		leaderboardTitle.setText(PbTrackerPlugin.wrapFriendly(title));
		leaderboardRows.removeAll();
		JLabel loading = new JLabel("Loading...");
		loading.setForeground(PbTrackerTheme.TEXT_DIM);
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
		JPanel highlightedRow = null;
		if (rows == null)
		{
			JLabel error = new JLabel("Lookup failed - try again later.");
			error.setForeground(PbTrackerTheme.TEXT_DIM);
			leaderboardRows.add(error);
		}
		else if (rows.isEmpty())
		{
			JLabel empty = new JLabel("No synced PBs for this boss yet.");
			empty.setForeground(PbTrackerTheme.TEXT_DIM);
			leaderboardRows.add(empty);
		}
		else
		{
			for (int i = 0; i < rows.size(); i++)
			{
				SyncClient.LeaderboardRow row = rows.get(i);
				boolean isHighlighted = highlight != null && row.displayName.equalsIgnoreCase(highlight);
				JPanel rowPanel = buildLeaderboardRow(i + 1, row, isHighlighted);
				leaderboardRows.add(rowPanel);
				if (isHighlighted)
				{
					highlightedRow = rowPanel;
				}
			}
		}
		leaderboardRows.revalidate();
		leaderboardRows.repaint();

		// Jumping here from a PB row is pointless if the player's own rank
		// isn't actually visible without manually scrolling - bring it into
		// view automatically. Deferred one tick so the rows above it have
		// already been laid out and their heights are known.
		if (highlightedRow != null)
		{
			JPanel target = highlightedRow;
			// scrollRectToVisible wants a rect in the component's OWN
			// coordinate space (0,0 to its own size), not its bounds
			// relative to its parent.
			SwingUtilities.invokeLater(() -> target.scrollRectToVisible(new java.awt.Rectangle(0, 0, target.getWidth(), target.getHeight())));
		}
	}

	// Gold/silver/bronze for the top 3, matching a medal-podium convention -
	// no real crown/medal image assets available, so color-coding the rank
	// number itself stands in for the mockup's crown icons.
	private static String rankBadge(int rank)
	{
		return "#" + rank;
	}

	private static Color rankColor(int rank)
	{
		switch (rank)
		{
			case 1:
				return PbTrackerTheme.RANK_GOLD;
			case 2:
				return PbTrackerTheme.RANK_SILVER;
			case 3:
				return PbTrackerTheme.RANK_BRONZE;
			default:
				return PbTrackerTheme.TEXT;
		}
	}

	private JPanel buildLeaderboardRow(int rank, SyncClient.LeaderboardRow row, boolean isHighlighted)
	{
		JPanel panel = new JPanel(new BorderLayout(8, 0));
		panel.setBackground(isHighlighted ? PbTrackerTheme.HIGHLIGHT_BG : PbTrackerTheme.PANEL);
		panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel rankAndName = new JLabel(rankBadge(rank) + "  " + row.displayName);
		rankAndName.setForeground(isHighlighted ? PbTrackerTheme.GOLD : rankColor(rank));

		JLabel time = new JLabel(PbTrackerPlugin.formatTime(row.timeSeconds));
		time.setForeground(PbTrackerTheme.GOLD_LIGHT);

		panel.add(rankAndName, BorderLayout.WEST);
		panel.add(time, BorderLayout.EAST);

		// Measured after both labels are added - see PbListPanel's
		// buildRowContainer() comment for why order matters here.
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, PbTrackerTheme.BG),
			BorderFactory.createEmptyBorder(6, 10, 6, 10)
		));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));

		PbTrackerPlugin.addRowClickListener(panel, () -> onPlayerClick.accept(row.displayName));
		return panel;
	}

	/** Called from the "jump to leaderboard, scrolled to this player" flow (rank click on a PB row). */
	void showBossHighlighted(String bossKey, String highlightPlayerName)
	{
		pendingHighlight = highlightPlayerName;
		showSelectedBossBar(PbTrackerPlugin.titleCase(bossKey), bossKey);
		drillDownPanel.removeAll();
		drillDownPanel.revalidate();
		drillDownPanel.repaint();
		loadLeaderboard(bossKey, PbTrackerPlugin.titleCase(bossKey));
	}

	/**
	 * A plain JPanel isn't Scrollable-aware, so its enclosing JViewport just
	 * takes its raw preferred width verbatim - which, because of the wrapped
	 * JTextArea leaderboard title, is misleadingly wide and forces the whole
	 * panel wider instead of wrapping. Implementing Scrollable and tracking
	 * viewport width fixes that.
	 */
	private static final class ScrollableWidthPanel extends JPanel implements javax.swing.Scrollable
	{
		ScrollableWidthPanel(java.awt.LayoutManager layout)
		{
			super(layout);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
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
	}
}
