package com.pbtracker;

import net.runelite.client.game.SpriteManager;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.function.BiConsumer;

/**
 * "My PBs" tab - auto-loads the logged-in player's own synced PBs, no typing
 * required (mirrors the in-game hiscore lookup convention of showing your
 * own stats by default).
 */
class MyPbsTab extends JPanel
{
	private final SyncClient syncClient;
	private final BiConsumer<String, String> onBossClickHandler;
	private final PbListPanel listPanel;

	MyPbsTab(SyncClient syncClient, SpriteManager spriteManager, BiConsumer<String, String> onBossClick)
	{
		this.syncClient = syncClient;
		this.onBossClickHandler = onBossClick;
		this.listPanel = new PbListPanel(spriteManager);
		setLayout(new BorderLayout());
		setBackground(PbTrackerTheme.BG);

		JScrollPane scrollPane = new JScrollPane(listPanel);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		add(scrollPane, BorderLayout.CENTER);

		listPanel.showMessage("Log in to see your PBs.");
	}

	/** Called on login (and whenever the panel is (re)opened) with the local player's name. */
	void load(String displayName)
	{
		listPanel.showMessage("Loading...");
		new Thread(() ->
		{
			SyncClient.PlayerLookupResult result = syncClient.lookupPlayer(displayName);
			SwingUtilities.invokeLater(() ->
			{
				switch (result.kind)
				{
					case FOUND:
						listPanel.showPlayer(result.player, onBossClickHandler);
						break;
					case NOT_FOUND:
						listPanel.showMessage("No synced PB data found yet - sync with the plugin first.");
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
		}, "pbtracker-mypbs-lookup").start();
	}

	void showLoggedOut()
	{
		listPanel.showMessage("Log in to see your PBs.");
	}
}
