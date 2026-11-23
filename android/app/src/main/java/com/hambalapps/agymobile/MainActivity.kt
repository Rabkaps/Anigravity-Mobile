package com.hambalapps.agymobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hambalapps.agymobile.data.termux.TermuxBridgeBootstrap
import com.hambalapps.agymobile.ui.chat.ChatScreen
import com.hambalapps.agymobile.ui.chat.ChatViewModel
import com.hambalapps.agymobile.ui.theme.AgyExpressiveTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val chatViewModel: ChatViewModel = viewModel()
            val state by chatViewModel.uiState.collectAsState()

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

            LaunchedEffect(chatViewModel) {
                chatViewModel.onRequestVoiceInput = { launchVoiceDictation() }
                chatViewModel.onRequestTermuxPermission = {
                    termuxPermissionLauncher.launch(TermuxBridgeBootstrap.PERMISSION_RUN_COMMAND)
                }
            }

            AgyExpressiveTheme(
                darkTheme = isSystemInDarkTheme(),
                dynamicColor = state.preferences?.dynamicColor ?: true,
                amoledDark = state.preferences?.amoledDark ?: false
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
