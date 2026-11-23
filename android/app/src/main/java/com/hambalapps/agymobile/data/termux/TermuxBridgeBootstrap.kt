package com.hambalapps.agymobile.data.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPOutputStream

object TermuxBridgeBootstrap {
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    const val TERMUX_HOME = "/data/data/com.termux/files/home"
    const val TERMUX_HOME_USER0 = "/data/user/0/com.termux/files/home"
    const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    const val TERMUX_BASH = "$TERMUX_PREFIX/bin/bash"

    const val ENABLE_EXTERNAL_APPS_CMD =
        "mkdir -p ~/.termux && grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings"

    const val AGY_TERMUX_INSTALL_CMD =
        "curl -fsSL https://antigravity.google/cli/install.sh | bash -s -- --dir \"\$PREFIX/bin\""

    data class StagedBridge(
        val bridgeDir: File,
        val runScriptFile: File,
        val files: Map<String, ByteArray>
    )

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TERMUX_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasRunCommandPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_RUN_COMMAND
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * F-Droid / GitHub Termux declares `com.termux.permission.RUN_COMMAND`, whereas
     * Google Play Termux (`googleplay.2026.*`) omits it.
     */
    fun isRunCommandSupported(context: Context): Boolean {
        return try {
            val info = context.packageManager.getPermissionInfo(PERMISSION_RUN_COMMAND, 0)
            info.packageName == TERMUX_PACKAGE
        } catch (_: Exception) {
            false
        }
    }

    fun openTermuxApp(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        context.startActivity(launchIntent)
        return true
    }

    fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates the Termux-side script that installs `nodejs-lts` + production npm deps
     * on first run, writes the paired Bearer token, and launches `agy-bridge` detached.
     */
    fun buildTermuxRunnerScript(
        token: String,
        port: Int,
        copyFromDir: String? = null
    ): String {
        val safeToken = shellSingleQuote(token)
        val copyStep = if (copyFromDir != null) {
            "cp -rf ${shellSingleQuote(copyFromDir)}/. \"${'$'}BRIDGE_DIR/\"\n"
        } else {
            ""
        }

        return """
            #!/data/data/com.termux/files/usr/bin/bash
            set -e
            export PREFIX="$TERMUX_PREFIX"
            export HOME="${'$'}{HOME:-$TERMUX_HOME}"
            export PATH="$TERMUX_PREFIX/bin:${'$'}HOME/bin:${'$'}PATH"

            if ! command -v node >/dev/null 2>&1; then
              echo "[agy-bridge] Installing nodejs-lts..."
              pkg update -y
              pkg install -y nodejs-lts curl
            fi

            if ! command -v agy >/dev/null 2>&1; then
              if ! command -v curl >/dev/null 2>&1; then
                pkg install -y curl || true
              fi
              if command -v curl >/dev/null 2>&1; then
                echo "[agy-bridge] Installing official Google Antigravity CLI (android_arm64)..."
                curl -fsSL https://antigravity.google/cli/install.sh | bash -s -- --dir "$TERMUX_PREFIX/bin" || echo "[agy-bridge] Warning: agy auto-install failed; you can install it from the Shell drawer."
              fi
            fi

            BRIDGE_DIR="${'$'}HOME/.agy-bridge"
            mkdir -p "${'$'}BRIDGE_DIR" "${'$'}HOME/.config/agy-bridge"
            $copyStep
            if [ ! -d "${'$'}BRIDGE_DIR/node_modules" ]; then
              echo "[agy-bridge] Installing Node dependencies..."
              cd "${'$'}BRIDGE_DIR"
              npm install --omit=dev --no-audit --no-fund
            fi

            printf '%s\n' $safeToken > "${'$'}HOME/.config/agy-bridge/token"
            chmod 600 "${'$'}HOME/.config/agy-bridge/token"

            pkill -f "agy-bridge/dist/server.js" >/dev/null 2>&1 || true
            sleep 0.3

            if [ -x "/system/bin/sh" ]; then
              export SHELL="/system/bin/sh"
            fi
            if [ -f "$TERMUX_PREFIX/lib/libtermux-exec.so" ]; then
              export LD_PRELOAD="$TERMUX_PREFIX/lib/libtermux-exec.so"
            fi

            cd "${'$'}BRIDGE_DIR"
            if command -v setsid >/dev/null 2>&1; then
              setsid node "${'$'}BRIDGE_DIR/dist/server.js" --port "$port" </dev/null >"${'$'}BRIDGE_DIR/bridge.log" 2>&1 &
            else
              nohup node "${'$'}BRIDGE_DIR/dist/server.js" --port "$port" </dev/null >"${'$'}BRIDGE_DIR/bridge.log" 2>&1 &
            fi
            disown 2>/dev/null || true
            echo "[agy-bridge] Started on port $port"
        """.trimIndent() + "\n"
    }

