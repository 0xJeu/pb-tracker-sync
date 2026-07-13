package com.pbtracker;

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
	private final PbListPanel listPanel = new PbListPanel();
	private final JScrollPane scrollPane;
	private long requestGeneration;

	MyPbsTab(SyncClient syncClient, BiConsumer<String, String> onBossClick)
	{
		this.syncClient = syncClient;
		this.onBossClickHandler = onBossClick;
		setLayout(new BorderLayout());
		setBackground(PbTrackerTheme.BG);

		scrollPane = new JScrollPane(listPanel);
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
		long request = ++requestGeneration;
		new Thread(() ->
		{
			SyncClient.PlayerLookupResult result = syncClient.lookupPlayer(displayName);
			SwingUtilities.invokeLater(() ->
			{
				if (request != requestGeneration)
				{
					return;
				}
				switch (result.kind)
				{
					case FOUND:
						listPanel.showPlayer(result.player, onBossClickHandler);
						scrollToTop();
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

	private void scrollToTop()
	{
		SwingUtilities.invokeLater(() -> scrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0)));
	}

	void showLoggedOut()
	{
		requestGeneration++;
		listPanel.showMessage("Log in to see your PBs.");
	}
}
