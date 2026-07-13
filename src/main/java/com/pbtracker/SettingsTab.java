package com.pbtracker;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.FontManager;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * A custom "Settings" tab, since RuneLite doesn't expose a public API for a
 * plugin to open its own real config panel from inside its own UI - the
 * classes involved (PluginListPanel, TopLevelConfigPanel) are package-private
 * to RuneLite core, and reflecting into them would get this plugin rejected
 * from the Plugin Hub review. This mirrors the toggle-style options from
 * PbTrackerConfig directly, backed by the same ConfigManager the real config
 * panel would use - just this plugin's own UI instead of RuneLite's.
 */
class SettingsTab extends JPanel
{
	private static final String CONFIG_GROUP = "pbtracker";

	SettingsTab(ConfigManager configManager, PbTrackerConfig config)
	{
		setLayout(new BorderLayout());
		setBackground(PbTrackerTheme.BG);

		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(PbTrackerTheme.BG);
		rows.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		rows.add(buildToggle("Auto-sync new PBs", "Automatically send a PB to the server the moment RuneLite records a new one",
			config.autoSync(), "autoSync", configManager));
		rows.add(buildToggle("Sync all PBs on login", "Bulk-upload every known PB shortly after logging in",
			config.syncOnLogin(), "syncOnLogin", configManager));
		rows.add(buildToggle("Enable !pbr command", "Type !pbr [boss] in chat to see your PB and leaderboard rank for that boss",
			config.pbrCommand(), "pbrCommand", configManager));
		rows.add(buildToggle("Right-click PB lookup", "Adds a \"Search PB\" right-click option on other players",
			config.rightClickLookup(), "rightClickLookup", configManager));

		add(rows, BorderLayout.NORTH);
	}

	private JPanel buildToggle(String label, String description, boolean initialValue, String configKey, ConfigManager configManager)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(PbTrackerTheme.PANEL);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, PbTrackerTheme.PANEL_BORDER),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)
		));
		row.setAlignmentX(0);
		row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		JCheckBox checkbox = new JCheckBox(label, initialValue);
		checkbox.setForeground(PbTrackerTheme.TEXT);
		checkbox.setFont(FontManager.getRunescapeBoldFont());
		checkbox.setBackground(PbTrackerTheme.PANEL);
		checkbox.setFocusPainted(false);
		checkbox.setAlignmentX(0);
		checkbox.addActionListener(e -> configManager.setConfiguration(CONFIG_GROUP, configKey, checkbox.isSelected()));
		row.add(checkbox);

		JLabel descLabel = new JLabel("<html>" + description + "</html>");
		descLabel.setForeground(PbTrackerTheme.TEXT_DIM);
		descLabel.setBorder(BorderFactory.createEmptyBorder(2, 24, 0, 0));
		descLabel.setAlignmentX(0);
		row.add(descLabel);

		return row;
	}
}