    /**
     * Builds a self-extracting, gzipped shell script for unrooted Termux `RUN_COMMAND`
     * so Termux never needs cross-app access to `/Android/data/...`.
     */
    fun buildSelfExtractingTermuxCommand(
        files: Map<String, ByteArray>,
        token: String,
        port: Int
    ): String {
        val encoder = Base64.getEncoder()
        val sb = StringBuilder()
        sb.append("set -e\n")
        sb.append("BRIDGE_DIR=\"\$HOME/.agy-bridge\"\n")
        sb.append("mkdir -p \"\$BRIDGE_DIR\"\n")

        for ((relPath, bytes) in files.entries.sortedBy { it.key }) {
            val safeRel = relPath.replace("\\", "/").trimStart('/')
            val parent = safeRel.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) {
                sb.append("mkdir -p \"\$BRIDGE_DIR/$parent\"\n")
            }
            val b64 = encoder.encodeToString(bytes)
            sb.append("printf '%s' '$b64' | base64 -d > \"\$BRIDGE_DIR/$safeRel\"\n")
        }

        sb.append(buildTermuxRunnerScript(token = token, port = port, copyFromDir = null))

        val gzBytes = ByteArrayOutputStream().use { bos ->
            GZIPOutputStream(bos).use { gzip ->
                gzip.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
            bos.toByteArray()
        }
        val gzB64 = encoder.encodeToString(gzBytes)
        return "printf '%s' '$gzB64' | base64 -d | gzip -dc | $TERMUX_BASH"
    }

    /**
     * Extracts bundled `assets/bridge/` (excluding `.d.ts` files) into app-private `filesDir/bridge`
     * and collects file bytes for self-extracting Termux `RUN_COMMAND`.
     */
    fun extractBundledBridge(context: Context, token: String, port: Int): StagedBridge {
        val targetDir = File(context.filesDir, "bridge")
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        val collected = linkedMapOf<String, ByteArray>()
        copyAssetTree(context, "bridge", "", targetDir, collected)

        val scriptFile = File(targetDir, "run.sh")
        scriptFile.writeText(
            buildTermuxRunnerScript(token = token, port = port, copyFromDir = null),
            Charsets.UTF_8
        )
        scriptFile.setReadable(true, false)
        scriptFile.setExecutable(true, false)

        return StagedBridge(bridgeDir = targetDir, runScriptFile = scriptFile, files = collected)
    }

