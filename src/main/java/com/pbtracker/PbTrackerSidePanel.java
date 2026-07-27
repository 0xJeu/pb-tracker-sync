package com.pbtracker;

import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Component;

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
 * <p>
 * A "Back" bar appears above the tabs whenever one of those jumps fires,
 * remembering whichever tab you were on before it - previously the only
 * way back to (say) the boss list after following a PB row into the
 * Bosses tab was to click yet another boss, there was no way back to
 * just browsing.
 */
class PbTrackerSidePanel extends PluginPanel
{
	private final JTabbedPane tabs = new JTabbedPane();
	private final MyPbsTab myPbsTab;
	private final PlayerSearchTab searchTab;
	private final BossesTab bossesTab;

	private final JPanel backBar = new JPanel(new BorderLayout());
	private Component previousTab;

	PbTrackerSidePanel(SyncClient syncClient, SpriteManager spriteManager, PbTrackerConfig config, LocalProfileLoadCoordinator localProfileLoadCoordinator)
	{
		super(false);
		setBackground(PbTrackerTheme.BG);
		setLayout(new BorderLayout());

		bossesTab = new BossesTab(syncClient, spriteManager, this::jumpToSearch);
		myPbsTab = new MyPbsTab(syncClient, spriteManager, config, this::jumpToBoss, localProfileLoadCoordinator);
		searchTab = new PlayerSearchTab(syncClient, spriteManager, config, this::jumpToBoss);

		tabs.setBackground(PbTrackerTheme.BG);
		tabs.setForeground(java.awt.Color.WHITE);
		tabs.addTab("My PBs", myPbsTab);
		tabs.addTab("Search", searchTab);
		tabs.addTab("Bosses", bossesTab);

		setUpBackBar();
		add(backBar, BorderLayout.NORTH);
		add(tabs, BorderLayout.CENTER);
	}

	private void setUpBackBar()
	{
		backBar.setBackground(PbTrackerTheme.BG);
		backBar.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
		backBar.setVisible(false);

		JLabel backLabel = new JLabel("< Back");
		backLabel.setForeground(PbTrackerTheme.GOLD_LIGHT);
		backLabel.setFont(FontManager.getRunescapeBoldFont());
		backLabel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(5, 10, 5, 10)
		));
		backLabel.setOpaque(true);
		backLabel.setBackground(PbTrackerTheme.PANEL);
		PbTrackerPlugin.addRowClickListener(backLabel, this::goBack);
		backBar.add(backLabel, BorderLayout.WEST);
	}

	private void showBackTo(Component current)
	{
		previousTab = current;
		backBar.setVisible(true);
		revalidate();
		repaint();
	}

	private void goBack()
	{
		if (previousTab != null)
		{
			tabs.setSelectedComponent(previousTab);
		}
		previousTab = null;
		backBar.setVisible(false);
		revalidate();
		repaint();
	}

	private void jumpToBoss(String bossKey, String highlightPlayerName)
	{
		showBackTo(tabs.getSelectedComponent());
		tabs.setSelectedComponent(bossesTab);
		bossesTab.showBossHighlighted(bossKey, highlightPlayerName);
	}

	private void jumpToSearch(String playerName)
	{
		showBackTo(tabs.getSelectedComponent());
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
		myPbsTab.setAllBosses(bosses);
		searchTab.setAllBosses(bosses);
	}

	/** Called by the right-click "Search PB" menu option. */
	void lookupPlayer(String name)
	{
		tabs.setSelectedComponent(searchTab);
		searchTab.searchFor(name);
	}

	void onSettingsChanged()
	{
		myPbsTab.onSettingsChanged();
		searchTab.onSettingsChanged();
	}
}
