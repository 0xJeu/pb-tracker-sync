package com.pbtracker;

import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
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
	private final JLabel viewOnWebsiteButton;

	private String currentDisplayName;

	MyPbsTab(SyncClient syncClient, SpriteManager spriteManager, BiConsumer<String, String> onBossClick, Runnable onSettingsClick)
	{
		this.syncClient = syncClient;
		this.onBossClickHandler = onBossClick;
		this.listPanel = new PbListPanel(spriteManager);
		setLayout(new BorderLayout());
		setBackground(PbTrackerTheme.BG);

		viewOnWebsiteButton = buildViewOnWebsiteButton();
		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBackground(PbTrackerTheme.BG);
		topBar.add(viewOnWebsiteButton, BorderLayout.CENTER);
		topBar.add(buildSettingsButton(onSettingsClick), BorderLayout.EAST);
		add(topBar, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(listPanel);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		add(scrollPane, BorderLayout.CENTER);

		listPanel.showMessage("Log in to see your PBs.");
	}

	/** Opens this player's PB tracker profile page on the website - same URL-building the config-driven "open profile on sync" feature already uses. */
	private JLabel buildViewOnWebsiteButton()
	{
		JLabel button = new JLabel("View My Profile on Website", SwingConstants.CENTER);
		button.setForeground(PbTrackerTheme.GOLD_LIGHT);
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)
		));
		button.setOpaque(true);
		button.setBackground(PbTrackerTheme.PANEL);
		PbTrackerPlugin.addRowClickListener(button, () ->
		{
			if (currentDisplayName != null)
			{
				LinkBrowser.browse(PbTrackerPlugin.buildProfileUrl(currentDisplayName));
			}
		});
		return button;
	}

	/**
	 * Plain text "Settings", not a gear glyph - RuneLite's bitmap OSRS font
	 * doesn't have a glyph for Unicode symbols like "⚙" and silently falls
	 * back to a "tofu" box character instead (same issue the old chevron and
	 * en-dash had).
	 */
	private JLabel buildSettingsButton(Runnable onSettingsClick)
	{
		JLabel button = new JLabel("Settings", SwingConstants.CENTER);
		button.setForeground(PbTrackerTheme.TEXT);
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(8, 12, 8, 12)
		));
		button.setOpaque(true);
		button.setBackground(PbTrackerTheme.PANEL);
		PbTrackerPlugin.addRowClickListener(button, onSettingsClick);
		return button;
	}

	/** Every boss key this plugin knows about system-wide - forwarded to the list panel so untracked bosses can show a dash instead of being omitted. */
	void setAllBosses(java.util.List<String> bosses)
	{
		listPanel.setAllBosses(bosses);
	}

	/** Called on login (and whenever the panel is (re)opened) with the local player's name. */
	void load(String displayName)
	{
		currentDisplayName = displayName;
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
		currentDisplayName = null;
		listPanel.showMessage("Log in to see your PBs.");
	}
}
