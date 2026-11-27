package com.hambalapps.agymobile.data.engine

import com.hambalapps.agymobile.data.acp.ModelQuotaInfo
import com.hambalapps.agymobile.data.acp.QuotaBucketInfo
import com.hambalapps.agymobile.data.acp.QuotaSummaryResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Queries Antigravity / Google Cloud Code quota endpoints directly from Android using the
 * local OAuth token stored by `agy`, enabling live quota display in Standalone mode.
 */
class QuotaClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val effortSuffixRegex = Regex("-(minimal|low|medium|high|xhigh|none|tiered|sonar)$", RegexOption.IGNORE_CASE)

    suspend fun fetchQuota(homeDir: File): QuotaSummaryResponse = withContext(Dispatchers.IO) {
        try {
            val tokenFile = File(homeDir, ".gemini/antigravity-cli/antigravity-oauth-token")
            if (!tokenFile.exists()) {
                return@withContext QuotaSummaryResponse(
                    ok = false,
                    error = "Not signed in yet — authenticate with Google to see model quotas."
                )
            }

            val outer = JSONObject(tokenFile.readText())
            val email = outer.optString("id_token_email").takeIf { it.isNotBlank() }
            val authMethod = outer.optString("auth_method").takeIf { it.isNotBlank() }
            val tokObj = when (val rawTok = outer.opt("token")) {
                is JSONObject -> rawTok
                is String -> JSONObject(rawTok)
                else -> null
            }
            val accessToken = tokObj?.optString("access_token").orEmpty()
            if (accessToken.isBlank()) {
                return@withContext QuotaSummaryResponse(
                    ok = false,
                    email = email,
                    authMethod = authMethod,
                    error = "No access token found. Sign in to Antigravity first."
                )
            }

            coroutineScope {
                val modelsDef = async { postEndpoint("fetchAvailableModels", accessToken) }
                val summaryDef = async { postEndpoint("retrieveUserQuotaSummary", accessToken) }
                val modelsResp = modelsDef.await()
                val summaryResp = summaryDef.await()

                if (modelsResp.first == 401 || summaryResp.first == 401) {
                    return@coroutineScope QuotaSummaryResponse(
                        ok = false,
                        email = email,
                        authMethod = authMethod,
                        error = "OAuth token expired (HTTP 401). Start or send a message to refresh."
                    )
                }
                if (modelsResp.first == 403 && summaryResp.first == 403) {
                    return@coroutineScope QuotaSummaryResponse(
                        ok = false,
                        email = email,
                        authMethod = authMethod,
                        error = "Quota endpoint returned HTTP 403 (check network/VPN access)."
                    )
                }

                val modelsJson = modelsResp.second?.let { runCatching { JSONObject(it) }.getOrNull() }
                val summaryJson = summaryResp.second?.let { runCatching { JSONObject(it) }.getOrNull() }
                val errorMsg = if (modelsJson == null && summaryJson == null) {
                    "Quota HTTP ${modelsResp.first}/${summaryResp.first}"
                } else null

                parseQuotaPayloads(
                    modelsJson = modelsJson,
                    summaryJson = summaryJson,
                    email = email,
                    authMethod = authMethod,
                    error = errorMsg
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (err: Throwable) {
            QuotaSummaryResponse(
                ok = false,
                error = err.message ?: "Failed to load quota"
            )
        }
    }

    private fun postEndpoint(endpoint: String, accessToken: String): Pair<Int, String?> {
        return runCatching {
            val req = Request.Builder()
                .url("https://daily-cloudcode-pa.googleapis.com/v1internal:$endpoint")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("User-Agent", "antigravity")
                .post("{}".toRequestBody(jsonMedia))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                resp.code to if (resp.isSuccessful) body else null
            }
        }.getOrElse { 0 to null }
    }

    internal fun parseQuotaPayloads(
        modelsJson: JSONObject?,
        summaryJson: JSONObject?,
        email: String? = null,
        authMethod: String? = null,
        error: String? = null
    ): QuotaSummaryResponse {
        val bucketMap = LinkedHashMap<String, QuotaBucketInfo>()

        fun addBuckets(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val bucketId = obj.optString("bucketId").ifBlank { obj.optString("displayName") }
                val displayName = obj.optString("displayName").ifBlank { bucketId }
                if (bucketId.isBlank() && displayName.isBlank()) continue
                if (bucketMap.containsKey(bucketId)) continue

                val disabled = obj.optBoolean("disabled", false)
                val resetTime = obj.optString("resetTime").takeIf { it.isNotBlank() }
                val rawFrac = obj.optDouble("remainingFraction", Double.NaN)
                val remainingFraction = when {
                    !rawFrac.isNaN() -> rawFrac
                    resetTime != null && !disabled -> 0.0
                    else -> null
                }
                val rawAmt = obj.optDouble("remainingAmount", Double.NaN)
                val remainingAmount = if (!rawAmt.isNaN()) rawAmt else null

                bucketMap[bucketId] = QuotaBucketInfo(
                    bucketId = bucketId,
                    displayName = displayName,
                    description = obj.optString("description").takeIf { it.isNotBlank() },
                    window = obj.optString("window").takeIf { it.isNotBlank() },
                    remainingFraction = remainingFraction,
                    remainingAmount = remainingAmount,
                    resetTime = resetTime,
                    disabled = disabled
                )
            }
        }

        if (summaryJson != null) {
            addBuckets(summaryJson.optJSONArray("buckets"))
            val groups = summaryJson.optJSONArray("groups")
            if (groups != null) {
                for (i in 0 until groups.length()) {
                    val grp = groups.optJSONObject(i) ?: continue
                    addBuckets(grp.optJSONArray("buckets"))
                }
            }
        }

        val modelsMap = LinkedHashMap<String, ModelQuotaInfo>()
        val rawModels = modelsJson?.optJSONObject("models")
        if (rawModels != null) {
            val keys = rawModels.keys()
            while (keys.hasNext()) {
                val modelKey = keys.next()
                val details = rawModels.optJSONObject(modelKey) ?: continue
                val disabled = details.optBoolean("disabled", false)
                val quotaObj = details.optJSONObject("quotaInfo")
                val beyondObj = details.optJSONObject("beyondModelInfo")
                val resetTime = quotaObj?.optString("resetTime")?.takeIf { it.isNotBlank() }
                val rawFrac = quotaObj?.optDouble("remainingFraction", Double.NaN) ?: Double.NaN
                val remainingFraction = when {
                    !rawFrac.isNaN() -> rawFrac
                    quotaObj != null && resetTime != null && !disabled -> 0.0
                    else -> null
                }

                val entry = ModelQuotaInfo(
                    modelId = modelKey,
                    displayName = details.optString("displayName").takeIf { it.isNotBlank() },
                    remainingFraction = remainingFraction,
                    resetTime = resetTime,
                    quotaBucketName = beyondObj?.optString("quotaBucketName")?.takeIf { it.isNotBlank() },
                    disabled = disabled
                )
                modelsMap[modelKey] = entry
                val lower = modelKey.lowercase()
                modelsMap.putIfAbsent(lower, entry)
                val baseFamily = effortSuffixRegex.replace(lower, "")
                if (baseFamily.isNotBlank()) {
                    modelsMap.putIfAbsent(baseFamily, entry)
                }
            }
        }

        val tieredObj = modelsJson?.optJSONObject("tieredModelIds")
        val preferredTierIds = buildList {
            tieredObj?.optJSONArray("pro")?.let { arr ->
                for (i in 0 until arr.length()) add(arr.optString(i))
            }
            tieredObj?.optJSONArray("flash")?.let { arr ->
                for (i in 0 until arr.length()) add(arr.optString(i))
            }
            add("gemini-3.8-flash-tiered")
            add("gemini-4-hydrogen-sonar")
            add("gemini-3-flash")
            add("gemini-2.5-pro")
        }
        val geminiPoolEntry = preferredTierIds
            .mapNotNull { modelsMap[it] }
            .firstOrNull { it.remainingFraction != null }
            ?: modelsMap.values.firstOrNull {
                it.modelId.startsWith("gemini-", ignoreCase = true) && it.remainingFraction != null
            }

        if (geminiPoolEntry != null) {
            modelsMap["__gemini_pool__"] = geminiPoolEntry
            if (!bucketMap.containsKey("gemini-models")) {
                bucketMap["gemini-models"] = QuotaBucketInfo(
                    bucketId = "gemini-models",
                    displayName = "Gemini Models",
                    window = "1P Pool",
                    remainingFraction = geminiPoolEntry.remainingFraction,
                    resetTime = geminiPoolEntry.resetTime,
                    disabled = geminiPoolEntry.disabled
                )
            }
        }

        val buckets = bucketMap.values.toList()
        return QuotaSummaryResponse(
            ok = error == null && (buckets.isNotEmpty() || modelsMap.isNotEmpty()),
            email = email,
            authMethod = authMethod,
            buckets = buckets,
            models = modelsMap,
            error = error
        )
    }
}
