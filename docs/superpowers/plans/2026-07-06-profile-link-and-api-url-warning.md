# API URL Confirmation + Open-Profile Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a confirmation prompt before `apiBaseUrl` can be changed, and a config-panel toggle that opens the current player's PB tracker profile in a browser.

**Architecture:** Two small additions to the existing plugin, following patterns already in the codebase - a `warning` attribute on an existing `@ConfigItem`, and a new checkbox-triggered action (same idiom as the existing `syncNow`/`dumpRawPbs` toggles) that builds a URL via a static, pure, unit-testable helper and opens it with RuneLite's `LinkBrowser`.

**Tech Stack:** Java 17, RuneLite plugin API, JUnit 4 (existing test setup), Gradle.

**Repo:** `osrs-pb-tracker/plugin` (this directory is its own git repo, remote `github.com/0xJeu/pb-tracker-sync`, branch `plugin-hub-submission` - confirm with `git remote -v` before committing if unsure).

---

### Task 1: Add confirmation warning to `apiBaseUrl`

**Files:**
- Modify: `src/main/java/com/pbtracker/PbTrackerConfig.java:10-19`

- [ ] **Step 1: Add the `warning` attribute**

Current code (lines 10-19):

```java
	@ConfigItem(
		keyName = "apiBaseUrl",
		name = "API base URL",
		description = "Base URL of your PB tracker backend, e.g. https://osrs-pb-tracker-backend.vercel.app",
		position = 0
	)
	default String apiBaseUrl()
	{
		return "https://osrs-pb-tracker-backend.vercel.app";
	}
```

Replace with:

```java
	@ConfigItem(
		keyName = "apiBaseUrl",
		name = "API base URL",
		description = "Base URL of your PB tracker backend, e.g. https://osrs-pb-tracker-backend.vercel.app",
		warning = "Changing this will send your PB data to a different server instead of the default "
			+ "PB Tracker leaderboard. Only change this if you're running your own backend or testing locally.",
		position = 0
	)
	default String apiBaseUrl()
	{
		return "https://osrs-pb-tracker-backend.vercel.app";
	}
```

- [ ] **Step 2: Compile to confirm the annotation is valid**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home gradle compileJava -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/pbtracker/PbTrackerConfig.java
git commit -m "Add confirmation warning before changing API base URL

George reported accidentally running against a stale/different API
URL without noticing. RuneLite's ConfigItem.warning() shows a
Yes/No confirmation dialog before the value changes - keeps the
field visible (so local dev and self-hosting still work) while
adding real friction against an accidental edit."
```

---

### Task 2: Add the `openProfile` config item

**Files:**
- Modify: `src/main/java/com/pbtracker/PbTrackerConfig.java:67-78` (end of file, after `dumpRawPbs`)

- [ ] **Step 1: Add the new config item**

Current end of file (lines 67-78):

```java
	@ConfigItem(
		keyName = "dumpRawPbs",
		name = "Copy raw PB data to clipboard",
		description = "Toggle this (either direction) to copy every raw personalbest.* "
			+ "value RuneLite has cached, and whether it would sync as-is, to your clipboard.",
		position = 5
	)
	default boolean dumpRawPbs()
	{
		return false;
	}
}
```

Replace with:

```java
	@ConfigItem(
		keyName = "dumpRawPbs",
		name = "Copy raw PB data to clipboard",
		description = "Toggle this (either direction) to copy every raw personalbest.* "
			+ "value RuneLite has cached, and whether it would sync as-is, to your clipboard.",
		position = 5
	)
	default boolean dumpRawPbs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "openProfile",
		name = "Open my profile in browser",
		description = "Toggle this (either direction) to open your PB tracker profile page in your browser.",
		position = 6
	)
	default boolean openProfile()
	{
		return false;
	}
}
```

- [ ] **Step 2: Compile to confirm**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home gradle compileJava -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/pbtracker/PbTrackerConfig.java
git commit -m "Add openProfile config item

New hidden-effect toggle in the Configuration panel, same
click-to-trigger idiom as syncNow/dumpRawPbs. Wiring to actually
open a browser comes in a later task."
```

---

### Task 3: Write the failing test for `buildProfileUrl`

