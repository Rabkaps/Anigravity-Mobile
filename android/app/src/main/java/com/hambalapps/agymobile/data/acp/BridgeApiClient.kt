package com.hambalapps.agymobile.data.acp

import com.hambalapps.agymobile.data.local.ConnectionProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
private data class SessionsEnvelope(val sessions: List<SessionSummary> = emptyList())

@Serializable
private data class ConfigEnvelope(val settings: JsonObject = JsonObject(emptyMap()))

class BridgeApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun getHealth(profile: ConnectionProfile): Result<HealthResponse> =
        executeGet(profile, "/health") { body ->
            json.decodeFromString<HealthResponse>(body)
        }

    suspend fun getSessions(profile: ConnectionProfile): Result<List<SessionSummary>> =
        executeGet(profile, "/api/sessions") { body ->
            json.decodeFromString<SessionsEnvelope>(body).sessions
        }

    suspend fun getDiff(profile: ConnectionProfile, cwd: String? = null): Result<DiffResponse> {
        val query = if (!cwd.isNullOrBlank()) "?cwd=${java.net.URLEncoder.encode(cwd, "UTF-8")}" else ""
        return executeGet(profile, "/api/diff$query") { body ->
            json.decodeFromString<DiffResponse>(body)
        }
    }

    suspend fun getQuota(profile: ConnectionProfile): Result<QuotaSummaryResponse> =
        executeGet(profile, "/api/quota") { body ->
            json.decodeFromString<QuotaSummaryResponse>(body)
        }

    suspend fun getConfig(profile: ConnectionProfile): Result<JsonObject> =
        executeGet(profile, "/api/config") { body ->
            json.decodeFromString<ConfigEnvelope>(body).settings
        }

    suspend fun patchConfig(profile: ConnectionProfile, patch: JsonObject): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("${profile.httpBaseUrl()}/api/config")
                    .applyAuth(profile)
                    .patch(patch.toString().toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code}: $raw")
                    json.decodeFromString<ConfigEnvelope>(raw).settings
                }
            }
        }

    suspend fun setEnvAndRestart(profile: ConnectionProfile, envPatch: JsonObject): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("${profile.httpBaseUrl()}/api/env")
                    .applyAuth(profile)
                    .post(envPatch.toString().toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}: ${resp.body?.string().orEmpty()}")
                    }
                }
            }
        }

    suspend fun restartAgy(profile: ConnectionProfile): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("${profile.httpBaseUrl()}/api/restart")
                    .applyAuth(profile)
                    .post("{}".toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}: ${resp.body?.string().orEmpty()}")
                    }
                }
            }
        }

    private suspend fun <T> executeGet(
        profile: ConnectionProfile,
        path: String,
        parse: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${profile.httpBaseUrl()}$path")
                .applyAuth(profile)
                .get()
                .build()
            Result.success(
                httpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code}: $raw")
                    parse(raw)
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun Request.Builder.applyAuth(profile: ConnectionProfile): Request.Builder = apply {
        if (profile.token.isNotBlank()) {
            header("Authorization", "Bearer ${profile.token.trim()}")
        }
    }
}
