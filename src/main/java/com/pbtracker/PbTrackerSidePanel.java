package com.pbtracker;

import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/**
 * Root side panel: three tabs (My PBs / Player Search / Bosses), matching
 * the website's own three top-level views (player lookup, boss leaderboard)
 * plus a "my own stats" shortcut the site doesn't need (it's not tied to a
 * logged-in account the way the plugin is).
 * <p>
 * Clicking a boss on a PB row jumps to the Bosses tab with that boss's
 * leaderboard shown, highlighted on the clicked player - mirrors the
 * website's rank-click-through behaviour. Clicking a player on a
 * leaderboard row jumps to the Search tab and looks them up.
 */
class PbTrackerSidePanel extends PluginPanel
{
	private final JTabbedPane tabs = new JTabbedPane();
	private final MyPbsTab myPbsTab;
	private final PlayerSearchTab searchTab;
	private final BossesTab bossesTab;

	PbTrackerSidePanel(SyncClient syncClient, SpriteManager spriteManager)
	{
		super(false);
		setBackground(PbTrackerTheme.BG);
		setLayout(new BorderLayout());

		bossesTab = new BossesTab(syncClient, spriteManager, this::jumpToSearch);
		myPbsTab = new MyPbsTab(syncClient, spriteManager, this::jumpToBoss);
		searchTab = new PlayerSearchTab(syncClient, spriteManager, this::jumpToBoss);

		tabs.setBackground(PbTrackerTheme.BG);
		tabs.setForeground(java.awt.Color.WHITE);
		tabs.addTab("My PBs", myPbsTab);
		tabs.addTab("Search", searchTab);
		tabs.addTab("Bosses", bossesTab);

		add(tabs, BorderLayout.CENTER);
	}

	private void jumpToBoss(String bossKey, String highlightPlayerName)
	{
		tabs.setSelectedComponent(bossesTab);
		bossesTab.showBossHighlighted(bossKey, highlightPlayerName);
	}

	private void jumpToSearch(String playerName)
	{
		tabs.setSelectedComponent(searchTab);
		searchTab.searchFor(playerName);
	}

	/** Called on login/logout and whenever the panel is (re)activated. */
	void onLocalPlayerChanged(String displayName)
	{
		if (displayName == null)
		{
			myPbsTab.showLoggedOut();
		}
		else
		{
			myPbsTab.load(displayName);
		}
	}

	void setBosses(java.util.List<String> bosses)
	{
		bossesTab.setBosses(bosses);
	}

	/** Called by the right-click "Search PB" menu option. */
	void lookupPlayer(String name)
	{
		tabs.setSelectedComponent(searchTab);
		searchTab.searchFor(name);
	}
}
