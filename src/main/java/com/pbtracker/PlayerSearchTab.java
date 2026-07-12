package com.pbtracker;

import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.util.function.BiConsumer;

/**
 * "Player Search" tab - a name box + the same PbListPanel rendering used by
 * MyPbsTab, so results look identical whether you're looking at your own
 * data or someone else's.
 */
class PlayerSearchTab extends JPanel
{
	private final SyncClient syncClient;
	private final BiConsumer<String, String> onBossClickHandler;
	private final PbListPanel listPanel = new PbListPanel();
	private final JTextField searchField = new JTextField();

	PlayerSearchTab(SyncClient syncClient, BiConsumer<String, String> onBossClick)
	{
		this.syncClient = syncClient;
		this.onBossClickHandler = onBossClick;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel searchBar = new JPanel(new BorderLayout(6, 0));
		searchBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setForeground(java.awt.Color.WHITE);
		ActionListener search = e -> doSearch();
		searchField.addActionListener(search);

		JButton searchButton = new JButton("Search");
		searchButton.addActionListener(search);

		searchBar.add(searchField, BorderLayout.CENTER);
		searchBar.add(searchButton, BorderLayout.EAST);

		add(searchBar, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(listPanel);
		scrollPane.setBorder(null);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		add(scrollPane, BorderLayout.CENTER);

		listPanel.showMessage("Search a player to see their synced PBs.");
	}

	private void doSearch()
	{
		String name = searchField.getText().trim();
		if (name.isEmpty())
		{
			return;
		}
		searchFor(name);
	}

	/** Also used by the right-click "Lookup PB Tracker" menu option, which switches to this tab and searches directly. */
	void searchFor(String name)
	{
		searchField.setText(name);
		listPanel.showMessage("Loading...");
		new Thread(() ->
		{
			SyncClient.PlayerLookupResult result = syncClient.lookupPlayer(name);
			SwingUtilities.invokeLater(() ->
			{
				switch (result.kind)
				{
					case FOUND:
						listPanel.showPlayer(result.player, onBossClickHandler);
						break;
					case NOT_FOUND:
						listPanel.showMessage("No synced PB data found for \"" + name + "\" yet.");
						break;
					case AMBIGUOUS:
						listPanel.showMessage("Multiple synced accounts share this name - check the website directly.");
						break;
					case ERROR:
					default:
						listPanel.showMessage("Lookup failed - try again later.");
						break;
				}
			});
		}, "pbtracker-search-lookup").start();
	}
}
