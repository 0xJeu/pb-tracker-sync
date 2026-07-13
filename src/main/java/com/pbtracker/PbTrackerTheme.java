package com.pbtracker;

import java.awt.Color;

/**
 * Exact palette from the website's frontend/src/theme.css :root block, so
 * the side panel looks like part of the same product rather than a generic
 * RuneLite plugin using RuneLite's own ColorScheme grays.
 */
final class PbTrackerTheme
{
	private PbTrackerTheme()
	{
	}

	static final Color BG = new Color(0x14, 0x10, 0x0c);
	static final Color PANEL = new Color(0x1f, 0x19, 0x12);
	static final Color PANEL_BORDER = new Color(0x3d, 0x2f, 0x1f);
	static final Color GOLD = new Color(0xff, 0x98, 0x1f);
	static final Color GOLD_LIGHT = new Color(0xff, 0xb8, 0x4d);
	static final Color TEXT = new Color(0xe8, 0xde, 0xd0);
	static final Color TEXT_DIM = new Color(0xa8, 0x9c, 0x8a);

	// A shade between PANEL and BG, for rows/cards that need to read as
	// slightly "raised" off the panel background (mirrors the website's
	// #100d09 input/row background).
	static final Color ROW_BG = new Color(0x10, 0x0d, 0x09);

	static final Color RANK_GOLD = new Color(0xFF, 0xD7, 0x00);
	static final Color RANK_SILVER = new Color(0xC0, 0xC0, 0xC0);
	static final Color RANK_BRONZE = new Color(0xCD, 0x7F, 0x32);

	static final Color HIGHLIGHT_BG = new Color(0x3A, 0x2A, 0x10);
}
