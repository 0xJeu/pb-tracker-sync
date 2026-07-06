# Hide API URL + Open-Profile Button — Design

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

## Scope

**In scope:**
- Hiding the `apiBaseUrl` config field from the Configuration panel.
- A new "Open my profile in browser" toggle in the Configuration panel that
  opens the current account's PB tracker profile page.

**Out of scope:**
- A real sidebar panel/toolbar button. RuneLite's `ConfigItem` annotation
  has no button or clickable-link type (confirmed by inspecting the actual
  class: `position/keyName/name/description/hidden/warning/secret/section`
  only) - a first-class button would require a new `PluginPanel` and
  `NavigationButton`, which is a real architectural addition this plugin
  has deliberately avoided so far. Explicitly decided against for now.
- Making `apiBaseUrl` fully unremovable/un-editable. `hidden = true` only
  affects whether RuneLite renders the field in the Configuration panel;
  the value is still readable/writable via RuneLite's config export/import
  if a future support case ever needs it.
- Any backend/frontend change.

## Design

**Hiding the API URL:** add `hidden = true` to `apiBaseUrl`'s existing
`@ConfigItem` annotation in `PbTrackerConfig.java`. No other change - the
field keeps its current default (the real hosted backend,
`https://osrs-pb-tracker-backend.vercel.app`), and all existing code that
reads `config.apiBaseUrl()` is unaffected.

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
  RuneLite's own utility for launching a URL in the system browser, already
  used elsewhere in the RuneLite plugin ecosystem for exactly this purpose.

**New constant:** `PROFILE_SITE_URL = "https://osrs-pb-tracker-frontend.vercel.app"`,
hardcoded rather than a config field - same reasoning as hiding
`apiBaseUrl`: there's no legitimate reason for a user to need to change it,
so don't give them a place to.

## Testing

Unit test `buildProfileUrl` with a few player names, including one with a
space (e.g. `"Blitzen Jones"`) to confirm URL-encoding, asserting the exact
expected URL string. No integration test needed for the actual browser
launch (thin `LinkBrowser` call, same trust level as the existing untested
`setStatus`/`ConfigManager` calls elsewhere in the plugin).
