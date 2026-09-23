package com.pbtracker;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class PbListPanelTest
{
	@Test
	public void escapesSearchTextBeforeRenderingItAsSwingHtml()
	{
		assertEquals("&lt;img src=&quot;x&quot;&gt; &amp; &#39;test&#39;", PbListPanel.escapeHtml("<img src=\"x\"> & 'test'"));
	}

	@Test
	public void clickingVisibleBossTextTriggersNavigation() throws Exception
	{
		AtomicBoolean clicked = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() ->
		{
			PbListPanel panel = new PbListPanel(null, null);
			SyncClient.PlayerLookupResponse player = new SyncClient.PlayerLookupResponse();
			player.displayName = "Tester";
			SyncClient.PbEntryDto pb = new SyncClient.PbEntryDto();
			pb.boss = "zulrah";
			pb.timeSeconds = 60;
			pb.rank = 1;
			player.pbs = Collections.singletonList(pb);
			panel.showPlayer(player, (boss, name) -> clicked.set(true));

			JTextArea heading = findTextArea(panel, "Zulrah");
			heading.dispatchEvent(new MouseEvent(heading, MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(), 0, 2, 2, 1, false));
		});
		assertTrue(clicked.get());
	}

	@Test
	public void lateBossListRerendersAnAlreadyLoadedPlayer() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			PbListPanel panel = new PbListPanel(null, null);
			SyncClient.PlayerLookupResponse player = new SyncClient.PlayerLookupResponse();
			player.displayName = "Tester";
			SyncClient.PbEntryDto pb = new SyncClient.PbEntryDto();
			pb.boss = "zulrah";
			pb.timeSeconds = 60;
			pb.rank = 1;
			player.pbs = Collections.singletonList(pb);

			panel.showPlayer(player, (boss, name) -> { });
			panel.setAllBosses(java.util.List.of("zulrah", "vorkath"));

			assertTrue(findTextAreaOrNull(panel, "Vorkath") != null);
		});
	}

	private static final class FakeConfig implements PbTrackerConfig
	{
		private final boolean showOverall;
		private final boolean showRoom;

		FakeConfig(boolean showOverall, boolean showRoom)
		{
			this.showOverall = showOverall;
			this.showRoom = showRoom;
		}

		@Override
		public boolean showOverallVariants()
		{
			return showOverall;
		}

		@Override
		public boolean showRoomVariants()
		{
			return showRoom;
		}
	}

	@Test
	public void hidesOverallVariantRowsWhenConfiguredOff() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			PbListPanel panel = new PbListPanel(null, new FakeConfig(false, true));
			SyncClient.PlayerLookupResponse player = new SyncClient.PlayerLookupResponse();
			player.displayName = "Tester";
			SyncClient.PbEntryDto overall = new SyncClient.PbEntryDto();
			overall.boss = "theatre of blood - fastest overall (3 player)";
			overall.timeSeconds = 1200;
			overall.rank = 2;
			SyncClient.PbEntryDto room = new SyncClient.PbEntryDto();
			room.boss = "theatre of blood - fastest room (3 player)";
			room.timeSeconds = 500;
			room.rank = 1;
			player.pbs = java.util.List.of(overall, room);
			panel.showPlayer(player, (boss, name) -> { });

			JTextArea heading = findTextArea(panel, "Theatre Of Blood");
			heading.dispatchEvent(new MouseEvent(heading, MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(), 0, 2, 2, 1, false));

			assertTrue(findTextAreaOrNull(panel, "Trio - Room   8:20   #1") != null);
			assertTrue(findTextAreaOrNull(panel, "Trio - Overall   20:00   #2") == null);
		});
	}

	private static SyncClient.PbEntryDto entry(String boss, double timeSeconds, int rank)
	{
		SyncClient.PbEntryDto pb = new SyncClient.PbEntryDto();
		pb.boss = boss;
		pb.timeSeconds = timeSeconds;
		pb.rank = rank;
		return pb;
	}

	@Test
	public void collapsedDoomRowShowsDeepestDelveAndItsTime() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			PbListPanel panel = new PbListPanel(null, new FakeConfig(true, true));
			SyncClient.PlayerLookupResponse player = new SyncClient.PlayerLookupResponse();
			player.displayName = "Tester";
			player.pbs = java.util.List.of(
				entry("Doom of Mokhaiotl - Delve 1", 49, 1),
				entry("Doom of Mokhaiotl - Delve 2", 55, 1),
				entry("Doom of Mokhaiotl - Delve 3", 96, 1),
				entry("Doom of Mokhaiotl - Delve 4", 113, 1),
				entry("chambers of xeric - fastest overall (solo)", 2000, 3),
				entry("chambers of xeric - fastest overall (3 players)", 1000, 2)
			);
			panel.setAllBosses(java.util.List.of("Doom of Mokhaiotl - Delve 1", "Doom of Mokhaiotl - Delve 8+"));
			panel.showPlayer(player, (boss, name) -> { });

			assertTrue(findLabelOrNull(panel, "1:53 (Delve 4)") != null);
			assertTrue(findLabelOrNull(panel, "16:40") != null);
			assertTrue(findTextAreaOrNull(panel, "Doom Of Mokhaiotl - Delve 1") == null);

			JTextArea heading = findTextArea(panel, "Doom Of Mokhaiotl");
			heading.dispatchEvent(new MouseEvent(heading, MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(), 0, 2, 2, 1, false));
			assertTrue(findTextAreaOrNull(panel, "Delve 1   0:49   #1") != null);
		});
	}

	@Test
	public void formatsSummaryTimeWithOptionalTierSuffix()
	{
		assertEquals("1:53 (Delve 4)", PbListPanel.summaryTimeText(113, "Delve 4"));
		assertEquals("1:53", PbListPanel.summaryTimeText(113, null));
	}

	private static javax.swing.JLabel findLabelOrNull(Container root, String text)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel) child).getText()))
			{
				return (javax.swing.JLabel) child;
			}
			if (child instanceof Container)
			{
				javax.swing.JLabel found = findLabelOrNull((Container) child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static JTextArea findTextArea(Container root, String text)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JTextArea && ((JTextArea) child).getText().equals(text))
			{
				return (JTextArea) child;
			}
			if (child instanceof Container)
			{
				JTextArea found = findTextAreaOrNull((Container) child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		throw new AssertionError("Text area not found: " + text);
	}

	private static JTextArea findTextAreaOrNull(Container root, String text)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JTextArea && ((JTextArea) child).getText().equals(text))
			{
				return (JTextArea) child;
			}
			if (child instanceof Container)
			{
				JTextArea found = findTextAreaOrNull((Container) child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
