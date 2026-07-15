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
