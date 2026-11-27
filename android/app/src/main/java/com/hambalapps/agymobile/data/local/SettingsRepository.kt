package com.hambalapps.agymobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agy_mobile_prefs")

@Serializable
data class ConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 4789,
    val token: String = "",
    val useTls: Boolean = false,
    val isRemote: Boolean = false,
    val isStandalone: Boolean = false,
    val defaultCwd: String = "/data/data/com.termux/files/home"
) {
    fun httpBaseUrl(): String {
        val scheme = if (useTls) "https" else "http"
        return "$scheme://${host.trim()}:$port"
    }

    fun acpWebSocketUrl(): String {
        val scheme = if (useTls) "wss" else "ws"
        return "$scheme://${host.trim()}:$port/acp"
    }

    fun ptyWebSocketUrl(): String {
        val scheme = if (useTls) "wss" else "ws"
        return "$scheme://${host.trim()}:$port/pty"
    }
}

@Serializable
data class LocalProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val rootDir: String,
    val extraDirs: List<String> = emptyList()
)

data class AppPreferences(
    val profiles: List<ConnectionProfile>,
    val activeProfileId: String,
    val amoledDark: Boolean,
    val dynamicColor: Boolean,
    val selectedModel: String,
    val selectedEffort: String,
    val selectedMode: String,
    val permissionPolicy: String = "request_review",
    val geminiApiKey: String = "",
    val projects: List<LocalProject> = emptyList(),
    val activeProjectId: String = "",
    val themeMode: String = "system"
) {
    val activeProfile: ConnectionProfile
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()

    val activeProject: LocalProject?
        get() = projects.firstOrNull { it.id == activeProjectId } ?: projects.firstOrNull()
}

