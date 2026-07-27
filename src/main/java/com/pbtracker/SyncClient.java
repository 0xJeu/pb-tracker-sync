package com.pbtracker;

import com.google.gson.Gson;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin wrapper around RuneLite's shared OkHttpClient that talks to the PB
 * tracker backend: POSTs a player's personal bests, and (for the !pbr chat
 * command) does a blocking GET lookup of a player's synced PBs.
 */
class SyncClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final PbTrackerConfig config;

	@Inject
	SyncClient(OkHttpClient httpClient, Gson gson, PbTrackerConfig config)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.config = config;
	}

	void sync(String accountHash, String displayName, Map<String, Double> pbs, String installSecret, Callback callback)
	{
		if (pbs.isEmpty())
		{
			return;
		}

		SyncPayload payload = new SyncPayload(accountHash, displayName, pbs, installSecret);
		String json = gson.toJson(payload);

		String base = config.apiBaseUrl() == null ? "" : config.apiBaseUrl().replaceAll("/+$", "");
		String url = base + "/api/sync";

		Request request = new Request.Builder()
			.url(url)
			.post(RequestBody.create(JSON, json))
			.build();

		httpClient.newCall(request).enqueue(callback);
	}

	/**
	 * Blocking GET of a player's synced PBs, for the !pbr chat command.
	 * Safe to call directly from a registerCommandAsync handler - those
	 * already run off the client thread, the same way RuneLite's own
	 * built-in hiscore-lookup commands (e.g. !lvl) make a blocking network
	 * call directly rather than needing their own executor/callback.
	 */
	PlayerLookupResult lookupPlayer(String displayName)
	{
		String base = config.apiBaseUrl() == null ? "" : config.apiBaseUrl().replaceAll("/+$", "");
		String encodedName = URLEncoder.encode(displayName, StandardCharsets.UTF_8).replace("+", "%20");
		String url = base + "/api/players/" + encodedName;

		Request request = new Request.Builder().url(url).get().build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 404)
			{
				return PlayerLookupResult.notFound();
			}
			if (!response.isSuccessful())
			{
				return PlayerLookupResult.error();
			}

			ResponseBody responseBody = response.body();
			String body = responseBody == null ? null : responseBody.string();
			if (body == null)
			{
				return PlayerLookupResult.error();
			}

			PlayerLookupResponse parsed = gson.fromJson(body, PlayerLookupResponse.class);
			if (parsed == null)
			{
				return PlayerLookupResult.error();
			}
			if (Boolean.TRUE.equals(parsed.ambiguous))
			{
				return PlayerLookupResult.ambiguous();
			}
			if (parsed.pbs != null)
			{
				parsed.pbs = parsed.pbs.stream()
					.filter(pb -> TrackedBosses.isTracked(pb.boss))
					.collect(Collectors.toList());
			}

			return PlayerLookupResult.found(parsed);
		}
		catch (IOException | RuntimeException e)
		{
			return PlayerLookupResult.error();
		}
	}

	/** Blocking GET of every tracked boss key - for the side panel's boss picker. Returns an empty list on any failure. */
	List<String> getBosses()
	{
		String base = config.apiBaseUrl() == null ? "" : config.apiBaseUrl().replaceAll("/+$", "");
		Request request = new Request.Builder().url(base + "/api/bosses").get().build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				return java.util.Collections.emptyList();
			}
			ResponseBody responseBody = response.body();
			String body = responseBody == null ? null : responseBody.string();
			if (body == null)
			{
				return java.util.Collections.emptyList();
			}
			String[] bosses = gson.fromJson(body, String[].class);
			if (bosses == null)
			{
				return java.util.Collections.emptyList();
			}
			return java.util.Arrays.stream(bosses)
				.filter(TrackedBosses::isTracked)
				.collect(Collectors.toList());
		}
		catch (IOException | RuntimeException e)
		{
			return java.util.Collections.emptyList();
		}
	}

	/** Blocking GET of a boss's leaderboard - for the side panel's Bosses tab. Returns null on failure. */
	List<LeaderboardRow> getLeaderboard(String boss, int limit, String highlight)
	{
		String base = config.apiBaseUrl() == null ? "" : config.apiBaseUrl().replaceAll("/+$", "");
		Request request = new Request.Builder().url(buildLeaderboardUrl(base, boss, limit, highlight)).get().build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				return null;
			}
			ResponseBody responseBody = response.body();
			String body = responseBody == null ? null : responseBody.string();
			if (body == null)
			{
				return null;
			}
			LeaderboardRow[] rows = gson.fromJson(body, LeaderboardRow[].class);
			return rows == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(rows);
		}
		catch (IOException | RuntimeException e)
		{
			return null;
		}
	}

	static String buildLeaderboardUrl(String base, String boss, int limit, String highlight)
	{
		String normalizedBase = base == null ? "" : base.replaceAll("/+$", "");
		String encodedBoss = URLEncoder.encode(boss, StandardCharsets.UTF_8).replace("+", "%20");
		StringBuilder url = new StringBuilder(normalizedBase)
			.append("/api/leaderboard/")
			.append(encodedBoss)
			.append("?limit=")
			.append(limit);
		if (highlight != null && !highlight.trim().isEmpty())
		{
			url.append("&highlight=")
				.append(URLEncoder.encode(highlight, StandardCharsets.UTF_8).replace("+", "%20"));
		}
		return url.toString();
	}

	static class LeaderboardRow
	{
		String displayName;
		double timeSeconds;
		String updatedAt;
	}

	static class SyncResponseDto
	{
		Integer updated;
	}

	/** Best-effort parse of the sync response body; returns an all-null result on any parse failure so callers fail safe (no forced refresh, no crash). */
	SyncResponseDto parseSyncResponse(Response response)
	{
		try
		{
			ResponseBody body = response.body();
			String json = body == null ? null : body.string();
			if (json == null)
			{
				return new SyncResponseDto();
			}
			SyncResponseDto parsed = gson.fromJson(json, SyncResponseDto.class);
			return parsed == null ? new SyncResponseDto() : parsed;
		}
		catch (IOException | RuntimeException e)
		{
			return new SyncResponseDto();
		}
	}

	private static class SyncPayload
	{
		final String accountHash;
		final String displayName;
		final Map<String, Double> pbs;
		final String installSecret;

		SyncPayload(String accountHash, String displayName, Map<String, Double> pbs, String installSecret)
		{
			this.accountHash = accountHash;
			this.displayName = displayName;
			this.pbs = pbs;
			this.installSecret = installSecret;
		}
	}

	/**
	 * Mirrors the backend's GET /api/players/:name response shape. `pbs` is
	 * null/absent on an ambiguous match (the endpoint returns `matches`
	 * instead, which the !pbr command has no use for and doesn't parse).
	 */
	static class PlayerLookupResponse
	{
		Integer id;
		String displayName;
		String updatedAt;
		List<PbEntryDto> pbs;
		Boolean ambiguous;
	}

	static class PbEntryDto
	{
		String boss;
		double timeSeconds;
		String updatedAt;
		int rank;
	}

	enum LookupKind
	{
		FOUND,
		NOT_FOUND,
		AMBIGUOUS,
		ERROR
	}

	/**
	 * Small result type so callers (the !pbr command) can branch on exactly
	 * why a lookup didn't produce PB data, instead of collapsing 404/
	 * ambiguous/network-failure into a single null.
	 */
	static class PlayerLookupResult
	{
		final LookupKind kind;
		final PlayerLookupResponse player;

		private PlayerLookupResult(LookupKind kind, PlayerLookupResponse player)
		{
			this.kind = kind;
			this.player = player;
		}

		static PlayerLookupResult found(PlayerLookupResponse player)
		{
			return new PlayerLookupResult(LookupKind.FOUND, player);
		}

		static PlayerLookupResult notFound()
		{
			return new PlayerLookupResult(LookupKind.NOT_FOUND, null);
		}

		static PlayerLookupResult ambiguous()
		{
			return new PlayerLookupResult(LookupKind.AMBIGUOUS, null);
		}

		static PlayerLookupResult error()
		{
			return new PlayerLookupResult(LookupKind.ERROR, null);
		}
	}
}
