# PB Tracker Sync

A RuneLite plugin that reads the boss personal best times RuneLite's built-in
Chat Commands plugin already tracks (config group `personalbest`) and syncs
them to a custom PB leaderboard backend, so they can be looked up by player
name or browsed as a leaderboard on a companion website.

## Features

- **Live sync** — the moment you get a new PB in-game, it's pushed
  immediately.
- **Bulk sync** — on login, and via the "Sync all PBs now" checkbox in the
  plugin's own Configuration panel, every personal best RuneLite already has
  on record is uploaded in one go.
- **Adventure Log parsing** — raid/team-size records (e.g. Theatre of Blood
  "Room time" vs "Overall time" vs team size) are ambiguous in RuneLite's raw
  config, so the plugin also reads your in-game Adventure Log → Counters page
  directly to label these distinctly (e.g. "Theatre of Blood - Fastest
  Room (3 player)").

There's no separate sidebar panel — everything lives in one place: the
plugin's Configuration screen (wrench icon → search "PB Tracker Sync").

## Configuration

| Setting | Description | Default |
|---|---|---|
| API base URL | Base URL of your PB tracker backend | `https://osrs-pb-tracker-backend.vercel.app` |
| Auto-sync new PBs | Push a PB the moment RuneLite records one | On |
| Sync all PBs on login | Bulk-upload every known PB shortly after login | On |
| Sync all PBs now | Check to trigger an immediate bulk sync. Uncheck and check again to trigger another one | Off |
| Last synced | Display only — shows "Last updated: `<timestamp>`" after each successful sync (or an error/nudge message if something needs attention). Technically an editable text box since RuneLite has no true read-only field type, but it's overwritten on every sync | "Never" |

## Building / testing locally

The plugin defaults to the hosted PB tracker backend. For local development,
point **API base URL** at whatever PB tracker backend you're running.

Prerequisites:

- JDK 11+
- Gradle

Run the plugin in a local RuneLite client:

```bash
gradle run
```

The `run` task compiles the plugin and launches RuneLite in developer mode
with `PB Tracker Sync` pre-loaded. In RuneLite, open the Configuration panel,
search for `PB Tracker Sync`, and set **API base URL** to your backend before
testing sync behavior.

## Privacy note

This plugin sends your account hash (RuneLite's stable per-account
identifier, not your password or any Jagex credential), your current
in-game display name, and your synced boss personal-best times to whatever
server is configured under **API base URL**.

By default that's `https://osrs-pb-tracker-backend.vercel.app`, a shared
community leaderboard server operated by the plugin's author — not an
official Jagex or RuneLite service. Your data is used to populate that
public leaderboard, viewable by anyone. If you'd rather not share your data
with that server, point **API base URL** at your own self-hosted backend
(see "Building / testing locally" above) or a different one you trust.