class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val PROFILES = stringPreferencesKey("connection_profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val SELECTED_EFFORT = stringPreferencesKey("selected_effort")
        val SELECTED_MODE = stringPreferencesKey("selected_mode")
        val PERMISSION_POLICY = stringPreferencesKey("permission_policy")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val PROJECTS = stringPreferencesKey("local_projects_json")
        val ACTIVE_PROJECT_ID = stringPreferencesKey("active_project_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    private val standaloneDefaultCwd = "${context.filesDir.absolutePath}/engine/home"
    private val defaultProject = LocalProject(
        id = "default-project",
        name = "Default Workspace",
        rootDir = standaloneDefaultCwd
    )

    private val defaultStandaloneProfile = ConnectionProfile(
        id = "local-standalone",
        name = "Standalone Engine (Built-in)",
        host = "127.0.0.1",
        port = 0,
        token = "",
        useTls = false,
        isRemote = false,
        isStandalone = true,
        defaultCwd = standaloneDefaultCwd
    )

    private val defaultProfiles = listOf(
        defaultStandaloneProfile,
        ConnectionProfile(
            id = "local-termux",
            name = "On-Device Termux",
            host = "127.0.0.1",
            port = 4789,
            token = "",
            useTls = false,
            isRemote = false,
            isStandalone = false,
            defaultCwd = "/data/data/com.termux/files/home"
        )
    )

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val decoded = prefs[Keys.PROFILES]?.let { raw ->
            runCatching { json.decodeFromString<List<ConnectionProfile>>(raw) }.getOrNull()
        }?.takeIf { it.isNotEmpty() }

        val decodedProfiles = when {
            decoded == null -> defaultProfiles
            decoded.none { it.isStandalone || it.id == "local-standalone" } ->
                listOf(defaultStandaloneProfile) + decoded
            else -> decoded
        }

        val activeId = prefs[Keys.ACTIVE_PROFILE_ID] ?: decodedProfiles.first().id
        val decodedProjects = prefs[Keys.PROJECTS]?.let { raw ->
            runCatching { json.decodeFromString<List<LocalProject>>(raw) }.getOrNull()
        }?.takeIf { it.isNotEmpty() } ?: listOf(defaultProject)
        val activeProjId = prefs[Keys.ACTIVE_PROJECT_ID] ?: decodedProjects.first().id
        AppPreferences(
            profiles = decodedProfiles,
            activeProfileId = activeId,
            amoledDark = prefs[Keys.AMOLED_DARK] ?: false,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            selectedModel = prefs[Keys.SELECTED_MODEL] ?: "default",
            selectedEffort = prefs[Keys.SELECTED_EFFORT] ?: "medium",
            selectedMode = prefs[Keys.SELECTED_MODE] ?: "default",
            permissionPolicy = prefs[Keys.PERMISSION_POLICY] ?: "request_review",
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            projects = decodedProjects,
            activeProjectId = activeProjId,
            themeMode = prefs[Keys.THEME_MODE] ?: "system"
        )
    }

    suspend fun saveProfile(profile: ConnectionProfile, setActive: Boolean = true) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES]?.let { raw ->
                runCatching { json.decodeFromString<List<ConnectionProfile>>(raw) }.getOrNull()
            } ?: defaultProfiles

            val updated = if (current.any { it.id == profile.id }) {
                current.map { if (it.id == profile.id) profile else it }
            } else {
                current + profile
            }
            prefs[Keys.PROFILES] = json.encodeToString(updated)
            if (setActive) {
                prefs[Keys.ACTIVE_PROFILE_ID] = profile.id
            }
        }
    }

    suspend fun deleteProfile(profileId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES]?.let { raw ->
                runCatching { json.decodeFromString<List<ConnectionProfile>>(raw) }.getOrNull()
            } ?: defaultProfiles
            val filtered = current.filterNot { it.id == profileId }.ifEmpty { defaultProfiles }
            prefs[Keys.PROFILES] = json.encodeToString(filtered)
            if (prefs[Keys.ACTIVE_PROFILE_ID] == profileId) {
                prefs[Keys.ACTIVE_PROFILE_ID] = filtered.first().id
            }
        }
    }

    suspend fun setActiveProfile(profileId: String) {
        context.dataStore.edit { prefs -> prefs[Keys.ACTIVE_PROFILE_ID] = profileId }
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AMOLED_DARK] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode }
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SELECTED_MODEL] = model }
    }

    suspend fun setSelectedEffort(effort: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SELECTED_EFFORT] = effort }
    }

    suspend fun setSelectedMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SELECTED_MODE] = mode }
    }

    suspend fun setPermissionPolicy(policy: String) {
        context.dataStore.edit { prefs -> prefs[Keys.PERMISSION_POLICY] = policy }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            if (apiKey.isBlank()) prefs.remove(Keys.GEMINI_API_KEY)
            else prefs[Keys.GEMINI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun saveProject(project: LocalProject, setActive: Boolean = true) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROJECTS]?.let { raw ->
                runCatching { json.decodeFromString<List<LocalProject>>(raw) }.getOrNull()
            }?.takeIf { it.isNotEmpty() } ?: listOf(defaultProject)
            val updated = if (current.any { it.id == project.id }) {
                current.map { if (it.id == project.id) project else it }
            } else {
                current + project
            }
            prefs[Keys.PROJECTS] = json.encodeToString(updated)
            if (setActive) {
                prefs[Keys.ACTIVE_PROJECT_ID] = project.id
            }
        }
    }

    suspend fun deleteProject(projectId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROJECTS]?.let { raw ->
                runCatching { json.decodeFromString<List<LocalProject>>(raw) }.getOrNull()
            } ?: listOf(defaultProject)
            val filtered = current.filterNot { it.id == projectId }.ifEmpty { listOf(defaultProject) }
            prefs[Keys.PROJECTS] = json.encodeToString(filtered)
            if (prefs[Keys.ACTIVE_PROJECT_ID] == projectId) {
                prefs[Keys.ACTIVE_PROJECT_ID] = filtered.first().id
            }
        }
    }

    suspend fun setActiveProject(projectId: String) {
        context.dataStore.edit { prefs -> prefs[Keys.ACTIVE_PROJECT_ID] = projectId }
    }
}
