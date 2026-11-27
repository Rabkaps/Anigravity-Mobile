package com.hambalapps.agymobile

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hambalapps.agymobile.ui.chat.ChatScreen
import com.hambalapps.agymobile.ui.chat.ChatViewModel
import com.hambalapps.agymobile.ui.chat.PendingAttachmentUi
import com.hambalapps.agymobile.ui.theme.AgyExpressiveTheme
import com.hambalapps.agymobile.ui.theme.AppThemeMode
import com.hambalapps.agymobile.ui.theme.getLastKnownCoarseLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val chatViewModel: ChatViewModel = viewModel()
            val state by chatViewModel.uiState.collectAsState()
            val themeMode = AppThemeMode.fromKey(state.preferences?.themeMode)
            val coroutineScope = rememberCoroutineScope()
            var solarLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
            var askedLocationOnce by remember { mutableStateOf(false) }
            var solarEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var pendingFolderCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

            val imagePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetMultipleContents()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    coroutineScope.launch {
                        val items = buildAttachmentsFromUris(
                            uris = uris,
                            forceImage = true,
                            stagingRoot = File(chatViewModel.engineManager.homeDir, ".agy-attachments")
                        )
                        chatViewModel.addAttachments(items)
                    }
                }
            }

            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    coroutineScope.launch {
                        val items = buildAttachmentsFromUris(
                            uris = uris,
                            forceImage = false,
                            stagingRoot = File(chatViewModel.engineManager.homeDir, ".agy-attachments")
                        )
                        chatViewModel.addAttachments(items)
                    }
                }
            }

            val folderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                val callback = pendingFolderCallback
                pendingFolderCallback = null
                if (uri != null && callback != null) {
                    val resolved = resolveTreeUriToPath(uri)
                    if (resolved != null) {
                        callback(resolved)
                    } else {
                        Toast.makeText(
                            this,
                            "Could not resolve filesystem path for selected folder",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    solarLocation = getLastKnownCoarseLocation(this)
                }
            }

            val speechLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                chatViewModel.setVoiceListening(false)
                if (result.resultCode == Activity.RESULT_OK) {
                    val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    val spoken = matches?.firstOrNull()?.trim()
                    if (!spoken.isNullOrEmpty()) {
                        chatViewModel.appendVoiceResult(spoken)
                    }
                }
            }

            val storagePermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { _ -> }

            fun requestAllFilesAccessIfNeeded() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        val appIntent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        try {
                            startActivity(appIntent)
                        } catch (_: Exception) {
                            runCatching {
                                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        }
                    }
                } else {
                    val hasWrite = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasRead = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasWrite || !hasRead) {
                        storagePermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        )
                    }
                }
            }

            fun launchVoiceDictation() {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak prompt for agy…")
                }
                try {
                    chatViewModel.setVoiceListening(true)
                    speechLauncher.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    chatViewModel.setVoiceListening(false)
                    Toast.makeText(
                        this,
                        "No speech recognition service available on device",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            LaunchedEffect(Unit) {
                requestAllFilesAccessIfNeeded()
            }

            LaunchedEffect(chatViewModel) {
                chatViewModel.onRequestVoiceInput = { launchVoiceDictation() }
            }

            LaunchedEffect(themeMode) {
                if (themeMode == AppThemeMode.SOLAR) {
                    val hasCoarse = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCoarse) {
                        solarLocation = getLastKnownCoarseLocation(this@MainActivity)
                    } else if (!askedLocationOnce) {
                        askedLocationOnce = true
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            solarEpochMillis = System.currentTimeMillis()
                            if (ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                solarLocation = getLastKnownCoarseLocation(this@MainActivity) ?: solarLocation
                            }
                            delay(60_000L)
                        }
                    }
                }
            }

            AgyExpressiveTheme(
                darkTheme = isSystemInDarkTheme(),
                dynamicColor = state.preferences?.dynamicColor ?: true,
                amoledDark = state.preferences?.amoledDark ?: false,
                themeMode = themeMode,
                solarLocation = solarLocation,
                solarEpochMillis = solarEpochMillis
            ) {
                ChatScreen(
                    state = state,
                    viewModel = chatViewModel,
                    onRequestVoice = { launchVoiceDictation() },
                    onAttachImages = { imagePickerLauncher.launch("image/*") },
                    onAttachFiles = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onPickSystemFolder = { onPicked ->
                        pendingFolderCallback = onPicked
                        folderPickerLauncher.launch(null)
                    }
                )
            }
        }
    }

    private suspend fun buildAttachmentsFromUris(
        uris: List<Uri>,
        forceImage: Boolean,
        stagingRoot: File
    ): List<PendingAttachmentUi> = withContext(Dispatchers.IO) {
        stagingRoot.mkdirs()
        uris.mapNotNull { uri ->
            runCatching {
                val displayName = queryDisplayName(uri) ?: "attachment_${System.currentTimeMillis()}"
                val detectedMime = contentResolver.getType(uri) ?: guessMimeType(displayName)
                val isImage = forceImage || detectedMime.startsWith("image/")

                val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val stagedFile = File(stagingRoot, "${System.currentTimeMillis()}_$safeName")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(stagedFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@mapNotNull null

                val sizeBytes = stagedFile.length()
                if (isImage) {
                    val encoded = encodeImageFileForAcp(stagedFile, detectedMime)
                    PendingAttachmentUi(
                        displayName = displayName,
                        mimeType = encoded?.second ?: detectedMime,
                        isImage = true,
                        localPath = stagedFile.absolutePath,
                        base64Data = encoded?.first,
                        sizeBytes = sizeBytes
                    )
                } else {
                    val textContent = if (isTextLike(detectedMime, displayName) && sizeBytes <= 256 * 1024) {
                        runCatching { stagedFile.readText(Charsets.UTF_8) }.getOrNull()
                    } else null
                    PendingAttachmentUi(
                        displayName = displayName,
                        mimeType = detectedMime,
                        isImage = false,
                        localPath = stagedFile.absolutePath,
                        textContent = textContent,
                        sizeBytes = sizeBytes
                    )
                }
            }.getOrNull()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun guessMimeType(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "kt", "kts", "java", "py", "ts", "tsx", "js", "jsx", "sh", "txt", "log", "yml", "yaml", "toml", "gradle" -> "text/plain"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    private fun isTextLike(mime: String, name: String): Boolean {
        if (mime.startsWith("text/")) return true
        if (mime in setOf("application/json", "application/xml", "application/javascript", "application/x-sh")) {
            return true
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf(
            "txt", "md", "json", "xml", "yaml", "yml", "toml", "ini", "conf", "cfg",
            "kt", "kts", "java", "py", "ts", "tsx", "js", "jsx", "sh", "bash", "zsh",
            "c", "cpp", "h", "hpp", "rs", "go", "rb", "php", "html", "css", "sql", "gradle", "properties", "log"
        )
    }

    private fun encodeImageFileForAcp(file: File, originalMime: String): Pair<String, String>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        val maxDim = 1600
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val rawBmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
        val bmp = if (rawBmp.width > maxDim || rawBmp.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(rawBmp.width, rawBmp.height).toFloat()
            val w = (rawBmp.width * scale).toInt().coerceAtLeast(1)
            val h = (rawBmp.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(rawBmp, w, h, true).also {
                if (it !== rawBmp) rawBmp.recycle()
            }
        } else {
            rawBmp
        }
        val usePng = originalMime.equals("image/png", ignoreCase = true)
        val outMime = if (usePng) "image/png" else "image/jpeg"
        val format = if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val bytes = ByteArrayOutputStream().use { baos ->
            bmp.compress(format, 85, baos)
            baos.toByteArray()
        }
        bmp.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP) to outMime
    }

    private fun resolveTreeUriToPath(uri: Uri): String? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        if (docId.startsWith("raw:")) {
            return docId.removePrefix("raw:")
        }
        val split = docId.split(":", limit = 2)
        val type = split.getOrNull(0).orEmpty()
        val rel = split.getOrNull(1).orEmpty()
        return when {
            type.equals("primary", ignoreCase = true) -> {
                val base = Environment.getExternalStorageDirectory().absolutePath
                if (rel.isBlank()) base else "$base/$rel"
            }
            type.equals("home", ignoreCase = true) -> {
                val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                if (rel.isBlank()) docs else "$docs/$rel"
            }
            type.isNotBlank() -> {
                if (rel.isBlank()) "/storage/$type" else "/storage/$type/$rel"
            }
            else -> null
        }
    }
}
