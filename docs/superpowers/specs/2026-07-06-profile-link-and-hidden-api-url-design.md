# API URL Confirmation + Open-Profile Button — Design

## Goal

Prevent a player from accidentally breaking their sync by editing the API
base URL config field, and give them a one-click way to open their own PB
tracker profile in a browser - matching a feature George noticed on the
Wise Old Man plugin.

## Background

George reported he'd accidentally been running against the wrong API URL
for a while without noticing (a stale/different `apiBaseUrl` value), and
asked whether we could hide that field so it can't be changed by accident,
plus add a way to jump straight to your own profile page like Wise Old
Man's plugin does.

The initial idea was to hide `apiBaseUrl` from the Configuration panel
entirely (`hidden = true`). That was dropped: the README documents two real
workflows that depend on this field being visible and editable - pointing
it at `localhost` for local development, and self-hosters pointing it at
their own backend for privacy - and RuneLite has no config export/import
feature to fall back on for a hidden field (checked the actual RuneLite
source for `ConfigPanel`/`PluginListPanel`/`TopLevelConfigPanel` - no such
feature exists). The only real fallback would be hand-editing RuneLite's
settings file on disk, which is a worse workflow than what exists today.

## Scope

**In scope:**
- Adding a confirmation prompt to `apiBaseUrl` so it can't be changed
  without an explicit "yes" - addresses George's actual concern (accidental
  edit) without removing the field or breaking the documented dev/self-host
  workflows.
- A new "Open my profile in browser" toggle in the Configuration panel that
  opens the current account's PB tracker profile page.

**Out of scope:**
- Hiding or otherwise removing `apiBaseUrl` from the Configuration panel
  (see Background above for why).
- A real sidebar panel/toolbar button for "open my profile" or anything
  else. RuneLite's `ConfigItem` annotation has no button or clickable-link
  type (confirmed by inspecting the actual class:
  `position/keyName/name/description/hidden/warning/secret/section` only) -
  a first-class button would require a new `PluginPanel` and
  `NavigationButton`, a real architectural addition this plugin has
  deliberately avoided so far. There's a real case for building this as a
  bigger follow-up (consolidating `syncNow`/`dumpRawPbs`/this into real
  buttons, plus a genuinely read-only API URL display), but that's its own
  separate brainstorm/spec, not part of this change.
- Any backend/frontend change.

## Design

**Confirming API URL changes:** add a `warning` string to `apiBaseUrl`'s
existing `@ConfigItem` annotation in `PbTrackerConfig.java`. Confirmed
against RuneLite's actual `ConfigPanel` source: a non-empty `warning()`
triggers a `JOptionPane` Yes/No confirmation dialog (defaulting focus to
"No") before the change commits; declining reverts the field. No other
change - the field stays visible, keeps its current default (the real
hosted backend, `https://osrs-pb-tracker-backend.vercel.app`), and all
existing code reading `config.apiBaseUrl()` is unaffected.

```java
@ConfigItem(
    keyName = "apiBaseUrl",
    name = "API base URL",
    description = "Base URL of your PB tracker backend, e.g. https://osrs-pb-tracker-backend.vercel.app",
    warning = "Changing this will send your PB data to a different server instead of the default "
        + "PB Tracker leaderboard. Only change this if you're running your own backend or testing locally.",
    position = 0
)
```

**Opening the profile:** a new hidden-effect boolean config item,
`openProfile` ("Open my profile in browser"), added the same way as the
existing `syncNow`/`dumpRawPbs` toggles - no persisted meaning of its own,
just a click-to-trigger idiom (RuneLite's config panel has no native button
widget, so this is the standard workaround, already used twice in this
plugin).

`PbTrackerPlugin.onConfigChanged`'s `SETTINGS_GROUP` branch gets a third
case alongside `SYNC_NOW_KEY`/`DUMP_RAW_KEY`:

```java
else if (OPEN_PROFILE_KEY.equals(event.getKey()) && shouldTriggerSyncNow(event.getNewValue()))
{
    executor.execute(this::openProfile);
}
```

`openProfile()`:
- Reads the player's name the same way `syncPbs()` already does
  (`client.getLocalPlayer().getName()`), so it opens *this* account's own
  profile rather than requiring the player to type anything.
- If not logged in yet (`client.getLocalPlayer()` is null), sets the
  existing status field to `"Not logged in yet - log in, then try again."`,
  matching the guard already used by `syncAll()`/`dumpRawPersonalBests()`.
- Builds the URL via a new **static, pure** helper,
  `static String buildProfileUrl(String playerName)`, returning
  `PROFILE_SITE_URL + "/player/" + URLEncoder.encode(playerName, UTF_8)` -
  matching the frontend's real path-based routing (`/player/:name`).
  Kept static/pure (no `Client`/AWT) so it's directly unit-testable, same
  pattern as `buildRawPbReport`.
- Opens the URL via `net.runelite.client.util.LinkBrowser.browse(url)` -
  confirmed to exist in the actual RuneLite client jar
  (`public static void browse(String)`) - RuneLite's own utility for
  launching a URL in the system browser.

**New constant:** `PROFILE_SITE_URL = "https://osrs-pb-tracker-frontend.vercel.app"`,
hardcoded rather than a config field - there's no legitimate reason for a
user to need to point this at a different site.

## Testing

Unit test `buildProfileUrl` with a few player names, including one with a
space (e.g. `"Blitzen Jones"`) to confirm URL-encoding, asserting the exact
expected URL string. No integration test needed for the actual browser
launch (thin `LinkBrowser` call, same trust level as the existing untested
`setStatus`/`ConfigManager` calls elsewhere in the plugin), and none needed
for the `warning` confirmation dialog (RuneLite's own `ConfigPanel` code,
not ours).