**Files:**
- Modify: `src/test/java/com/pbtracker/PbTrackerPluginUnitTest.java` (end of file, before final `}`)

- [ ] **Step 1: Add the test**

Current end of file:

```java
	@Test
	public void rawPbReportSortsEntriesByKey()
	{
		Map<String, Double> raw = new LinkedHashMap<>();
		raw.put("zulrah", 41.0);
		raw.put("cerberus", 61.0);

		String report = PbTrackerPlugin.buildRawPbReport(raw);

		assertTrue(report.indexOf("cerberus") < report.indexOf("zulrah"));
	}
}
```

Replace with:

```java
	@Test
	public void rawPbReportSortsEntriesByKey()
	{
		Map<String, Double> raw = new LinkedHashMap<>();
		raw.put("zulrah", 41.0);
		raw.put("cerberus", 61.0);

		String report = PbTrackerPlugin.buildRawPbReport(raw);

		assertTrue(report.indexOf("cerberus") < report.indexOf("zulrah"));
	}

	@Test
	public void buildsProfileUrlForSimpleName()
	{
		assertEquals(
			"https://osrs-pb-tracker-frontend.vercel.app/player/Zulrah",
			PbTrackerPlugin.buildProfileUrl("Zulrah"));
	}

	@Test
	public void buildsProfileUrlWithUrlEncodedSpaces()
	{
		assertEquals(
			"https://osrs-pb-tracker-frontend.vercel.app/player/Blitzen+Jones",
			PbTrackerPlugin.buildProfileUrl("Blitzen Jones"));
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home gradle test -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home --tests "com.pbtracker.PbTrackerPluginUnitTest"`
Expected: `FAILURE` - `compileTestJava` fails with `cannot find symbol: method buildProfileUrl(java.lang.String)` (the method doesn't exist yet; this is the expected Java "red" state before implementing).

- [ ] **Step 3: Commit the test**

```bash
git add src/test/java/com/pbtracker/PbTrackerPluginUnitTest.java
git commit -m "Add failing tests for buildProfileUrl

TDD red step - buildProfileUrl doesn't exist yet, so this fails to
compile. Implementation follows in the next task."
```

---

### Task 4: Implement `buildProfileUrl` and make the tests pass

**Files:**
- Modify: `src/main/java/com/pbtracker/PbTrackerPlugin.java:22-40` (imports)
- Modify: `src/main/java/com/pbtracker/PbTrackerPlugin.java:69-78` (constants)
- Modify: `src/main/java/com/pbtracker/PbTrackerPlugin.java` (new method, placed near `buildRawPbReport`)

- [ ] **Step 1: Add the new imports**

Current imports (lines 22-24):

```java
import javax.inject.Inject;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
```

Replace with:

```java
import javax.inject.Inject;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
```

Also add the `LinkBrowser` import alongside the other `net.runelite.client.*` imports (after line 16, `import net.runelite.client.plugins.PluginDescriptor;`):

```java
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
```

- [ ] **Step 2: Add the `PROFILE_SITE_URL` constant**

Current constants (lines 77-78, right after `SYNC_STATUS_KEY`):

```java
	private static final String SYNC_STATUS_KEY = "syncStatus";
	private static final String DUMP_RAW_KEY = "dumpRawPbs";
```

Replace with:

```java
	private static final String SYNC_STATUS_KEY = "syncStatus";
	private static final String DUMP_RAW_KEY = "dumpRawPbs";
	private static final String OPEN_PROFILE_KEY = "openProfile";
	private static final String PROFILE_SITE_URL = "https://osrs-pb-tracker-frontend.vercel.app";
```

- [ ] **Step 3: Add `buildProfileUrl` next to `buildRawPbReport`**

Find the end of `buildRawPbReport` (it returns `report.toString();` then closes with `}`), and insert this new method directly after it, before `private void syncPbs(Map<String, Double> pbs)`:

```java
	/**
	 * Pure URL-building for the "open my profile" action - kept static and
	 * side-effect-free (no Client, no LinkBrowser) so it's directly
	 * unit-testable, same pattern as buildRawPbReport.
	 */
	static String buildProfileUrl(String playerName)
	{
		try
		{
			return PROFILE_SITE_URL + "/player/" + URLEncoder.encode(playerName, StandardCharsets.UTF_8.name());
		}
		catch (UnsupportedEncodingException ex)
		{
			// UTF-8 is always supported; this branch is unreachable in practice.
			throw new AssertionError(ex);
		}
	}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home gradle test -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home --tests "com.pbtracker.PbTrackerPluginUnitTest"`
Expected: `BUILD SUCCESSFUL`, all tests pass including `buildsProfileUrlForSimpleName` and `buildsProfileUrlWithUrlEncodedSpaces`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/pbtracker/PbTrackerPlugin.java
git commit -m "Implement buildProfileUrl

Static, pure URL-building for the open-profile action - makes the
tests from the previous commit pass."
```

---

### Task 5: Wire up the `openProfile()` action

**Files:**
- Modify: `src/main/java/com/pbtracker/PbTrackerPlugin.java:207-226` (`onConfigChanged`)
- Modify: `src/main/java/com/pbtracker/PbTrackerPlugin.java` (new `openProfile()` method, placed near `dumpRawPersonalBests()`)

- [ ] **Step 1: Add the branch in `onConfigChanged`**

Current code (lines 207-226):

```java
		if (SETTINGS_GROUP.equals(event.getGroup()))
		{
			// The config panel has no notion of a "button" - toggling a
			// checkbox to trigger an action is the usual RuneLite idiom for
			// this. We deliberately don't reset it back programmatically:
			// the config panel doesn't repaint from a programmatic change
			// until the panel is closed and reopened, which made the box
			// look "stuck" checked. Triggering on either direction of the
			// toggle avoids that entirely - whatever you clicked is what's
			// actually stored, so there's nothing for the UI to get stale on.
			if (SYNC_NOW_KEY.equals(event.getKey()) && shouldTriggerSyncNow(event.getNewValue()))
			{
				executor.execute(this::syncAll);
			}
			else if (DUMP_RAW_KEY.equals(event.getKey()) && shouldTriggerSyncNow(event.getNewValue()))
			{
				executor.execute(this::dumpRawPersonalBests);
			}
			return;
		}
```

Replace with:

```java
		if (SETTINGS_GROUP.equals(event.getGroup()))
		{
			// The config panel has no notion of a "button" - toggling a
			// checkbox to trigger an action is the usual RuneLite idiom for
			// this. We deliberately don't reset it back programmatically:
			// the config panel doesn't repaint from a programmatic change
			// until the panel is closed and reopened, which made the box
			// look "stuck" checked. Triggering on either direction of the
			// toggle avoids that entirely - whatever you clicked is what's
			// actually stored, so there's nothing for the UI to get stale on.
			if (SYNC_NOW_KEY.equals(event.getKey()) && shouldTriggerSyncNow(event.getNewValue()))
			{
				executor.execute(this::syncAll);
			}
			else if (DUMP_RAW_KEY.equals(event.getKey()) && shouldTriggerSyncNow(event.getNewValue()))
			{
				executor.execute(this::dumpRawPersonalBests);
			}
			else if (OPEN_PROFILE_KEY.equals(event.getKey()) && shouldTriggerSyncNow(event.getNewValue()))
			{
				executor.execute(this::openProfile);
			}
			return;
		}
```

- [ ] **Step 2: Add the `openProfile()` method**

Current code where `dumpRawPersonalBests()` closes and `buildRawPbReport`'s javadoc begins:

```java
		String report = buildRawPbReport(raw);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null);
		setStatus("Copied " + raw.size() + " raw PB value(s) to clipboard.");
	}

	/**
	 * Pure formatting of a raw personalbest.* map into a diagnostic report,
```

Replace with (inserting the new method between them):

```java
		String report = buildRawPbReport(raw);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null);
		setStatus("Copied " + raw.size() + " raw PB value(s) to clipboard.");
	}

	/**
	 * Opens the current account's PB tracker profile page in the system
	 * browser - mirrors Wise Old Man's "open my profile" feature.
	 */
	private void openProfile()
	{
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
		{
			setStatus("Not logged in yet - log in, then try again.");
			return;
		}

		String url = buildProfileUrl(client.getLocalPlayer().getName());
		LinkBrowser.browse(url);
	}
