package com.hambalapps.agymobile

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.speech.RecognizerIntent
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
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hambalapps.agymobile.data.termux.TermuxBridgeBootstrap
import com.hambalapps.agymobile.ui.chat.ChatScreen
import com.hambalapps.agymobile.ui.chat.ChatViewModel
import com.hambalapps.agymobile.ui.theme.AgyExpressiveTheme
import com.hambalapps.agymobile.ui.theme.AppThemeMode
import com.hambalapps.agymobile.ui.theme.getLastKnownCoarseLocation
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val chatViewModel: ChatViewModel = viewModel()
            val state by chatViewModel.uiState.collectAsState()
            val themeMode = AppThemeMode.fromKey(state.preferences?.themeMode)
            var solarLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
            var askedLocationOnce by remember { mutableStateOf(false) }
            var solarEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

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

            val termuxPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                chatViewModel.onTermuxPermissionResult(granted)
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
                chatViewModel.onRequestTermuxPermission = {
                    termuxPermissionLauncher.launch(TermuxBridgeBootstrap.PERMISSION_RUN_COMMAND)
                }
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
                    onRequestVoice = { launchVoiceDictation() }
                )
            }
        }
    }
}
