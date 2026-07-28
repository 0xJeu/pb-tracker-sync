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
import java.util.function.Supplier;

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
	private final JScrollPane scrollPane;
	private final JLabel viewOnWebsiteButton;
	private final LocalProfileLoadCoordinator localProfileLoadCoordinator;
	private final Supplier<String> accountHashSupplier;
	private long requestGeneration;
	private volatile String inFlightDisplayName;

	private String currentDisplayName;

	MyPbsTab(SyncClient syncClient, SpriteManager spriteManager, PbTrackerConfig config, BiConsumer<String, String> onBossClick, LocalProfileLoadCoordinator localProfileLoadCoordinator, Supplier<String> accountHashSupplier)
	{
		this.syncClient = syncClient;
		this.onBossClickHandler = onBossClick;
		this.localProfileLoadCoordinator = localProfileLoadCoordinator;
		this.accountHashSupplier = accountHashSupplier;
		this.listPanel = new PbListPanel(spriteManager, config);
		setLayout(new BorderLayout());
		setBackground(PbTrackerTheme.BG);

		viewOnWebsiteButton = buildViewOnWebsiteButton();
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(viewOnWebsiteButton, BorderLayout.NORTH);
		header.add(buildRefreshButton(), BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);

		scrollPane = new JScrollPane(listPanel);
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

	private JLabel buildRefreshButton()
	{
		JLabel button = new JLabel("Refresh My PBs", SwingConstants.CENTER);
		button.setForeground(PbTrackerTheme.GOLD_LIGHT);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
		button.setOpaque(true);
		button.setBackground(PbTrackerTheme.PANEL);
		PbTrackerPlugin.addRowClickListener(button, this::refresh);
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
		String normalized = displayName == null ? "" : displayName.trim().toLowerCase();
		if (normalized.equals(inFlightDisplayName))
		{
			// Coordinator-level gating (Task 2) already prevents most
			// duplicate calls; this is a direct guard against any other
			// caller re-entering load() for the same name while a lookup
			// thread is still running.
			return;
		}
		inFlightDisplayName = normalized;

		currentDisplayName = displayName;
		listPanel.showMessage("Loading...");
		long request = ++requestGeneration;
		new Thread(() ->
		{
			SyncClient.PlayerLookupResult result = syncClient.lookupPlayer(displayName);
			SwingUtilities.invokeLater(() ->
			{
				if (normalized.equals(inFlightDisplayName))
				{
					inFlightDisplayName = null;
				}
				if (request != requestGeneration)
				{
					return;
				}
				switch (result.kind)
				{
					case FOUND:
						listPanel.showPlayer(result.player, onBossClickHandler);
						scrollToTop();
						if (localProfileLoadCoordinator.markLoaded())
						{
							load(displayName);
						}
						break;
					case NOT_FOUND:
						listPanel.showMessage("No synced PB data found yet - sync with the plugin first.");
						if (localProfileLoadCoordinator.markLoaded())
						{
							load(displayName);
						}
						break;
					case AMBIGUOUS:
						listPanel.showMessage("Multiple synced accounts share this name - check the website directly.");
						if (localProfileLoadCoordinator.markLoaded())
						{
							load(displayName);
						}
						break;
					case ERROR:
					default:
						listPanel.showMessage("Lookup failed - try again later.");
						localProfileLoadCoordinator.markError(System.currentTimeMillis());
						break;
				}
			});
		}, "pbtracker-mypbs-lookup").start();
	}

	/** Goes through the coordinator's own manual-refresh decision (bypasses its "already loaded" cache but still coalesces a concurrent refresh at the coordinator level) so this path stays in sync with the coordinator's in-flight tracking instead of relying only on the local inFlightDisplayName guard. */
	void refresh()
	{
		if (currentDisplayName == null)
		{
			return;
		}
		String accountHash = accountHashSupplier.get();
		if (accountHash == null)
		{
			return;
		}
		LocalProfileLoadCoordinator.Decision decision = localProfileLoadCoordinator.onManualRefresh(accountHash, currentDisplayName);
		if (decision == LocalProfileLoadCoordinator.Decision.LOAD)
		{
			load(currentDisplayName);
		}
	}

	private void scrollToTop()
	{
		SwingUtilities.invokeLater(() -> scrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0)));
	}

	void showLoggedOut()
	{
		requestGeneration++;
		inFlightDisplayName = null;
		currentDisplayName = null;
		listPanel.showMessage("Log in to see your PBs.");
	}

	void onSettingsChanged()
	{
		listPanel.onSettingsChanged();
	}
}