    private fun copyAssetTree(
        context: Context,
        assetPath: String,
        relPath: String,
        targetFile: File,
        collected: MutableMap<String, ByteArray>
    ) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            if (relPath.endsWith(".d.ts")) return
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            targetFile.parentFile?.mkdirs()
            targetFile.writeBytes(bytes)
            targetFile.setReadable(true, false)
            if (relPath.isNotEmpty()) {
                collected[relPath] = bytes
            }
            return
        }

        targetFile.mkdirs()
        for (child in children) {
            val childRel = if (relPath.isEmpty()) child else "$relPath/$child"
            copyAssetTree(context, "$assetPath/$child", childRel, File(targetFile, child), collected)
        }
    }

    fun launchViaTermuxRunCommand(
        context: Context,
        staged: StagedBridge,
        token: String,
        port: Int,
        background: Boolean = true
    ): Result<Unit> = runCatching {
        val inlineCommand = buildSelfExtractingTermuxCommand(staged.files, token, port)
        val intent = Intent(TERMUX_RUN_COMMAND_ACTION).apply {
            setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", inlineCommand))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Zero-touch bootstrap on rooted devices:
     * Enters the global mount namespace (`su -t 0` on Magisk 26+, falling back to `su -mm` / `su`)
     * so `/data/data/com.termux` is visible outside Android 11+ per-app `/data/data` isolation,
     * copies bridge files as root with proper Termux UID ownership, enables `allow-external-apps`,
     * and executes `run.sh` under Termux's UID.
     */
    suspend fun launchViaRootSu(
        staged: StagedBridge,
        token: String,
        port: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeBridgeSrc = shellSingleQuote(staged.bridgeDir.absolutePath)
            val safeToken = shellSingleQuote(token)
            val rootScript = """
                THOME=""
                for cand in "$TERMUX_HOME" "$TERMUX_HOME_USER0"; do
                  if [ -d "${'$'}cand" ]; then
                    THOME="${'$'}cand"
                    break
                  fi
                done
                if [ -z "${'$'}THOME" ]; then
                  echo "Termux home directory not found ($TERMUX_HOME). Open Termux once first." >&2
                  exit 1
                fi

                TERMUX_UID=$(stat -c '%u' "${'$'}THOME" 2>/dev/null || true)
                if [ -z "${'$'}TERMUX_UID" ]; then
                  echo "Could not resolve Termux UID for ${'$'}THOME" >&2
                  exit 1
                fi

                TPREFIX="$(dirname "${'$'}THOME")/usr"
                TBASH="${'$'}TPREFIX/bin/bash"
                BRIDGE_DST="${'$'}THOME/.agy-bridge"

                mkdir -p "${'$'}THOME/.termux" "${'$'}BRIDGE_DST" "${'$'}THOME/.config/agy-bridge"
                cp -rf $safeBridgeSrc/. "${'$'}BRIDGE_DST/"

                printf '%s\n' $safeToken > "${'$'}THOME/.config/agy-bridge/token"

                PROP="${'$'}THOME/.termux/termux.properties"
                grep -qxF 'allow-external-apps=true' "${'$'}PROP" 2>/dev/null || echo 'allow-external-apps=true' >> "${'$'}PROP"

                chown -R "${'$'}TERMUX_UID:${'$'}TERMUX_UID" "${'$'}THOME/.termux" "${'$'}BRIDGE_DST" "${'$'}THOME/.config/agy-bridge"
                chmod 700 "${'$'}THOME/.termux" "${'$'}BRIDGE_DST/run.sh"
                chmod 600 "${'$'}PROP" "${'$'}THOME/.config/agy-bridge/token"

                # If node + node_modules are already installed, launch node directly as root (uid=0)
                # so netd DNS works cleanly without u:r:magisk:s0 UID-switch DNS restrictions.
                if [ -x "${'$'}TPREFIX/bin/node" ] && [ -d "${'$'}BRIDGE_DST/node_modules" ]; then
                  export PREFIX="${'$'}TPREFIX"
                  export HOME="${'$'}THOME"
                  export PATH="${'$'}TPREFIX/bin:${'$'}THOME/bin:${'$'}PATH"
                  if [ -x "/system/bin/sh" ]; then
                    export SHELL="/system/bin/sh"
                  fi
                  if [ -f "${'$'}TPREFIX/lib/libtermux-exec.so" ]; then
                    export LD_PRELOAD="${'$'}TPREFIX/lib/libtermux-exec.so"
                  fi
                  if [ ! -x "${'$'}TPREFIX/bin/agy" ] && command -v curl >/dev/null 2>&1; then
                    HOME="${'$'}THOME" "${'$'}TBASH" -c "curl -fsSL https://antigravity.google/cli/install.sh | bash -s -- --dir '${'$'}TPREFIX/bin'" >/dev/null 2>&1 || true
                    chown -R "${'$'}TERMUX_UID:${'$'}TERMUX_UID" "${'$'}TPREFIX/bin/agy" "${'$'}THOME/.cache/antigravity" 2>/dev/null || true
                  fi

                  pkill -f "agy-bridge/dist/server.js" >/dev/null 2>&1 || true
                  sleep 0.3
                  cd "${'$'}BRIDGE_DST"
                  if command -v setsid >/dev/null 2>&1; then
                    setsid "${'$'}TPREFIX/bin/node" "${'$'}BRIDGE_DST/dist/server.js" --port "$port" </dev/null >"${'$'}BRIDGE_DST/bridge.log" 2>&1 &
                  else
                    nohup "${'$'}TPREFIX/bin/node" "${'$'}BRIDGE_DST/dist/server.js" --port "$port" </dev/null >"${'$'}BRIDGE_DST/bridge.log" 2>&1 &
                  fi
                  disown 2>/dev/null || true
                  echo "STARTED_DIRECT"
                  exit 0
                fi

                echo "NEEDS_TERMUX_FIRST_INSTALL"
                exit 2
            """.trimIndent()

            // Try global mount namespace flags in preference order: Magisk 26+ (-t 0), legacy (-mm), default
            val candidates = listOf(
                listOf("su", "-t", "0", "-c", rootScript),
                listOf("su", "-mm", "-c", rootScript),
                listOf("su", "-c", rootScript)
            )

            var lastErr = "su failed"
            for (cmd in candidates) {
                val res = runSuCommand(cmd)
                if (res.first == 0) {
                    return@runCatching res.second
                }
                lastErr = "su exited with ${res.first}: ${res.second}"
                // Only fall back to another su flag if the flag itself was unrecognized
                if (!res.second.contains("unknown option", ignoreCase = true) &&
                    !res.second.contains("unrecognized", ignoreCase = true) &&
                    !res.second.contains("invalid option", ignoreCase = true) &&
                    !res.second.contains("Termux home directory not found", ignoreCase = true)
                ) {
                    break
                }
            }
            error(lastErr)
        }
    }

    private fun runSuCommand(cmd: List<String>): Pair<Int, String> {
        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        val code = proc.waitFor()
        return code to output
    }

    private fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