```

- [ ] **Step 3: Compile and run the full test suite**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home gradle test -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/pbtracker/PbTrackerPlugin.java
git commit -m "Wire up the open-profile config toggle

Toggling 'Open my profile in browser' now opens the current
account's PB tracker profile page via LinkBrowser, matching the
existing syncNow/dumpRawPbs click-to-trigger idiom."
```

---

### Task 6: Update README documentation

**Files:**
- Modify: `README.md:26-31` (Configuration table)

- [ ] **Step 1: Update the Configuration table**

Current table (lines 26-31):

```markdown
| Setting | Description | Default |
|---|---|---|
| API base URL | Base URL of your PB tracker backend | `https://osrs-pb-tracker-backend.vercel.app` |
| Auto-sync new PBs | Push a PB the moment RuneLite records one | On |
| Sync all PBs on login | Bulk-upload every known PB shortly after login | On |
| Sync all PBs now | Check to trigger an immediate bulk sync. Uncheck and check again to trigger another one | Off |
| Last synced | Display only — shows "Last updated: `<timestamp>`" after each successful sync (or an error/nudge message if something needs attention). Technically an editable text box since RuneLite has no true read-only field type, but it's overwritten on every sync | "Never" |
```

Replace with:

```markdown
| Setting | Description | Default |
|---|---|---|
| API base URL | Base URL of your PB tracker backend. Changing it prompts a Yes/No confirmation, since pointing it at the wrong server means your PBs stop reaching the real leaderboard without any obvious error | `https://osrs-pb-tracker-backend.vercel.app` |
| Auto-sync new PBs | Push a PB the moment RuneLite records one | On |
| Sync all PBs on login | Bulk-upload every known PB shortly after login | On |
| Sync all PBs now | Check to trigger an immediate bulk sync. Uncheck and check again to trigger another one | Off |
| Last synced | Display only — shows "Last updated: `<timestamp>`" after each successful sync (or an error/nudge message if something needs attention). Technically an editable text box since RuneLite has no true read-only field type, but it's overwritten on every sync | "Never" |
| Copy raw PB data to clipboard | Check to copy every raw `personalbest.*` value RuneLite has cached for your account to your clipboard, annotated with whether it would sync as-is - useful for reporting a PB that looks wrong on the site | Off |
| Open my profile in browser | Check to open your PB tracker profile page in your browser | Off |
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "Document the confirmation warning and open-profile toggle

