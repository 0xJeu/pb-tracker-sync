package com.pbtracker;

import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Pure unit tests for SyncClient.parseSyncResponse - only the Gson field is
 * exercised, so httpClient/config can be left null in the constructor.
 */
public class SyncClientTest
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private static SyncClient newClient()
	{
		return new SyncClient(null, new Gson(), null);
	}

	private static Response responseWithBody(String body)
	{
		Request request = new Request.Builder().url("https://api.example/api/sync").build();
		return new Response.Builder()
			.request(request)
			.protocol(Protocol.HTTP_1_1)
			.code(200)
			.message("OK")
			.body(body == null ? null : ResponseBody.create(JSON, body))
			.build();
	}

	@Test
	public void parsesUpdatedCountFromAWellFormedResponse()
	{
		SyncClient.SyncResponseDto dto = newClient().parseSyncResponse(
			responseWithBody("{\"ok\":true,\"playerId\":123,\"received\":5,\"updated\":2,\"metadataChanged\":true,\"deduplicated\":false}"));

		assertNotNull(dto.updated);
		org.junit.Assert.assertEquals(2, dto.updated.intValue());
		org.junit.Assert.assertEquals(Boolean.TRUE, dto.metadataChanged);
		org.junit.Assert.assertEquals(Boolean.FALSE, dto.deduplicated);
	}

	@Test
	public void malformedNonJsonBodyFailsSafeInsteadOfThrowing()
	{
		SyncClient.SyncResponseDto dto = newClient().parseSyncResponse(
			responseWithBody("<html>not json</html>"));

		assertNotNull(dto);
		assertNull(dto.updated);
	}

	@Test
	public void missingBodyFailsSafe()
	{
		SyncClient.SyncResponseDto dto = newClient().parseSyncResponse(responseWithBody(null));

		assertNotNull(dto);
		assertNull(dto.updated);
	}
}
