package com.hambalapps.agymobile.data.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class MarketplaceCatalogItem(
    val slug: String,
    val displayName: String,
    val publisher: String,
    val shelf: String,
    val description: String,
    val marketplace: String
) {
    val installSpec: String get() = "$slug@$marketplace"
    val isOfficial: Boolean get() = marketplace == "antigravity-plugins-official"
}

data class InstalledPluginInfo(
    val name: String,
    val dirName: String,
    val enabled: Boolean,
    val version: String = "",
    val source: String = "",
    val path: String = "",
    val skills: List<String> = emptyList(),
    val agents: List<String> = emptyList(),
    val rulesCount: Int = 0,
    val mcpServers: List<String> = emptyList(),
    val hasHooks: Boolean = false
)

data class SkillItemInfo(
    val name: String,
    val scope: String,
    val path: String,
    val content: String
)

/**
 * Manages Antigravity Marketplace catalog discovery, `agy plugin` CLI operations,
 * global/workspace Rules (`GEMINI.md`), and custom Skills (`SKILL.md`).
 */
class PluginMarketplaceManager(
    private val engineManager: StandaloneEngineManager,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    @Volatile
    private var cachedCatalog: List<MarketplaceCatalogItem>? = null

    suspend fun fetchCatalog(forceRefresh: Boolean = false): List<MarketplaceCatalogItem> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                cachedCatalog?.let { return@withContext it }
            }
            val live = runCatching {
                val req = Request.Builder()
                    .url("https://antigravity.google/docs/marketplace.md")
                    .header("User-Agent", "AgyMobile/1.5.0")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) emptyList()
                    else parseMarketplaceMarkdown(resp.body?.string().orEmpty())
                }
            }.getOrDefault(emptyList())

            val merged = (live + builtInCatalog)
                .distinctBy { it.installSpec.lowercase() }
            if (live.isNotEmpty()) {
                cachedCatalog = merged
            }
            merged
        }

    suspend fun listInstalledPlugins(workspaceCwd: String): List<InstalledPluginInfo> =
        withContext(Dispatchers.IO) {
            val fromCli = if (engineManager.isAgyInstalled()) {
                runCatching {
                    val res = runAgyCli(listOf("plugin", "list", "--json"), timeoutSec = 20L, cwd = workspaceCwd)
                    if (res.first == 0) parsePluginListJson(res.second) else emptyList()
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            if (fromCli.isNotEmpty()) {
                return@withContext fromCli
            }

            // Fallback: scan ~/.gemini/config/plugins and <cwd>/.agents/plugins
            val dirs = listOf(
                File(engineManager.homeDir, ".gemini/config/plugins"),
                File(workspaceCwd, ".agents/plugins")
            )
            val discovered = ArrayList<InstalledPluginInfo>()
            for (root in dirs) {
                if (!root.isDirectory) continue
                root.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
                    val manifestFile = File(pluginDir, "plugin.json")
                        .takeIf { it.exists() }
                        ?: File(pluginDir, ".antigravity-plugin/plugin.json").takeIf { it.exists() }
                    val json = manifestFile?.let { f ->
                        runCatching { JSONObject(f.readText()) }.getOrNull()
                    }
                    val name = json?.optString("name")?.takeIf { it.isNotBlank() } ?: pluginDir.name
                    val version = json?.optString("version").orEmpty()
                    val skills = File(pluginDir, "skills").listFiles()
                        ?.filter { it.isDirectory && File(it, "SKILL.md").exists() }
                        ?.map { it.name }
                        .orEmpty()
                    val agents = File(pluginDir, "agents").listFiles()
                        ?.filter { it.isFile && it.extension == "md" }
                        ?.map { it.nameWithoutExtension }
                        .orEmpty()
                    val rulesCount = File(pluginDir, "rules").listFiles()
                        ?.count { it.isFile && it.extension == "md" } ?: 0
                    val mcpServers = runCatching {
                        val mcpFile = File(pluginDir, "mcp_config.json")
                        if (!mcpFile.exists()) emptyList()
                        else JSONObject(mcpFile.readText()).optJSONObject("mcpServers")?.keys()?.asSequence()?.toList().orEmpty()
                    }.getOrDefault(emptyList())
                    val hasHooks = File(pluginDir, "hooks.json").exists()
                    discovered.add(
                        InstalledPluginInfo(
                            name = name,
                            dirName = pluginDir.name,
                            enabled = true,
                            version = version,
                            source = if (root.absolutePath.contains(".agents")) "workspace" else "local",
                            path = pluginDir.absolutePath,
                            skills = skills,
                            agents = agents,
                            rulesCount = rulesCount,
                            mcpServers = mcpServers,
                            hasHooks = hasHooks
                        )
                    )
                }
            }
            discovered.distinctBy { it.name }
        }

    suspend fun installPlugin(specOrUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val target = specOrUrl.trim()
        if (target.isBlank()) return@withContext Result.failure(IllegalArgumentException("Plugin name or URL is empty"))
        if (!engineManager.isAgyInstalled()) {
            return@withContext Result.failure(IllegalStateException("Install Standalone agy engine first."))
        }
        if ((target.startsWith("http://") || target.startsWith("https://")) && !engineManager.isToolchainInstalled()) {
            return@withContext Result.failure(
                IllegalStateException("Installing from a GitHub URL requires the Git & Node/NPM toolchain. Tap Install Git & Node/NPM in Setup first.")
            )
        }
        val (code, out) = runAgyCli(listOf("plugin", "install", target), timeoutSec = 45L)
        if (code == 0) {
            Result.success(out.ifBlank { "Installed $target" })
        } else {
            val friendly = when {
                out.contains("sign in", ignoreCase = true) ||
                    out.contains("auth", ignoreCase = true) ||
                    out.contains("oauth", ignoreCase = true) ->
                    "Sign in to Antigravity first before installing Marketplace plugins.\n$out"
                else -> out.ifBlank { "agy plugin install exited with code $code" }
            }
            Result.failure(IllegalStateException(friendly.trim()))
        }
    }

    suspend fun setPluginEnabled(name: String, enabled: Boolean): Result<String> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.startsWith("-")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid plugin name"))
        }
        if (!engineManager.isAgyInstalled()) {
            return@withContext Result.failure(IllegalStateException("Standalone agy is not installed."))
        }
        val sub = if (enabled) "enable" else "disable"
        val (code, out) = runAgyCli(listOf("plugin", sub, cleanName), timeoutSec = 20L)
        if (code == 0) Result.success(out.ifBlank { "Plugin $cleanName ${sub}d" })
        else Result.failure(IllegalStateException(out.ifBlank { "Failed to $sub $cleanName ($code)" }))
    }

    suspend fun uninstallPlugin(name: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.startsWith("-")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid plugin name"))
        }
        if (!engineManager.isAgyInstalled()) {
            return@withContext Result.failure(IllegalStateException("Standalone agy is not installed."))
        }
        val (code, out) = runAgyCli(listOf("plugin", "uninstall", cleanName), timeoutSec = 20L)
        if (code == 0) Result.success(out.ifBlank { "Uninstalled $cleanName" })
        else Result.failure(IllegalStateException(out.ifBlank { "Failed to uninstall $cleanName ($code)" }))
    }

    fun readGlobalRules(): String {
        val f = File(engineManager.homeDir, ".gemini/config/GEMINI.md")
        return if (f.exists()) runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("") else ""
    }

    fun writeGlobalRules(text: String): Result<Unit> = runCatching {
        val f = File(engineManager.homeDir, ".gemini/config/GEMINI.md")
        f.parentFile?.mkdirs()
        f.writeText(text, Charsets.UTF_8)
    }

    fun readWorkspaceRules(cwd: String): String {
        val f = File(cwd, "GEMINI.md")
        return if (f.exists()) runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("") else ""
    }

    fun writeWorkspaceRules(cwd: String, text: String): Result<Unit> = runCatching {
        val f = File(cwd, "GEMINI.md")
        f.parentFile?.mkdirs()
        f.writeText(text, Charsets.UTF_8)
    }

    fun listSkills(cwd: String): List<SkillItemInfo> {
        val roots = listOf(
            "Global" to File(engineManager.homeDir, ".gemini/config/skills"),
            "Workspace" to File(cwd, ".agents/skills")
        )
        val items = ArrayList<SkillItemInfo>()
        for ((scope, dir) in roots) {
            if (!dir.isDirectory) continue
            dir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
                val skillFile = File(skillDir, "SKILL.md")
                if (skillFile.exists()) {
                    val content = runCatching { skillFile.readText(Charsets.UTF_8) }.getOrDefault("")
                    items.add(
                        SkillItemInfo(
                            name = skillDir.name,
                            scope = scope,
                            path = skillFile.absolutePath,
                            content = content
                        )
                    )
                }
            }
        }
        return items
    }

    fun saveSkill(name: String, content: String, cwd: String, isGlobal: Boolean): Result<Unit> = runCatching {
        val safeName = name.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "-")
        require(safeName.isNotBlank()) { "Skill name cannot be empty" }
        val base = if (isGlobal) {
            File(engineManager.homeDir, ".gemini/config/skills/$safeName/SKILL.md")
        } else {
            File(cwd, ".agents/skills/$safeName/SKILL.md")
        }
        base.parentFile?.mkdirs()
        val finalContent = if (content.trimStart().startsWith("---")) {
            content
        } else {
            "---\nname: $safeName\ndescription: Custom mobile skill\n---\n\n$content"
        }
        base.writeText(finalContent, Charsets.UTF_8)
    }

    private fun runAgyCli(args: List<String>, timeoutSec: Long, cwd: String? = null): Pair<Int, String> {
        val workDir = cwd?.takeIf { it.isNotBlank() && File(it).isDirectory }?.let(::File)
            ?: engineManager.homeDir
        val cmd = engineManager.buildAgyCommand(args)
        val pb = ProcessBuilder(cmd)
            .directory(workDir)
            .redirectErrorStream(true)
        pb.environment().putAll(engineManager.buildChildEnvironment())
        val proc = pb.start()
        val buf = StringBuffer()
        val readerThread = Thread {
            runCatching {
                proc.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val chunk = CharArray(4096)
                    while (true) {
                        val n = reader.read(chunk)
                        if (n <= 0) break
                        if (buf.length < 512 * 1024) {
                            buf.append(chunk, 0, n)
                        }
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            readerThread.join(1_000L)
            return -1 to "Command timed out after ${timeoutSec}s"
        }
        readerThread.join(2_000L)
        val clean = ANSI_REGEX.replace(buf.toString(), "").trim()
        return proc.exitValue() to clean
    }

    private fun parsePluginListJson(raw: String): List<InstalledPluginInfo> {
        val startIdx = raw.indexOf('[')
        val endIdx = raw.lastIndexOf(']')
        if (startIdx < 0 || endIdx <= startIdx) return emptyList()
        val arr = runCatching { JSONArray(raw.substring(startIdx, endIdx + 1)) }.getOrNull() ?: return emptyList()
        val list = ArrayList<InstalledPluginInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name")
            if (name.isBlank()) continue
            val path = obj.optString("path")
            val dirName = path.trimEnd('/').substringAfterLast('/').ifBlank { name }
            list.add(
                InstalledPluginInfo(
                    name = name,
                    dirName = dirName,
                    enabled = obj.optBoolean("enabled", !obj.optBoolean("disabled", false)),
                    version = obj.optString("version"),
                    source = obj.optString("source"),
                    path = path,
                    skills = jsonStringArray(obj.optJSONArray("skills")),
                    agents = jsonStringArray(obj.optJSONArray("agents")),
                    rulesCount = obj.optJSONArray("rules")?.length() ?: 0,
                    mcpServers = jsonStringArray(obj.optJSONArray("mcpServers")),
                    hasHooks = (obj.optJSONArray("hooks")?.length() ?: 0) > 0 || obj.optBoolean("hasHooks", false)
                )
            )
        }
        return list
    }

    private fun jsonStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotBlank()) out.add(s)
        }
        return out
    }

    private fun parseMarketplaceMarkdown(md: String): List<MarketplaceCatalogItem> {
        val sectionIdx = md.indexOf("## Browse Marketplace plugins")
        val body = if (sectionIdx >= 0) md.substring(sectionIdx) else md
        val blocks = body.split("\n### ")
        val items = ArrayList<MarketplaceCatalogItem>()
        for (block in blocks.drop(1)) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            val title = lines.firstOrNull()?.takeIf { !it.startsWith("#") } ?: continue
            val linkMatch = DEEP_LINK_REGEX.find(block) ?: continue
            val marketplace = linkMatch.groupValues[1]
            val slug = linkMatch.groupValues[2]
            val publisher = lines.getOrNull(1)?.takeIf { it == "Google" || it == "Partner" } ?: if (marketplace.contains("official")) "Google" else "Partner"
            val shelf = lines.getOrNull(2)?.takeIf { !it.startsWith("[") && it.length < 48 } ?: "General"
            val description = lines.drop(3).firstOrNull {
                !it.startsWith("[") && !it.startsWith("!") && !it.startsWith("*") && !it.startsWith("#")
            }.orEmpty()
            items.add(
                MarketplaceCatalogItem(
                    slug = slug,
                    displayName = title,
                    publisher = publisher,
                    shelf = shelf,
                    description = description,
                    marketplace = marketplace
                )
            )
        }
        return items
    }

    companion object {
        private val ANSI_REGEX = Regex("\\u001B\\[[0-9;?]*[A-Za-z]")
        private val DEEP_LINK_REGEX = Regex("antigravity://customizations/marketplaces/([^/]+)/plugins/([^\"\\s)]+)")

        val builtInCatalog: List<MarketplaceCatalogItem> = listOf(
            MarketplaceCatalogItem("firebase", "Firebase", "Google", "Build with Google", "Prototype, build & run modern apps with Firebase backend, AI, and operational infrastructure.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("flutter", "Dart and Flutter", "Google", "Build with Google", "Flutter/Dart skills, rules, custom agents, and Dart MCP server for mobile, web, and desktop.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("android-cli-plugin", "Android CLI", "Google", "Build with Google", "Core tools and knowledge required to develop and debug Android apps.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("chrome-devtools-plugin", "Chrome DevTools", "Google", "Build with Google", "Reliable automation, in-depth debugging, and performance analysis in Chrome.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("gemini-api", "Gemini API", "Google", "Build with Google", "Build apps with Gemini Interactions API, Live API, and multimodal generation.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("modern-web-guidance-plugin", "Modern Web Guidance", "Google", "Build with Google", "Keep your coding agent up to date with the latest web platform best practices.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("google-cloud-developer", "Google Cloud Developer", "Google", "Build with Google", "Google Cloud guidance for coding agents: gcloud validation, IAM, and project onboarding.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("google-developer-knowledge", "Google Developer Knowledge", "Google", "Build with Google", "Search official documentation across Google Cloud, Gemini, Android, Firebase, and Web.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("google-antigravity-sdk", "Google Antigravity SDK", "Google", "Build with Google", "Build custom AI agents with the Google Antigravity Python SDK.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("gdocs", "Google Docs", "Google", "Google Workspace", "Read, draft, and edit Google Docs.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("gsheets", "Google Sheets", "Google", "Google Workspace", "Read, write, and format spreadsheets.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("gslides", "Google Slides", "Google", "Google Workspace", "Read, edit, and export presentations.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("gdrive", "Google Drive", "Google", "Google Workspace", "Search, upload, download, and share files.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("gmail", "Gmail", "Google", "Google Workspace", "Search, read, draft, and send emails in Gmail.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("gcalendar", "Google Calendar", "Google", "Google Workspace", "Check availability, schedule events, and RSVP.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("looker", "Looker", "Google", "Build with Google", "Connect to Looker MCP server to explore data, run Looks, and validate LookML.", "antigravity-plugins-official"),
            MarketplaceCatalogItem("figma", "Figma", "Partner", "Design, Creative & Media", "Turn Figma designs into production code and inspect Figma files.", "antigravity-plugins-partners"),
            MarketplaceCatalogItem("notion", "Notion", "Partner", "Productivity & Project Management", "Connect Antigravity to your Notion workspace with the Notion MCP server.", "antigravity-plugins-partners"),
            MarketplaceCatalogItem("linear", "Linear", "Partner", "Productivity & Project Management", "Manage issues, projects, and documents across your Linear workspace.", "antigravity-plugins-partners"),
            MarketplaceCatalogItem("stripe", "Stripe", "Partner", "Business", "Accept payments and integrate Stripe APIs.", "antigravity-plugins-partners"),
            MarketplaceCatalogItem("vercel-plugin", "Vercel", "Partner", "Cloud Hosting & Deployment", "Build and deploy web apps and AI agents on Vercel.", "antigravity-plugins-partners"),
            MarketplaceCatalogItem("datadog", "Datadog", "Partner", "Developer Tools", "Query logs, metrics, traces, and dashboards with Datadog MCP.", "antigravity-plugins-partners"),
            MarketplaceCatalogItem("auth0", "Auth0", "Partner", "Developer Tools", "Add Auth0 authentication, MFA, SSO, and RBAC to any app.", "antigravity-plugins-partners"),
            MarketplaceCatalogItem("canva", "Canva", "Partner", "Design, Creative & Media", "Create, edit, resize, and brand-check Canva designs.", "antigravity-plugins-partners"),
            MarketplaceCatalogItem("clickup", "ClickUp", "Partner", "Productivity & Project Management", "Manage tasks, track time, and collaborate in ClickUp.", "antigravity-plugins-partners")
        )
    }
}