Configuration table was missing the already-shipped 'Copy raw PB
data to clipboard' row too - added while touching this table."
```

---

### Task 7: Manual verification in a real RuneLite client

**Files:** none (manual testing step)

- [ ] **Step 1: Launch the plugin in a real client**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home gradle run -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`

Expected: a RuneLite client window opens with `PbTrackerPlugin is now running` in the console output.

- [ ] **Step 2: Verify the API URL warning**

Log in, open the wrench-icon Configuration panel, search "PB Tracker Sync", and try editing **API base URL**.
Expected: a "Are you sure?" Yes/No dialog appears with the warning text before the change takes effect; choosing "No" leaves the field unchanged.

- [ ] **Step 3: Verify the open-profile toggle**

Toggle **Open my profile in browser**.
Expected: your system's default browser opens to `https://osrs-pb-tracker-frontend.vercel.app/player/<your-display-name>`, and the **Last synced** status field is unaffected (this action doesn't sync anything).

- [ ] **Step 4: Fast-forward the worktree branch**

```bash
cd "../../worktrees/pb-tracker-sync-plugin"
git merge --ff-only plugin-hub-submission
```

Expected: `Fast-forward`, no conflicts (this worktree has been kept in sync with `plugin-hub-submission` throughout this project - see prior session history).

- [ ] **Step 5: Push to origin**

Only do this once the user confirms manual testing looks correct.

```bash
cd "../../osrs-pb-tracker/plugin"
git push origin plugin-hub-submission
```

Expected: push succeeds. This updates the real Plugin Hub repo (`0xJeu/pb-tracker-sync`) but does not affect the already-merged Plugin Hub submission (which is pinned to a specific commit hash, not this branch) - see `docs/superpowers/specs/2026-07-06-profile-link-and-hidden-api-url-design.md` Background section for why that's safe.
