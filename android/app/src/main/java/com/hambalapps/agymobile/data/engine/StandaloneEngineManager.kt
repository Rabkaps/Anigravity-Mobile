package com.hambalapps.agymobile.data.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.system.Os
import com.hambalapps.agymobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class EngineArch(
    val abi: String,
    val agyManifest: String,
    val termuxBootstrapArch: String,
    val isStaticBinary: Boolean
)

/**
 * Manages the self-contained on-device Antigravity runtime inside `filesDir/engine/`,
 * removing any hard dependency on the Termux app across both `arm64-v8a` and `x86_64` (gbOS86).
 */
class StandaloneEngineManager(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    val engineDir: File = File(context.filesDir, "engine")
    val usrDir: File = File(engineDir, "usr")
    val homeDir: File = File(engineDir, "home")
    val tmpDir: File = File(engineDir, "tmp")
    val workspaceDir: File = File(homeDir, "workspace")
    val agyBin: File = File(usrDir, "bin/agy")
    val agyRealBin: File = File(usrDir, "libexec/agy.bin")
    val nodeBin: File = File(usrDir, "bin/node")
    val npmBin: File = File(usrDir, "bin/npm")
    val gitBin: File = File(usrDir, "bin/git")
    val bashBin: File = File(usrDir, "bin/bash")
    val bridgeDir: File = File(homeDir, ".agy-bridge")
    val mcpScript: File = File(bridgeDir, "dist/mcp/android-control-mcp.js")
    val oauthTokenFile: File = File(homeDir, ".gemini/antigravity-cli/antigravity-oauth-token")
    val cliSettingsFile: File = File(homeDir, ".gemini/antigravity-cli/settings.json")
    val browserSpoolDir: File = File(tmpDir, "browser_open")
    val androidSdkDir: File = File(homeDir, "android-sdk")
    private val agyVersionFile: File = File(engineDir, "agy.version")
    private val toolchainMarker: File = File(engineDir, "toolchain.ok")
    private val installedPkgsFile: File = File(usrDir, ".installed-pkgs")

    private val bridgeToken: String by lazy {
        ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }

    @Volatile
    private var cachedPrivilegeLevel: String? = null

    @Volatile
    private var shimsInitialized: Boolean = false

    fun isAndroidSdkInstalled(): Boolean {
        val hasJava = resolveJavaHome() != null
        val hasAapt2 = File(usrDir, "bin/aapt2").exists()
        val hasPlatform = File(androidSdkDir, "platforms").listFiles()?.any { it.isDirectory } == true
        return hasJava && hasAapt2 && hasPlatform
    }

    private fun resolveJavaHome(): File? {
        val jvmRoot = File(usrDir, "lib/jvm")
        if (!jvmRoot.isDirectory) return null
        val preferred = listOf("java-17-openjdk", "java-21-openjdk")
            .map { File(jvmRoot, it) }
            .firstOrNull { File(it, "bin/java").exists() }
        return preferred ?: jvmRoot.listFiles()?.firstOrNull {
            it.isDirectory && File(it, "bin/java").exists()
        }
    }

    fun detectArch(): EngineArch {
        val abis = Build.SUPPORTED_ABIS.toList()
        return if (abis.any { it.equals("x86_64", ignoreCase = true) }) {
            EngineArch(
                abi = "x86_64",
                agyManifest = "linux_amd64_musl",
                termuxBootstrapArch = "x86_64",
                isStaticBinary = true
            )
        } else {
            EngineArch(
                abi = "arm64-v8a",
                agyManifest = "android_arm64",
                termuxBootstrapArch = "aarch64",
                isStaticBinary = false
            )
        }
    }

    fun isAgyInstalled(): Boolean =
        (agyRealBin.exists() && agyRealBin.length() > 1_000_000L) ||
            (agyBin.exists() && agyBin.length() > 1_000_000L)

    fun isToolchainInstalled(): Boolean =
        toolchainMarker.exists() && bashBin.exists() && nodeBin.exists() && gitBin.exists()

    fun isNodeMcpReady(): Boolean =
        nodeBin.exists() && mcpScript.exists() && File(bridgeDir, "node_modules").exists()

    fun installedAgyVersion(): String? =
        if (agyVersionFile.exists()) agyVersionFile.readText().trim().ifBlank { null } else null

    fun hasGeminiApiKeyConfigured(): Boolean {
        if (!cliSettingsFile.exists()) return false
        return runCatching {
            val obj = JSONObject(cliSettingsFile.readText())
            obj.optString("modelProvider") == "gemini"
        }.getOrDefault(false)
    }

    fun isSignedIn(): Boolean =
        (oauthTokenFile.exists() && oauthTokenFile.length() > 20L) || hasGeminiApiKeyConfigured()

    fun setModelProviderGemini(enabled: Boolean) {
        runCatching {
            cliSettingsFile.parentFile?.mkdirs()
            val obj = if (cliSettingsFile.exists()) {
                runCatching { JSONObject(cliSettingsFile.readText()) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }
            if (enabled) {
                obj.put("modelProvider", "gemini")
            } else {
                obj.remove("modelProvider")
            }
            cliSettingsFile.writeText(obj.toString(2) + "\n", Charsets.UTF_8)
        }
    }

    fun ensureBaseDirs() {
        usrDir.mkdirs()
        File(usrDir, "bin").mkdirs()
        File(usrDir, "lib").mkdirs()
        File(usrDir, "libexec").mkdirs()
        homeDir.mkdirs()
        tmpDir.mkdirs()
        browserSpoolDir.mkdirs()
        workspaceDir.mkdirs()
    }

    fun ensureAgyWrapper() {
        runCatching {
            File(usrDir, "libexec").mkdirs()
            File(usrDir, "bin").mkdirs()
            if (!agyRealBin.exists() && agyBin.exists() && agyBin.length() > 1_000_000L) {
                if (!agyBin.renameTo(agyRealBin)) {
                    agyBin.copyTo(agyRealBin, overwrite = true)
                    agyBin.delete()
                }
            }
            if (!agyRealBin.exists()) return@runCatching
            chmod755(agyRealBin)
            val arch = detectArch()
            val wrapperScript = if (arch.isStaticBinary) {
                "#!/system/bin/sh\nexec \"${agyRealBin.absolutePath}\" \"${'$'}@\"\n"
            } else {
                val linker = File("/system/bin/linker64")
                if (linker.exists()) {
                    "#!/system/bin/sh\nexec /system/bin/linker64 \"${agyRealBin.absolutePath}\" \"${'$'}@\"\n"
                } else {
                    "#!/system/bin/sh\nexec \"${agyRealBin.absolutePath}\" \"${'$'}@\"\n"
                }
            }
            if (!agyBin.exists() || agyBin.length() > 4096L || agyBin.readText() != wrapperScript) {
                agyBin.writeText(wrapperScript, Charsets.UTF_8)
            }
            chmod755(agyBin)
        }
    }

    fun buildLinkerCommand(executable: File, args: List<String> = emptyList()): List<String> {
        val linker = File("/system/bin/linker64")
        return if (linker.exists()) {
            listOf(linker.absolutePath, executable.absolutePath) + args
        } else {
            listOf(executable.absolutePath) + args
        }
    }

    fun buildAgyCommand(args: List<String> = emptyList()): List<String> {
        ensureAgyWrapper()
        val targetBin = if (agyRealBin.exists()) agyRealBin else agyBin
        val arch = detectArch()
        return if (arch.isStaticBinary) {
            listOf(targetBin.absolutePath) + args
        } else {
            buildLinkerCommand(targetBin, args)
        }
    }

    fun findTermuxExecLib(): File? {
        val v2 = File(usrDir, "lib/libtermux-exec-ld-preload.so")
        if (v2.exists()) return v2
        val v1 = File(usrDir, "lib/libtermux-exec.so")
        if (v1.exists()) return v1
        return null
    }

    fun buildChildEnvironment(): Map<String, String> {
        ensureBaseDirs()
        val env = LinkedHashMap<String, String>()
        val sysPath = System.getenv("PATH")
            ?: "/product/bin:/apex/com.android.runtime/bin:/system_ext/bin:/system/bin:/system/xbin"
        val binDir = File(usrDir, "bin").absolutePath
        val libDir = File(usrDir, "lib").absolutePath

        val pathEntries = ArrayList<String>()
        pathEntries.add(binDir)
        val javaHome = resolveJavaHome()
        val ldEntries = ArrayList<String>()
        ldEntries.add(libDir)
        if (javaHome != null) {
            env["JAVA_HOME"] = javaHome.absolutePath
            pathEntries.add(File(javaHome, "bin").absolutePath)
            listOf("lib", "lib/server", "lib/jli").forEach { sub ->
                val d = File(javaHome, sub)
                if (d.isDirectory) ldEntries.add(d.absolutePath)
            }
        }
        if (androidSdkDir.isDirectory) {
            env["ANDROID_HOME"] = androidSdkDir.absolutePath
            env["ANDROID_SDK_ROOT"] = androidSdkDir.absolutePath
            val cmdlineBin = File(androidSdkDir, "cmdline-tools/latest/bin")
            if (cmdlineBin.isDirectory) pathEntries.add(cmdlineBin.absolutePath)
            val platformTools = File(androidSdkDir, "platform-tools")
            if (platformTools.isDirectory) pathEntries.add(platformTools.absolutePath)
        }
        val aapt2Bin = File(usrDir, "bin/aapt2")
        if (aapt2Bin.exists()) {
            env["ORG_GRADLE_PROJECT_android_aapt2FromMavenOverride"] = aapt2Bin.absolutePath
        }
        pathEntries.add(sysPath)

        env["HOME"] = homeDir.absolutePath
        env["PREFIX"] = usrDir.absolutePath
        env["TMPDIR"] = tmpDir.absolutePath
        env["TERM"] = "xterm-256color"
        env["PATH"] = pathEntries.joinToString(":")
        env["LD_LIBRARY_PATH"] = ldEntries.joinToString(":")
        env["GRADLE_USER_HOME"] = File(homeDir, ".gradle").absolutePath
        env["NPM_CONFIG_CACHE"] = File(homeDir, ".npm").absolutePath
        env["PIP_CACHE_DIR"] = File(homeDir, ".cache/pip").absolutePath
        env["XDG_CONFIG_HOME"] = File(homeDir, ".config").absolutePath
        env["XDG_CACHE_HOME"] = File(homeDir, ".cache").absolutePath
        env["SHELL"] = if (File("/system/bin/sh").exists()) "/system/bin/sh" else bashBin.absolutePath

        // Termux v2 `termux-exec-ld-preload.so` honors custom prefix + data dir when configured here.
        env["TERMUX__PREFIX"] = usrDir.absolutePath
        env["TERMUX_APP__DATA_DIR"] = context.dataDir.absolutePath
        env["TERMUX_APP__LEGACY_DATA_DIR"] = "/data/data/${context.packageName}"
        env["TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE"] = "enable"

        findTermuxExecLib()?.let { lib ->
            env["LD_PRELOAD"] = lib.absolutePath
        }

        val certFile = ensureCaBundle()
        if (certFile != null) {
            env["SSL_CERT_FILE"] = certFile.absolutePath
            env["CURL_CA_BUNDLE"] = certFile.absolutePath
            env["GIT_SSL_CAINFO"] = certFile.absolutePath
            env["NODE_EXTRA_CA_CERTS"] = certFile.absolutePath
        }
        val certDirs = listOf("/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts")
            .filter { File(it).isDirectory }
        if (certDirs.isNotEmpty()) {
            env["SSL_CERT_DIR"] = certDirs.joinToString(":")
        }
        val gitCore = File(usrDir, "libexec/git-core")
        if (gitCore.exists()) {
            env["GIT_EXEC_PATH"] = gitCore.absolutePath
        }
        val gitTemplates = File(usrDir, "share/git-core/templates")
        if (gitTemplates.exists()) {
            env["GIT_TEMPLATE_DIR"] = gitTemplates.absolutePath
        }
        // Prevent bundled Termux git from failing on inaccessible /data/data/com.termux/files/usr/etc/gitconfig
        env["GIT_CONFIG_NOSYSTEM"] = "1"
        env["GIT_ATTR_NOSYSTEM"] = "1"
        val termInfo = File(usrDir, "share/terminfo")
        if (termInfo.exists()) {
            env["TERMINFO"] = termInfo.absolutePath
        }
        val shizukuPort = ShizukuBridge.startLocalRishServer()
        if (shizukuPort > 0) {
            env["AGY_SHIZUKU_PORT"] = shizukuPort.toString()
            env["AGY_SHIZUKU_TOKEN"] = ShizukuBridge.localToken
        }
        val browserPort = startLocalBrowserServer()
        startBrowserSpoolWatcher()
        if (browserPort > 0) {
            env["AGY_BROWSER_PORT"] = browserPort.toString()
            env["AGY_BRIDGE_TOKEN"] = bridgeToken
        }
        val proxyPort = startLocalProxyServer()
        if (proxyPort > 0 && System.getenv("HTTPS_PROXY").isNullOrBlank()) {
            val proxyUrl = "http://127.0.0.1:$proxyPort"
            env["HTTP_PROXY"] = proxyUrl
            env["HTTPS_PROXY"] = proxyUrl
            env["http_proxy"] = proxyUrl
            env["https_proxy"] = proxyUrl
            env["NO_PROXY"] = "127.0.0.1,localhost,::1"
            env["no_proxy"] = "127.0.0.1,localhost,::1"
        }
        if (!shimsInitialized || !File(usrDir, "bin/agy-pkg").exists() || !File(usrDir, "bin/xdg-open").exists()) {
            if (shizukuPort > 0) ensureRishShim()
            ensureBrowserShims()
            ensureClipboardShims()
            ensurePkgShim()
            ensureGitSafeDirectory()
            if (File(usrDir, "bin/apt").exists() || !File(usrDir, ".sanitized_v1").exists()) {
                sanitizeUsrBin()
            }
            ensureAgyWrapper()
            shimsInitialized = true
        }
        if (cachedPrivilegeLevel == null && !File(homeDir, ".gemini/config/GEMINI.md").exists()) {
            ensureMobileEnvRules(null)
        }
        val xdgOpenPath = File(usrDir, "bin/xdg-open").absolutePath
        env["BROWSER"] = xdgOpenPath
        env["GH_BROWSER"] = xdgOpenPath
        return env
    }

    private fun ensureGitSafeDirectory() {
        val gitCfg = File(homeDir, ".gitconfig")
        runCatching {
            val existing = if (gitCfg.exists()) gitCfg.readText(Charsets.UTF_8) else ""
            if (!existing.contains("directory = *")) {
                val addition = "\n[safe]\n\tdirectory = *\n"
                gitCfg.writeText(existing + addition, Charsets.UTF_8)
            }
        }
    }

    private fun ensureCaBundle(): File? {
        val certFile = File(usrDir, "etc/tls/cert.pem")
        if (certFile.exists() && certFile.length() > 1024L) {
            return certFile
        }
        return runCatching {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            val x509Tm = tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                ?: return@runCatching null
            val issuers = x509Tm.acceptedIssuers
            if (issuers.isEmpty()) return@runCatching null

            val encoder = Base64.getMimeEncoder(64, "\n".toByteArray(Charsets.US_ASCII))
            val pem = buildString {
                issuers.forEach { cert ->
                    append("-----BEGIN CERTIFICATE-----\n")
                    append(encoder.encodeToString(cert.encoded))
                    append("\n-----END CERTIFICATE-----\n")
                }
            }
            certFile.parentFile?.mkdirs()
            certFile.writeText(pem, Charsets.UTF_8)
            certFile
        }.getOrNull()
    }

    private val localBrowserPort = AtomicInteger(-1)
    private var localBrowserServer: ServerSocket? = null
    private val localProxyPort = AtomicInteger(-1)
    private var localProxyServer: ServerSocket? = null
    @Volatile
    private var browserSpoolThreadStarted = false

    private val _lastBrowserUrl = MutableStateFlow<String?>(null)
    val lastBrowserUrl: StateFlow<String?> = _lastBrowserUrl.asStateFlow()

    @Volatile
    private var lastOpenedBrowserUrl: String? = null

    @Volatile
    private var lastOpenedBrowserAtMs: Long = 0L

    fun openUrlInBrowser(url: String, force: Boolean = false): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return false
        }
        _lastBrowserUrl.value = trimmed
        val now = System.currentTimeMillis()
        if (!force && trimmed == lastOpenedBrowserUrl && (now - lastOpenedBrowserAtMs) < 4_000L) {
            return true
        }
        lastOpenedBrowserUrl = trimmed
        lastOpenedBrowserAtMs = now
        val launchIntent = {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            launchIntent()
        } else {
            Handler(Looper.getMainLooper()).post { launchIntent() }
            true
        }
    }

    @Synchronized
    fun startBrowserSpoolWatcher() {
        if (browserSpoolThreadStarted) return
        browserSpoolThreadStarted = true
        browserSpoolDir.mkdirs()
        Thread({
            while (true) {
                runCatching {
                    val files = browserSpoolDir.listFiles { f -> f.isFile && f.name.endsWith(".url") }
                    files?.sortedBy { it.lastModified() }?.forEach { file ->
                        val url = runCatching { file.readText(Charsets.UTF_8).trim() }.getOrNull()
                        runCatching { file.delete() }
                        if (!url.isNullOrBlank()) {
                            openUrlInBrowser(url)
                        }
                    }
                }
                runCatching { Thread.sleep(250L) }
            }
        }, "AgyBrowserSpool").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun startLocalBrowserServer(): Int {
        val existing = localBrowserPort.get()
        if (existing > 0 && localBrowserServer?.isClosed == false) return existing

        return runCatching {
            val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
            localBrowserServer = server
            val port = server.localPort
            localBrowserPort.set(port)
            Thread({
                while (!server.isClosed) {
                    runCatching {
                        val sock = server.accept()
                        Thread({ runCatching { handleBrowserClient(sock) } }, "AgyBrowserReq").start()
                    }
                }
            }, "AgyBrowserBridge").apply {
                isDaemon = true
                start()
            }
            port
        }.getOrDefault(-1)
    }

    /**
     * Local HTTP CONNECT tunnel that resolves hostnames via Android's system DNS (`netd`),
     * preventing Go's pure-Go DNS resolver from failing on `[::1]:53` when `/etc/resolv.conf` is absent.
     */
    @Synchronized
    fun startLocalProxyServer(): Int {
        val existing = localProxyPort.get()
        if (existing > 0 && localProxyServer?.isClosed == false) return existing

        return runCatching {
            val server = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
            localProxyServer = server
            val port = server.localPort
            localProxyPort.set(port)
            Thread({
                while (!server.isClosed) {
                    runCatching {
                        val sock = server.accept()
                        Thread({ runCatching { handleProxyClient(sock) } }, "AgyProxyReq").apply {
                            isDaemon = true
                            start()
                        }
                    }
                }
            }, "AgyProxyBridge").apply {
                isDaemon = true
                start()
            }
            port
        }.getOrDefault(-1)
    }

    private fun handleProxyClient(client: Socket) {
        client.use { cSock ->
            cSock.soTimeout = 30_000
            val inp = BufferedInputStream(cSock.getInputStream())
            val out = cSock.getOutputStream()

            val headerBytes = ByteArrayOutputStream()
            var prev3 = 0
            var prev2 = 0
            var prev1 = 0
            var foundEnd = false
            while (headerBytes.size() < 65_536) {
                val b = inp.read()
                if (b < 0) return
                headerBytes.write(b)
                if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                    foundEnd = true
                    break
                }
                prev3 = prev2
                prev2 = prev1
                prev1 = b
            }
            if (!foundEnd) {
                out.write("HTTP/1.1 431 Request Header Fields Too Large\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                return
            }

            val rawHeaders = headerBytes.toString(Charsets.ISO_8859_1.name())
            val lines = rawHeaders.split("\r\n")
            val requestLine = lines.firstOrNull()?.trim() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 3) {
                out.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                return
            }
            val method = parts[0].uppercase()
            val target = parts[1]

            if (method == "CONNECT") {
                val (host, port) = parseHostPort(target, 443) ?: run {
                    out.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                    return
                }
                val upstream = connectWithRetry(host, port)
                if (upstream == null) {
                    out.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                    return
                }
                upstream.use { uSock ->
                    cSock.keepAlive = true
                    uSock.keepAlive = true
                    cSock.soTimeout = 300_000
                    uSock.soTimeout = 300_000
                    out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                    relayBidirectional(cSock, uSock)
                }
            } else {
                val uri = runCatching { URI(target) }.getOrNull()
                val host = uri?.host
                if (uri == null || host.isNullOrBlank()) {
                    out.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                    return
                }
                val port = if (uri.port > 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
                val path = (uri.rawPath?.ifEmpty { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
                val upstream = connectWithRetry(host, port)
                if (upstream == null) {
                    out.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                    return
                }
                upstream.use { uSock ->
                    cSock.keepAlive = true
                    uSock.keepAlive = true
                    cSock.soTimeout = 120_000
                    uSock.soTimeout = 120_000
                    val uOut = uSock.getOutputStream()
                    val rebuilt = buildString {
                        append("$method $path ${parts[2]}\r\n")
                        lines.drop(1).forEach { h ->
                            if (h.isNotEmpty() &&
                                !h.startsWith("Proxy-Connection:", ignoreCase = true) &&
                                !h.startsWith("Connection:", ignoreCase = true)
                            ) {
                                append("$h\r\n")
                            }
                        }
                        append("Connection: close\r\n\r\n")
                    }
                    uOut.write(rebuilt.toByteArray(Charsets.ISO_8859_1))
                    uOut.flush()
                    relayBidirectional(cSock, uSock)
                }
            }
        }
    }

    private fun connectWithRetry(host: String, port: Int): Socket? {
        val backoffsMs = longArrayOf(0L, 400L, 1000L)
        for (attempt in backoffsMs.indices) {
            if (backoffsMs[attempt] > 0L) {
                runCatching { Thread.sleep(backoffsMs[attempt]) }
            }
            val addrs = runCatching {
                InetAddress.getAllByName(host).sortedBy { if (it is Inet4Address) 0 else 1 }
            }.getOrNull().orEmpty()
            for (addr in addrs) {
                val sock = Socket()
                val ok = runCatching {
                    sock.connect(InetSocketAddress(addr, port), 10_000)
                    true
                }.getOrDefault(false)
                if (ok) return sock
                runCatching { sock.close() }
            }
        }
        return null
    }

    private fun parseHostPort(hostPort: String, defaultPort: Int): Pair<String, Int>? {
        val trimmed = hostPort.trim()
        if (trimmed.startsWith("[")) {
            val closeIdx = trimmed.indexOf(']')
            if (closeIdx <= 1) return null
            val host = trimmed.substring(1, closeIdx)
            val port = trimmed.substring(closeIdx + 1).removePrefix(":").toIntOrNull() ?: defaultPort
            return host to port
        }
        val colonIdx = trimmed.lastIndexOf(':')
        return if (colonIdx > 0) {
            val host = trimmed.substring(0, colonIdx)
            val port = trimmed.substring(colonIdx + 1).toIntOrNull() ?: defaultPort
            host to port
        } else {
            trimmed to defaultPort
        }
    }

    private fun relayBidirectional(a: Socket, b: Socket) {
        val t1 = Thread({
            runCatching {
                val buf = ByteArray(16 * 1024)
                val inp = a.getInputStream()
                val out = b.getOutputStream()
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    out.flush()
                }
            }
            runCatching { b.shutdownOutput() }
        }, "AgyProxyUp").apply { isDaemon = true; start() }

        runCatching {
            val buf = ByteArray(16 * 1024)
            val inp = b.getInputStream()
            val out = a.getOutputStream()
            while (true) {
                val n = inp.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                out.flush()
            }
        }
        runCatching { a.shutdownOutput() }
        runCatching { t1.join(2_000L) }
    }

    private fun handleBrowserClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 10_000
            val inp = BufferedInputStream(sock.getInputStream())
            val headerBytes = readHttpHeaderBytes(inp) ?: return
            val lines = String(headerBytes, Charsets.US_ASCII).split("\r\n")
            val requestLine = lines.firstOrNull() ?: return
            var contentLength = 0
            var providedToken = ""
            for (line in lines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val key = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                when {
                    key.equals("Content-Length", ignoreCase = true) -> {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                    key.equals("X-Agy-Token", ignoreCase = true) -> {
                        providedToken = value
                    }
                }
            }

            if (!MessageDigest.isEqual(
                    providedToken.toByteArray(Charsets.UTF_8),
                    bridgeToken.toByteArray(Charsets.UTF_8)
                )
            ) {
                val forbidden = "Forbidden\n".toByteArray(Charsets.UTF_8)
                val hdr = "HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${forbidden.size}\r\nConnection: close\r\n\r\n"
                sock.getOutputStream().write(hdr.toByteArray(Charsets.US_ASCII))
                sock.getOutputStream().write(forbidden)
                sock.getOutputStream().flush()
                return
            }

            val bodyBytes = readExactBytes(inp, contentLength.coerceIn(0, 128_000))
            val body = String(bodyBytes, Charsets.UTF_8).trim()
            val reqParts = requestLine.split(" ")
            val method = reqParts.getOrNull(0)?.uppercase() ?: "GET"
            val reqPath = reqParts.getOrNull(1) ?: "/"

            val (status, msg) = when {
                reqPath.startsWith("/clipboard") -> {
                    if (method == "POST") {
                        setAndroidClipboard(body)
                        "HTTP/1.1 200 OK" to "ok\n"
                    } else {
                        "HTTP/1.1 200 OK" to getAndroidClipboard()
                    }
                }
                reqPath.startsWith("/pkg/") -> {
                    sock.soTimeout = 300_000
                    handlePkgBridgeRequest(reqPath, body)
                }
                else -> {
                    val url = if (method == "POST") {
                        body
                    } else {
                        val query = reqPath.substringAfter("url=", "")
                        URLDecoder.decode(query, "UTF-8")
                    }
                    val ok = openUrlInBrowser(url)
                    if (ok) "HTTP/1.1 200 OK" to "ok\n" else "HTTP/1.1 400 Bad Request" to "invalid url\n"
                }
            }

            val bytes = msg.toByteArray(Charsets.UTF_8)
            val header = "$status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            sock.getOutputStream().write(header.toByteArray(Charsets.US_ASCII))
            sock.getOutputStream().write(bytes)
            sock.getOutputStream().flush()
        }
    }

    private fun readHttpHeaderBytes(inp: BufferedInputStream): ByteArray? {
        val out = ByteArrayOutputStream(512)
        var state = 0
        while (out.size() < 32_768) {
            val b = inp.read()
            if (b == -1) return null
            out.write(b)
            state = when (state) {
                0 -> if (b == '\r'.code) 1 else 0
                1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
                2 -> if (b == '\r'.code) 3 else 0
                3 -> if (b == '\n'.code) return out.toByteArray() else if (b == '\r'.code) 1 else 0
                else -> 0
            }
        }
        return null
    }

    private fun readExactBytes(inp: BufferedInputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val buf = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = inp.read(buf, off, length - off)
            if (n <= 0) break
            off += n
        }
        return if (off == length) buf else buf.copyOf(off)
    }

    private fun handlePkgBridgeRequest(reqPath: String, body: String): Pair<String, String> {
        return runCatching {
            when {
                reqPath == "/pkg/list" -> {
                    val installed = listInstalledPackages().sorted().joinToString("\n")
                    "HTTP/1.1 200 OK" to (if (installed.isEmpty()) "(none)\n" else "$installed\n")
                }
                reqPath.startsWith("/pkg/search") -> {
                    val query = body.trim().lowercase()
                    val index = runBlocking { fetchPackagesIndex() }
                    val matches = index.values
                        .filter { query.isBlank() || it.name.lowercase().contains(query) }
                        .sortedBy { it.name }
                        .take(50)
                        .joinToString("\n") { "${it.name} (${it.version})" }
                    "HTTP/1.1 200 OK" to (if (matches.isEmpty()) "No packages matching '$query'\n" else "$matches\n")
                }
                reqPath == "/pkg/install" -> {
                    val pkgs = body.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
                    if (pkgs.isEmpty()) {
                        "HTTP/1.1 400 Bad Request" to "Specify one or more package names\n"
                    } else {
                        val res = runBlocking { installPackages(pkgs) }
                        res.fold(
                            onSuccess = { installed ->
                                "HTTP/1.1 200 OK" to "Installed: ${installed.joinToString(", ")}\n"
                            },
                            onFailure = { err ->
                                "HTTP/1.1 500 Install Failed" to "Error: ${err.message}\n"
                            }
                        )
                    }
                }
                reqPath == "/pkg/install-android-sdk" -> {
                    val res = runBlocking { installAndroidBuildSdk() }
                    res.fold(
                        onSuccess = {
                            "HTTP/1.1 200 OK" to "Android Build SDK ready (JAVA_HOME=${resolveJavaHome()?.absolutePath}, ANDROID_HOME=${androidSdkDir.absolutePath})\n"
                        },
                        onFailure = { err ->
                            "HTTP/1.1 500 SDK Install Failed" to "Error: ${err.message}\n"
                        }
                    )
                }
                else -> "HTTP/1.1 404 Not Found" to "Unknown /pkg endpoint\n"
            }
        }.getOrElse { err ->
            "HTTP/1.1 500 Internal Error" to "${err.message ?: "pkg error"}\n"
        }
    }

    private fun setAndroidClipboard(text: String) {
        if (text.isEmpty()) return
        val applyClip = {
            runCatching {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("CLI output", text))
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) applyClip()
        else Handler(Looper.getMainLooper()).post { applyClip() }
    }

    private fun getAndroidClipboard(): String {
        val readClip = {
            runCatching {
                val cm = context.getSystemService(ClipboardManager::class.java)
                val clip = cm?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                } else ""
            }.getOrDefault("")
        }
        if (Looper.myLooper() == Looper.getMainLooper()) return readClip()
        var out = ""
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            out = readClip()
            latch.countDown()
        }
        runCatching { latch.await(2, TimeUnit.SECONDS) }
        return out
    }

    private fun ensureBrowserShims() {
        val defaultSpool = browserSpoolDir.absolutePath
        val script = """#!/system/bin/sh
URL=""
for arg in "${'$'}@"; do
  case "${'$'}arg" in
    http://*|https://*)
      URL="${'$'}arg"
      break
      ;;
  esac
done
if [ -z "${'$'}URL" ]; then
  echo "Usage: ${'$'}0 <url>" >&2
  exit 1
fi
if [ -n "${'$'}TMPDIR" ]; then
  SPOOL_DIR="${'$'}TMPDIR/browser_open"
else
  SPOOL_DIR="$defaultSpool"
fi
mkdir -p "${'$'}SPOOL_DIR" 2>/dev/null
TMP_FILE="${'$'}SPOOL_DIR/.req_${'$'}${'$'}.tmp"
URL_FILE="${'$'}SPOOL_DIR/req_${'$'}${'$'}.url"
if printf '%s\n' "${'$'}URL" > "${'$'}TMP_FILE" 2>/dev/null && mv "${'$'}TMP_FILE" "${'$'}URL_FILE" 2>/dev/null; then
  exit 0
fi
if [ -n "${'$'}AGY_BROWSER_PORT" ]; then
  CURL_BIN="${'$'}{PREFIX}/bin/curl"
  if [ -x "${'$'}CURL_BIN" ]; then
    "${'$'}CURL_BIN" --noproxy '*' -sS -f -H "X-Agy-Token: ${'$'}AGY_BRIDGE_TOKEN" -X POST --data-binary "${'$'}URL" "http://127.0.0.1:${'$'}AGY_BROWSER_PORT/open" >/dev/null
    exit ${'$'}?
  fi
  NODE_BIN="${'$'}{PREFIX}/bin/node"
  if [ -x "${'$'}NODE_BIN" ]; then
    exec "${'$'}NODE_BIN" -e 'const http=require("http");const req=http.request("http://127.0.0.1:"+process.env.AGY_BROWSER_PORT+"/open",{method:"POST",headers:{"X-Agy-Token":process.env.AGY_BRIDGE_TOKEN||""}},res=>{res.resume();res.on("end",()=>process.exit(res.statusCode===200?0:1));});req.on("error",()=>process.exit(1));req.end(process.argv[1]);' "${'$'}URL"
  fi
fi
echo "Browser bridge not available for: ${'$'}URL" >&2
exit 1
"""
        listOf(
            "xdg-open",
            "termux-open-url",
            "open",
            "sensible-browser",
            "x-www-browser",
            "www-browser",
            "gnome-open",
            "gio"
        ).forEach { name ->
            val shimFile = File(usrDir, "bin/$name")
            runCatching {
                shimFile.parentFile?.mkdirs()
                if (!shimFile.exists() || shimFile.readText() != script) {
                    shimFile.writeText(script)
                }
                chmod755(shimFile)
            }
        }
    }

    private fun ensureClipboardShims() {
        val setScript = """#!/system/bin/sh
if [ -z "${'$'}AGY_BROWSER_PORT" ]; then
  exit 1
fi
DATA=""
if [ ! -t 0 ]; then
  DATA="${'$'}(cat)"
elif [ "${'$'}#" -gt 0 ]; then
  DATA="${'$'}*"
fi
CURL_BIN="${'$'}{PREFIX}/bin/curl"
if [ -x "${'$'}CURL_BIN" ]; then
  printf '%s' "${'$'}DATA" | "${'$'}CURL_BIN" --noproxy '*' -sS -f -H "X-Agy-Token: ${'$'}AGY_BRIDGE_TOKEN" -X POST --data-binary @- "http://127.0.0.1:${'$'}AGY_BROWSER_PORT/clipboard" >/dev/null
  exit ${'$'}?
fi
NODE_BIN="${'$'}{PREFIX}/bin/node"
if [ -x "${'$'}NODE_BIN" ]; then
  printf '%s' "${'$'}DATA" | exec "${'$'}NODE_BIN" -e 'const fs=require("fs"),http=require("http");const d=fs.readFileSync(0,"utf8");const r=http.request("http://127.0.0.1:"+process.env.AGY_BROWSER_PORT+"/clipboard",{method:"POST",headers:{"X-Agy-Token":process.env.AGY_BRIDGE_TOKEN||""}},res=>{res.resume();res.on("end",()=>process.exit(res.statusCode===200?0:1));});r.on("error",()=>process.exit(1));r.end(d);'
fi
exit 1
"""
        val getScript = """#!/system/bin/sh
if [ -z "${'$'}AGY_BROWSER_PORT" ]; then
  exit 1
fi
CURL_BIN="${'$'}{PREFIX}/bin/curl"
if [ -x "${'$'}CURL_BIN" ]; then
  exec "${'$'}CURL_BIN" --noproxy '*' -sS -f -H "X-Agy-Token: ${'$'}AGY_BRIDGE_TOKEN" "http://127.0.0.1:${'$'}AGY_BROWSER_PORT/clipboard"
fi
NODE_BIN="${'$'}{PREFIX}/bin/node"
if [ -x "${'$'}NODE_BIN" ]; then
  exec "${'$'}NODE_BIN" -e 'require("http").get("http://127.0.0.1:"+process.env.AGY_BROWSER_PORT+"/clipboard",{headers:{"X-Agy-Token":process.env.AGY_BRIDGE_TOKEN||""}},res=>{res.pipe(process.stdout);}).on("error",()=>process.exit(1));'
fi
exit 1
"""
        listOf("termux-clipboard-set", "xclip", "xsel", "wl-copy").forEach { name ->
            val f = File(usrDir, "bin/$name")
            runCatching {
                f.parentFile?.mkdirs()
                if (!f.exists() || f.readText() != setScript) f.writeText(setScript)
                chmod755(f)
            }
        }
        val getFile = File(usrDir, "bin/termux-clipboard-get")
        runCatching {
            getFile.parentFile?.mkdirs()
            if (!getFile.exists() || getFile.readText() != getScript) getFile.writeText(getScript)
            chmod755(getFile)
        }
    }

    fun ensureMobileEnvRules(privilegeLevel: String? = null) {
        val marker = "<!-- antigravity-mobile-env -->"
        val contextBlock = buildDeviceContextPrompt(privilegeLevel ?: "unknown")
        val block = "$marker\n$contextBlock\n$marker\n"
        val targets = listOf(
            File(homeDir, ".gemini/config/GEMINI.md"),
            File(homeDir, ".gemini/GEMINI.md")
        )
        val markerRegex = Regex(
            "${Regex.escape(marker)}[\\s\\S]*?${Regex.escape(marker)}\\r?\\n?"
        )
        for (geminiFile in targets) {
            runCatching {
                geminiFile.parentFile?.mkdirs()
                val existing = if (geminiFile.exists()) geminiFile.readText(Charsets.UTF_8) else ""
                val updated = when {
                    existing.isBlank() -> block
                    markerRegex.containsMatchIn(existing) -> existing.replace(markerRegex, block)
                    else -> "$existing\n\n$block"
                }
                if (updated != existing) {
                    geminiFile.writeText(updated, Charsets.UTF_8)
                }
            }
        }
    }

    private fun ensurePkgShim() {
        val pkgFile = File(usrDir, "bin/agy-pkg")
        val script = """#!/system/bin/sh
if [ -z "${'$'}AGY_BROWSER_PORT" ]; then
  echo "agy-pkg bridge port not configured" >&2
  exit 126
fi
CMD="${'$'}1"
[ -n "${'$'}CMD" ] && shift
ARGS="${'$'}*"
CURL_BIN="${'$'}{PREFIX}/bin/curl"
case "${'$'}CMD" in
  list)
    "${'$'}CURL_BIN" --noproxy '*' -sS -f -H "X-Agy-Token: ${'$'}AGY_BRIDGE_TOKEN" "http://127.0.0.1:${'$'}AGY_BROWSER_PORT/pkg/list"
    exit ${'$'}?
    ;;
  search)
    printf '%s' "${'$'}ARGS" | "${'$'}CURL_BIN" --noproxy '*' -sS -f -H "X-Agy-Token: ${'$'}AGY_BRIDGE_TOKEN" -X POST --data-binary @- "http://127.0.0.1:${'$'}AGY_BROWSER_PORT/pkg/search"
    exit ${'$'}?
    ;;
  install)
    printf '%s' "${'$'}ARGS" | "${'$'}CURL_BIN" --noproxy '*' -sS -f -H "X-Agy-Token: ${'$'}AGY_BRIDGE_TOKEN" -X POST --data-binary @- "http://127.0.0.1:${'$'}AGY_BROWSER_PORT/pkg/install"
    exit ${'$'}?
    ;;
  install-android-sdk)
    "${'$'}CURL_BIN" --noproxy '*' -sS -f -H "X-Agy-Token: ${'$'}AGY_BRIDGE_TOKEN" -X POST --data-binary "" "http://127.0.0.1:${'$'}AGY_BROWSER_PORT/pkg/install-android-sdk"
    exit ${'$'}?
    ;;
  *)
    echo "Usage: agy-pkg <list|search <query>|install <pkg...>|install-android-sdk>" >&2
    exit 1
    ;;
esac
"""
        runCatching {
            pkgFile.parentFile?.mkdirs()
            if (!pkgFile.exists() || pkgFile.readText() != script) {
                pkgFile.writeText(script)
            }
            chmod755(pkgFile)
            val pkgAlias = File(usrDir, "bin/pkg")
            runCatching { Files.deleteIfExists(pkgAlias.toPath()) }
            runCatching { Os.symlink(pkgFile.absolutePath, pkgAlias.absolutePath) }
        }
    }

    private fun ensureRishShim() {
        val rishFile = File(usrDir, "bin/rish")
        val script = """#!/system/bin/sh
if [ -z "${'$'}AGY_SHIZUKU_PORT" ]; then
  echo "Shizuku bridge port not configured" >&2
  exit 126
fi
if [ "${'$'}1" = "-c" ]; then
  shift
  CMD="${'$'}*"
elif [ "${'$'}#" -gt 0 ]; then
  CMD="${'$'}*"
else
  CMD="${'$'}(cat)"
fi
CURL_BIN="${'$'}{PREFIX}/bin/curl"
if [ -x "${'$'}CURL_BIN" ]; then
  HDR_FILE="${'$'}{TMPDIR:-/tmp}/rish_hdr_${'$'}${'$'}"
  printf '%s' "${'$'}CMD" | "${'$'}CURL_BIN" --noproxy '*' -sS -D "${'$'}HDR_FILE" -H "X-Agy-Token: ${'$'}AGY_SHIZUKU_TOKEN" -X POST --data-binary @- "http://127.0.0.1:${'$'}AGY_SHIZUKU_PORT/exec"
  RC=${'$'}?
  CODE=0
  if [ -f "${'$'}HDR_FILE" ]; then
    PARSED="${'$'}(grep -i '^X-Exit-Code:' "${'$'}HDR_FILE" | tail -n 1 | tr -dc '0-9')"
    rm -f "${'$'}HDR_FILE"
    [ -n "${'$'}PARSED" ] && CODE="${'$'}PARSED" || CODE=${'$'}RC
  else
    CODE=${'$'}RC
  fi
  exit "${'$'}CODE"
fi
NODE_BIN="${'$'}{PREFIX}/bin/node"
if [ -x "${'$'}NODE_BIN" ]; then
  exec "${'$'}NODE_BIN" -e 'const http=require("http");const req=http.request("http://127.0.0.1:"+process.env.AGY_SHIZUKU_PORT+"/exec",{method:"POST",headers:{"X-Agy-Token":process.env.AGY_SHIZUKU_TOKEN||""}},res=>{res.pipe(process.stdout);res.on("end",()=>{const c=parseInt(res.headers["x-exit-code"],10);process.exit(Number.isFinite(c)?c:(res.statusCode===200?0:1));});});req.on("error",e=>{console.error(e.message);process.exit(126);});req.end(process.argv[1]);' "${'$'}CMD"
fi
echo "Neither curl nor node found for rish shim" >&2
exit 127
"""
        runCatching {
            rishFile.parentFile?.mkdirs()
            if (!rishFile.exists() || rishFile.readText() != script) {
                rishFile.writeText(script)
            }
            chmod755(rishFile)
        }
    }

    suspend fun detectPrivilegeLevel(): String = withContext(Dispatchers.IO) {
        val detected = when {
            runQuickCheck(listOf("su", "-c", "id"), "uid=0", timeoutSec = 3L) -> "root"
            ShizukuBridge.hasPermission() -> {
                ShizukuBridge.warmUp(context)
                "shizuku"
            }
            runQuickCheck(listOf("rish", "-c", "id"), "uid=2000", timeoutSec = 3L, env = buildChildEnvironment()) -> "shizuku"
            else -> "none"
        }
        cachedPrivilegeLevel = detected
        detected
    }

    fun buildDeviceContextPrompt(privilegeLevel: String): String {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val javaHome = resolveJavaHome()
        val hasSdk = isAndroidSdkInstalled()
        val tools = buildList {
            if (bashBin.exists()) add("bash")
            if (gitBin.exists()) add("git")
            if (File(usrDir, "bin/gh").exists()) add("gh")
            if (nodeBin.exists()) add("node")
            if (npmBin.exists()) add("npm")
            if (File(usrDir, "bin/python3").exists()) add("python3")
            if (File(usrDir, "bin/rg").exists()) add("rg")
            if (File(usrDir, "bin/jq").exists()) add("jq")
            if (File(usrDir, "bin/curl").exists()) add("curl")
            if (javaHome != null) add("java")
            if (File(usrDir, "bin/aapt2").exists()) add("aapt2")
            if (File(usrDir, "bin/apksigner").exists()) add("apksigner")
            if (File(usrDir, "bin/rish").exists()) add("rish")
            if (File(usrDir, "bin/agy-pkg").exists()) add("agy-pkg")
        }
        val privDesc = when (privilegeLevel) {
            "root" -> "root (`su -c ...`, uid=0)"
            "shizuku" -> "Shizuku (`rish -c ...`, uid=2000 ADB-level privileges; use `rish -c \"screencap -p ...\"` etc. for privileged Android commands)"
            "unknown" -> "detected per session (`su -c` if rooted, `rish -c` if Shizuku is granted, otherwise app sandbox)"
            else -> if (ShizukuBridge.isBinderAlive()) {
                "unprivileged app sandbox (Shizuku is running but permission is not granted to Agy Mobile yet)"
            } else {
                "unprivileged app sandbox (no root or active Shizuku)"
            }
        }
        return buildString {
            appendLine("## Agy Mobile Standalone Runtime Environment")
            appendLine("- **Host App:** Agy Mobile v${BuildConfig.VERSION_NAME} built-in Standalone Engine on Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) — **NOT** Termux (`com.termux`).")
            appendLine("- **Device:** ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} ($abi)")
            appendLine("- **Important:** `TERMUX__*` environment variables exist only for the bundled `libtermux-exec` binary loader. Do NOT attempt to access `/data/data/com.termux/...` or assume the Termux app is installed. `GIT_CONFIG_NOSYSTEM=1` and `GIT_ATTR_NOSYSTEM=1` are pre-configured for `git`.")
            appendLine("- **Package Manager (`agy-pkg` / `pkg`):** Use `agy-pkg search <query>`, `agy-pkg install <pkg...>`, `agy-pkg list`, or `agy-pkg install-android-sdk` to install ARM64 packages directly into `${usrDir.absolutePath}`.")
            if (hasSdk) {
                appendLine("- **Android APK Build Toolchain (READY):** `JAVA_HOME=${javaHome?.absolutePath}`, `ANDROID_HOME=${androidSdkDir.absolutePath}`, and native ARM64 `aapt2` (`ORG_GRADLE_PROJECT_android_aapt2FromMavenOverride`) are configured. Local `./gradlew assembleDebug` / `assembleRelease` builds work directly on this device.")
            } else {
                appendLine("- **Android APK Build Toolchain:** Not installed yet. You can install JDK 17 + Android SDK + ARM64 `aapt2` anytime via `agy-pkg install-android-sdk` (or use `gh` / GitHub Actions if the user prefers CI).")
            }
            appendLine("- **Privilege backend:** $privDesc")
            appendLine("- **Installed CLI tools in PATH:** ${if (tools.isEmpty()) "agy only" else tools.joinToString(", ")} (prefix: `${usrDir.absolutePath}`)")
            appendLine("- **Engine HOME:** `${homeDir.absolutePath}` (shared storage: `/storage/emulated/0`)")
            appendLine("- **Browser & Clipboard bridges:** `BROWSER`, `GH_BROWSER`, `xdg-open`, and `termux-open-url` are natively bridged to open URLs in the user's Android browser (**never** override `BROWSER=true` in interactive auth flows like `gh auth login --web`). `termux-clipboard-set`, `termux-clipboard-get`, `xclip`, `xsel`, and `wl-copy` are natively bridged to the Android clipboard.")
            appendLine("- **Sandbox note:** `run_command` executes in the app sandbox unless prefixed with `su -c` (root) or `rish -c` (Shizuku).")
        }.trim()
    }

    /**
     * Removes non-functional Termux package-manager binaries (`apt*`, `dpkg*`, `pkg`) and rewrites
     * any hardcoded `/data/data/com.termux/files/usr/...` symlinks in `usr/bin` to `usrDir`.
     */
    private fun sanitizeUsrBin() {
        val binDir = File(usrDir, "bin")
        if (!binDir.isDirectory) return
        val brokenPkgBins = setOf(
            "apt", "apt-get", "apt-cache", "apt-config", "apt-key", "apt-mark",
            "dpkg", "dpkg-deb", "dpkg-divert", "dpkg-query", "dpkg-realpath", "dpkg-split", "dpkg-trigger",
            "pkg"
        )
        val legacyPrefix = "/data/data/com.termux/files/usr/"
        binDir.listFiles()?.forEach { f ->
            if (f.name in brokenPkgBins) {
                runCatching { f.delete() }
                return@forEach
            }
            val target = runCatching { Os.readlink(f.absolutePath) }.getOrNull()
            if (target != null && target.startsWith(legacyPrefix)) {
                val fixed = File(usrDir, target.removePrefix(legacyPrefix)).absolutePath
                runCatching {
                    f.delete()
                    Os.symlink(fixed, f.absolutePath)
                }
            }
        }
        runCatching { File(usrDir, ".sanitized_v1").writeText("ok\n") }
    }

    private fun runQuickCheck(
        cmd: List<String>,
        expectedSubstring: String,
        timeoutSec: Long = 3L,
        env: Map<String, String>? = null
    ): Boolean {
        return runCatching {
            val pb = ProcessBuilder(cmd).redirectErrorStream(true)
            if (env != null) pb.environment().putAll(env)
            val proc = pb.start()
            runCatching { proc.outputStream.close() }
            val outBuf = StringBuilder()
            val reader = Thread {
                runCatching {
                    proc.inputStream.bufferedReader().use { r ->
                        outBuf.append(r.readText())
                    }
                }
            }.apply { isDaemon = true; start() }
            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@runCatching false
            }
            reader.join(500L)
            proc.exitValue() == 0 && outBuf.toString().contains(expectedSubstring)
        }.getOrDefault(false)
    }

    fun extractBundledBridgeAssets() {
        ensureBaseDirs()
        copyAssetDir("bridge", bridgeDir)
    }

    private fun copyAssetDir(assetPath: String, targetDir: File) {
        val items = context.assets.list(assetPath) ?: return
        if (items.isEmpty()) {
            targetDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetDir).use { output -> input.copyTo(output) }
            }
            return
        }
        targetDir.mkdirs()
        for (item in items) {
            copyAssetDir("$assetPath/$item", File(targetDir, item))
        }
    }

    suspend fun installOrUpdateAgy(
        forceUpdate: Boolean = false,
        onProgress: (status: String, progress: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            ensureBaseDirs()
            extractBundledBridgeAssets()
            ensureAgyWrapper()
            val arch = detectArch()

            onProgress("Checking ${arch.agyManifest} release manifest…", 0.05f)
            val manifestUrl =
                "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/${arch.agyManifest}.json"
            val manifestReq = Request.Builder().url(manifestUrl).get().build()
            val manifestJson = httpClient.newCall(manifestReq).execute().use { resp ->
                if (!resp.isSuccessful) error("Manifest HTTP ${resp.code}")
                JSONObject(resp.body?.string().orEmpty())
            }

            val version = manifestJson.optString("version")
            val url = manifestJson.optString("url")
            val expectedSha512 = manifestJson.optString("sha512")
            if (url.isBlank() || expectedSha512.isBlank()) {
                error("Invalid agy release manifest")
            }

            if (!forceUpdate && isAgyInstalled() && installedAgyVersion() == version) {
                ensureAgyWrapper()
                onProgress("agy $version already up to date", 1.0f)
                return@runCatching agyBin
            }

            val archiveFile = File(tmpDir, "agy.tar.gz")
            onProgress("Downloading agy $version (${arch.abi})…", 0.10f)
            val actualSha = downloadWithSha512(url, archiveFile) { frac ->
                onProgress("Downloading agy $version (${(frac * 100).toInt()}%)…", 0.10f + frac * 0.70f)
            }

            if (!actualSha.equals(expectedSha512, ignoreCase = true)) {
                archiveFile.delete()
                error("SHA-512 mismatch for downloaded agy archive")
            }

            onProgress("Extracting agy binary…", 0.85f)
            val tmpBin = File(tmpDir, "antigravity.extracted")
            extractSingleFileFromTarGz(archiveFile, "antigravity", tmpBin)
            archiveFile.delete()

            if (!tmpBin.exists() || tmpBin.length() < 1_000_000L) {
                error("Extracted agy binary missing or truncated")
            }

            agyRealBin.parentFile?.mkdirs()
            val staged = File(agyRealBin.parentFile, "agy.bin.new")
            if (staged.exists()) staged.delete()
            if (!tmpBin.renameTo(staged)) {
                tmpBin.copyTo(staged, overwrite = true)
                tmpBin.delete()
            }
            chmod755(staged)
            Files.move(staged.toPath(), agyRealBin.toPath(), StandardCopyOption.REPLACE_EXISTING)
            ensureAgyWrapper()
            agyVersionFile.writeText(version)

            onProgress("Standalone agy $version ready (${arch.abi})", 1.0f)
            agyBin
        }
    }

    suspend fun installPortableToolchain(
        onProgress: (status: String, progress: Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureBaseDirs()
            extractBundledBridgeAssets()
            val arch = detectArch()

            onProgress("Fetching ${arch.termuxBootstrapArch} Linux runtime metadata…", 0.03f)
            val relReq = Request.Builder()
                .url("https://api.github.com/repos/termux/termux-packages/releases/latest")
                .header("User-Agent", "AgyMobile/1.3.0")
                .get()
                .build()
            val relJson = httpClient.newCall(relReq).execute().use { resp ->
                if (!resp.isSuccessful) error("Bootstrap release HTTP ${resp.code}")
                JSONObject(resp.body?.string().orEmpty())
            }

            val assets = relJson.optJSONArray("assets") ?: JSONArray()
            val targetAssetName = "bootstrap-${arch.termuxBootstrapArch}.zip"
            var bootstrapUrl = ""
            for (i in 0 until assets.length()) {
                val obj = assets.optJSONObject(i) ?: continue
                if (obj.optString("name") == targetAssetName) {
                    bootstrapUrl = obj.optString("browser_download_url")
                    break
                }
            }
            if (bootstrapUrl.isBlank()) error("Could not find $targetAssetName in latest release")

            val bootstrapZip = File(tmpDir, targetAssetName)
            downloadToFile(bootstrapUrl, bootstrapZip) { frac ->
                onProgress(
                    "Downloading $targetAssetName (${(frac * 100).toInt()}%)…",
                    0.05f + frac * 0.35f
                )
            }

            onProgress("Unpacking portable Linux prefix…", 0.42f)
            unzipInto(bootstrapZip, usrDir)
            bootstrapZip.delete()
            applySymlinksTxt(File(usrDir, "SYMLINKS.txt"), usrDir)
            makeTreeExecutable(usrDir)

            onProgress("Resolving git & nodejs-lts dependencies…", 0.50f)
            installPackagesInternal(listOf("git", "nodejs-lts")) { status, frac ->
                onProgress(status, 0.50f + frac * 0.35f)
            }

            if (nodeBin.exists() && npmBin.exists() && !File(bridgeDir, "node_modules").exists()) {
                onProgress("Installing android-control-mcp Node dependencies…", 0.90f)
                installBridgeNodeModules()
            }

            toolchainMarker.writeText("ok:${arch.abi}\n")
            shimsInitialized = false
            onProgress("Portable Linux toolchain (bash, git, node, npm) ready!", 1.0f)
        }
    }

    private fun listInstalledPackages(): Set<String> {
        val set = HashSet<String>()
        set.addAll(parseInstalledDpkgPackages(File(usrDir, "var/lib/dpkg/status")))
        if (installedPkgsFile.exists()) {
            runCatching {
                installedPkgsFile.readLines().map { it.trim() }.filter { it.isNotBlank() }.forEach { set.add(it) }
            }
        }
        return set
    }

    private fun recordInstalledPackages(names: Collection<String>) {
        if (names.isEmpty()) return
        val merged = (listInstalledPackages() + names).sorted()
        runCatching {
            installedPkgsFile.parentFile?.mkdirs()
            installedPkgsFile.writeText(merged.joinToString("\n") + "\n", Charsets.UTF_8)
        }
    }

    private fun fetchPackagesIndex(): Map<String, DebPkg> {
        val arch = detectArch()
        val repoBase = "https://packages.termux.dev/apt/termux-main"
        return httpClient.newCall(
            Request.Builder()
                .url("$repoBase/dists/stable/main/binary-${arch.termuxBootstrapArch}/Packages")
                .header("User-Agent", "AgyMobile/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) error("Packages index HTTP ${resp.code}")
            val body = resp.body ?: error("Empty Packages response")
            parseDebianPackagesStream(body.charStream().buffered())
        }
    }

    private fun installPackagesInternal(
        packages: List<String>,
        onProgress: (status: String, progress: Float) -> Unit = { _, _ -> }
    ): List<String> {
        val repoBase = "https://packages.termux.dev/apt/termux-main"
        val pkgIndex = fetchPackagesIndex()
        val missing = packages.filter { it !in pkgIndex }
        if (missing.isNotEmpty()) {
            error("Unknown package(s): ${missing.joinToString(", ")}")
        }
        val installed = listInstalledPackages()
        val toInstall = resolveDebDependencies(packages, pkgIndex, installed)
        if (toInstall.isEmpty()) {
            return emptyList()
        }
        val env = buildChildEnvironment()
        toInstall.forEachIndexed { idx, pkg ->
            val frac = idx.toFloat() / toInstall.size.coerceAtLeast(1)
            onProgress("Installing ${pkg.name} ${pkg.version} (${idx + 1}/${toInstall.size})…", frac)
            val debFile = File(tmpDir, "${pkg.name}.deb")
            try {
                downloadToFile("$repoBase/${pkg.filename}", debFile) { _ -> }
                extractDebIntoUsr(debFile, env)
            } finally {
                debFile.delete()
            }
        }
        makeTreeExecutable(usrDir)
        sanitizeUsrBin()
        ensureAgyWrapper()
        ensureBrowserShims()
        ensureClipboardShims()
        ensurePkgShim()
        val shFile = File(usrDir, "bin/sh")
        if (!shFile.exists() && bashBin.exists()) {
            runCatching { Os.symlink("bash", shFile.absolutePath) }
        }
        val installedNames = toInstall.map { it.name }
        recordInstalledPackages(installedNames)
        shimsInitialized = false
        return installedNames
    }

    suspend fun installPackages(
        packages: List<String>,
        onProgress: (status: String, progress: Float) -> Unit = { _, _ -> }
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureBaseDirs()
            installPackagesInternal(packages, onProgress)
        }
    }

    suspend fun installAndroidBuildSdk(
        onProgress: (status: String, progress: Float) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureBaseDirs()
            onProgress("Installing OpenJDK 17, aapt2, apksigner & zip tools…", 0.05f)
            installPackagesInternal(
                listOf("openjdk-17", "aapt2", "aapt", "apksigner", "zip", "unzip")
            ) { msg, frac ->
                onProgress(msg, 0.05f + frac * 0.45f)
            }

            val sdkManagerBin = File(androidSdkDir, "cmdline-tools/latest/bin/sdkmanager")
            if (!sdkManagerBin.exists()) {
                val toolsZip = File(tmpDir, "commandlinetools-linux.zip")
                val toolsUrl = "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
                try {
                    onProgress("Downloading Android SDK command-line tools…", 0.52f)
                    downloadToFile(toolsUrl, toolsZip) { frac ->
                        onProgress("Downloading cmdline-tools (${(frac * 100).toInt()}%)…", 0.52f + frac * 0.18f)
                    }
                    val stage = File(tmpDir, "cmdline_stage").apply {
                        deleteRecursively()
                        mkdirs()
                    }
                    unzipInto(toolsZip, stage)
                    val extracted = File(stage, "cmdline-tools")
                    val targetLatest = File(androidSdkDir, "cmdline-tools/latest")
                    targetLatest.parentFile?.mkdirs()
                    if (targetLatest.exists()) targetLatest.deleteRecursively()
                    if (extracted.isDirectory) {
                        extracted.copyRecursively(targetLatest, overwrite = true)
                    } else {
                        error("cmdline-tools directory missing in archive")
                    }
                    stage.deleteRecursively()
                    makeTreeExecutable(targetLatest)
                } finally {
                    toolsZip.delete()
                }
            }

            val env = buildChildEnvironment()
            onProgress("Accepting Android SDK licenses…", 0.75f)
            val yesInput = "y\n".repeat(32)
            val licProc = ProcessBuilder(
                "/system/bin/sh",
                "-c",
                "\"${sdkManagerBin.absolutePath}\" --sdk_root=\"${androidSdkDir.absolutePath}\" --licenses"
            )
                .directory(homeDir)
                .redirectErrorStream(true)
                .apply { environment().putAll(env) }
                .start()
            runCatching {
                licProc.outputStream.use { it.write(yesInput.toByteArray(Charsets.UTF_8)) }
            }
            licProc.inputStream.bufferedReader().use { it.readText() }
            licProc.waitFor(120, TimeUnit.SECONDS)

            onProgress("Installing platforms;android-35 & build-tools;35.0.0…", 0.82f)
            val sdkProc = ProcessBuilder(
                "/system/bin/sh",
                "-c",
                "\"${sdkManagerBin.absolutePath}\" --sdk_root=\"${androidSdkDir.absolutePath}\" \"platforms;android-35\" \"build-tools;35.0.0\""
            )
                .directory(homeDir)
                .redirectErrorStream(true)
                .apply { environment().putAll(env) }
                .start()
            runCatching { sdkProc.outputStream.close() }
            val sdkOut = sdkProc.inputStream.bufferedReader().use { it.readText() }
            val sdkOk = sdkProc.waitFor(600, TimeUnit.SECONDS)
            if (!sdkOk || sdkProc.exitValue() != 0) {
                error("sdkmanager failed (${sdkProc.exitValue()}): ${sdkOut.takeLast(400)}")
            }
            shimsInitialized = false
            ensureMobileEnvRules(cachedPrivilegeLevel)
            onProgress("Android Build SDK (JDK 17 + API 35 + aapt2) ready!", 1.0f)
        }
    }

    private fun installBridgeNodeModules() {
        val cmd = buildLinkerCommand(nodeBin, listOf(npmBin.absolutePath, "install", "--omit=dev", "--no-audit", "--no-fund"))
        val proc = ProcessBuilder(cmd)
            .directory(bridgeDir)
            .redirectErrorStream(true)
            .apply { environment().putAll(buildChildEnvironment()) }
            .start()
        val out = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        if (code != 0) {
            error("npm install failed ($code): ${out.takeLast(500)}")
        }
    }

    private fun downloadWithSha512(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit
    ): String {
        val req = Request.Builder().url(url).get().build()
        val digest = MessageDigest.getInstance("SHA-512")
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Download HTTP ${resp.code}")
            val body = resp.body ?: error("Empty response body")
            val total = body.contentLength()
            dest.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    var lastEmitMs = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        readTotal += n
                        if (total > 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastEmitMs >= 100L || readTotal >= total) {
                                lastEmitMs = now
                                onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadToFile(url: String, dest: File, onProgress: (Float) -> Unit) {
        val req = Request.Builder().url(url).header("User-Agent", "AgyMobile/1.3.0").get().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Download HTTP ${resp.code} for $url")
            val body = resp.body ?: error("Empty body for $url")
            val total = body.contentLength()
            dest.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    var lastEmitMs = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        if (total > 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastEmitMs >= 100L || readTotal >= total) {
                                lastEmitMs = now
                                onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractSingleFileFromTarGz(tarGzFile: File, targetEntryName: String, outFile: File) {
        outFile.parentFile?.mkdirs()
        GZIPInputStream(BufferedInputStream(FileInputStream(tarGzFile))).use { tarStream ->
            val header = ByteArray(512)
            val copyBuf = ByteArray(64 * 1024)
            while (true) {
                if (!readFully(tarStream, header)) break
                if (header.all { it == 0.toByte() }) break

                val rawName = header.copyOfRange(0, 100)
                val nameLen = rawName.indexOf(0.toByte()).let { if (it >= 0) it else rawName.size }
                val name = String(rawName, 0, nameLen, Charsets.UTF_8).trimStart('.', '/')
                val sizeStr = String(header, 124, 12, Charsets.US_ASCII).trim('\u0000', ' ')
                val size = sizeStr.toLongOrNull(8) ?: 0L

                if (name == targetEntryName) {
                    FileOutputStream(outFile).use { out ->
                        var rem = size
                        while (rem > 0) {
                            val chunk = tarStream.read(copyBuf, 0, minOf(copyBuf.size.toLong(), rem).toInt())
                            if (chunk <= 0) error("Unexpected EOF extracting $targetEntryName")
                            out.write(copyBuf, 0, chunk)
                            rem -= chunk
                        }
                    }
                    return
                } else {
                    val skipTotal = size + ((512 - (size % 512)) % 512)
                    skipFully(tarStream, skipTotal)
                }
            }
            error("Entry '$targetEntryName' not found in tar.gz")
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var rem = bytes
        val scratch = ByteArray(8192)
        while (rem > 0) {
            val n = input.read(scratch, 0, minOf(scratch.size.toLong(), rem).toInt())
            if (n <= 0) break
            rem -= n
        }
    }

    private fun unzipInto(zipFile: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalPath
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(targetDir, entry.name)
                val outCanonical = outFile.canonicalPath
                if (outCanonical != canonicalTarget && !outCanonical.startsWith(canonicalTarget + File.separator)) continue
                if (entry.isDirectory) {
                    val outPath = outFile.toPath()
                    if (Files.isSymbolicLink(outPath)) {
                        Files.deleteIfExists(outPath)
                    }
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    runCatching { Files.deleteIfExists(outFile.toPath()) }
                    FileOutputStream(outFile).use { out ->
                        while (true) {
                            val n = zis.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }

    private fun applySymlinksTxt(symlinksFile: File, baseDir: File) {
        if (!symlinksFile.exists()) return
        val canonicalBase = baseDir.canonicalPath
        symlinksFile.readLines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val idx = line.indexOf('←')
            if (idx <= 0 || idx >= line.lastIndex) return@forEach
            val target = line.substring(0, idx)
            val linkRel = line.substring(idx + 1)
            val linkFile = File(baseDir, linkRel)
            val parentPath = linkFile.canonicalFile.parentFile?.canonicalPath ?: return@forEach
            if (parentPath != canonicalBase && !parentPath.startsWith(canonicalBase + File.separator)) return@forEach
            linkFile.parentFile?.mkdirs()
            runCatching { Files.deleteIfExists(linkFile.toPath()) }
            runCatching { Os.symlink(target, linkFile.absolutePath) }
        }
    }

    private fun makeTreeExecutable(usr: File) {
        listOf(
            File(usr, "bin"),
            File(usr, "libexec"),
            File(usr, "lib/jvm")
        ).forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().forEach { f ->
                    if (f.isFile && !Files.isSymbolicLink(f.toPath())) {
                        if (dir.name != "jvm" || f.parentFile?.name == "bin" || f.name.contains(".so")) {
                            chmod755(f)
                        }
                    }
                }
            }
        }
        val libDir = File(usr, "lib")
        if (libDir.exists()) {
            libDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.contains(".so") && !Files.isSymbolicLink(f.toPath())) {
                    chmod755(f)
                }
            }
        }
    }

    private fun chmod755(file: File) {
        runCatching { Os.chmod(file.absolutePath, 493) } // 0755 octal
            .onFailure { file.setExecutable(true, false) }
    }

    private data class DebPkg(
        val name: String,
        val version: String,
        val filename: String,
        val depends: List<String>
    )

    private fun parseDebianPackagesStream(reader: BufferedReader): Map<String, DebPkg> {
        val map = HashMap<String, DebPkg>()
        var pkg = ""
        var ver = ""
        var filename = ""
        var depsRaw = ""

        fun flushBlock() {
            if (pkg.isNotBlank() && filename.isNotBlank()) {
                val deps = if (depsRaw.isBlank()) {
                    emptyList()
                } else {
                    depsRaw.split(",").mapNotNull { dep ->
                        dep.split("|").firstOrNull()
                            ?.substringBefore("(")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                    }
                }
                map[pkg] = DebPkg(pkg, ver, filename, deps)
            }
            pkg = ""
            ver = ""
            filename = ""
            depsRaw = ""
        }

        reader.useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) {
                    flushBlock()
                    continue
                }
                when {
                    line.startsWith("Package:") -> pkg = line.substringAfter(':').trim()
                    line.startsWith("Version:") -> ver = line.substringAfter(':').trim()
                    line.startsWith("Filename:") -> filename = line.substringAfter(':').trim()
                    line.startsWith("Depends:") -> depsRaw = line.substringAfter(':').trim()
                }
            }
        }
        flushBlock()
        return map
    }

    private fun parseInstalledDpkgPackages(statusFile: File): Set<String> {
        if (!statusFile.exists()) return emptySet()
        val installed = HashSet<String>()
        statusFile.forEachLine { line ->
            if (line.startsWith("Package:")) {
                installed.add(line.substringAfter(':').trim())
            }
        }
        return installed
    }

    private fun resolveDebDependencies(
        roots: List<String>,
        index: Map<String, DebPkg>,
        alreadyInstalled: Set<String>
    ): List<DebPkg> {
        val visited = HashSet<String>(alreadyInstalled)
        val result = ArrayList<DebPkg>()

        fun visit(pkgName: String) {
            if (!visited.add(pkgName)) return
            val pkg = index[pkgName] ?: return
            for (dep in pkg.depends) {
                visit(dep)
            }
            result.add(pkg)
        }

        for (root in roots) {
            visit(root)
        }
        return result
    }

    private fun extractDebIntoUsr(debFile: File, prebuiltEnv: Map<String, String>? = null) {
        val stageDir = File(tmpDir, "deb_stage").apply {
            deleteRecursively()
            mkdirs()
        }
        val dataTar = extractArDataTar(debFile, stageDir)
            ?: error("data.tar.* not found in ${debFile.name}")

        val env = prebuiltEnv ?: buildChildEnvironment()
        val decompBin = when {
            dataTar.name.endsWith(".xz") -> File(usrDir, "bin/xz")
            dataTar.name.endsWith(".gz") -> File(usrDir, "bin/gzip")
            dataTar.name.endsWith(".zst") -> File(usrDir, "bin/zstd")
            dataTar.name.endsWith(".bz2") -> File(usrDir, "bin/bzip2")
            else -> null
        }

        val tarBin = File(usrDir, "bin/tar")
        val extractStage = File(stageDir, "out").apply { mkdirs() }

        if (decompBin != null && decompBin.exists()) {
            val decompProc = ProcessBuilder(buildLinkerCommand(decompBin, listOf("-dc", dataTar.absolutePath)))
                .apply { environment().putAll(env) }
                .start()
            val tarProc = ProcessBuilder(buildLinkerCommand(tarBin, listOf("-xf", "-", "-C", extractStage.absolutePath)))
                .redirectErrorStream(true)
                .apply { environment().putAll(env) }
                .start()

            val pipeErr = ByteArrayOutputStream()
            val errThread = Thread {
                runCatching { decompProc.errorStream.use { it.copyTo(pipeErr) } }
            }.apply { isDaemon = true; start() }
            val copier = Thread {
                runCatching {
                    decompProc.inputStream.use { inp ->
                        tarProc.outputStream.use { out -> inp.copyTo(out) }
                    }
                }
            }.apply { isDaemon = true; start() }
            val tarOut = tarProc.inputStream.bufferedReader().readText()
            copier.join()
            errThread.join(1_000L)
            val dCode = decompProc.waitFor()
            val tCode = tarProc.waitFor()
            if (dCode != 0 || tCode != 0) {
                error("Failed extracting ${debFile.name} ($dCode/$tCode): $tarOut ${pipeErr.toString("UTF-8")}")
            }
        } else {
            val tarProc = ProcessBuilder(buildLinkerCommand(tarBin, listOf("-xf", dataTar.absolutePath, "-C", extractStage.absolutePath)))
                .redirectErrorStream(true)
                .apply { environment().putAll(env) }
                .start()
            val tarOut = tarProc.inputStream.bufferedReader().readText()
            val tCode = tarProc.waitFor()
            if (tCode != 0) error("tar failed for ${debFile.name}: $tarOut")
        }

        val srcUsr = File(extractStage, "data/data/com.termux/files/usr")
        if (srcUsr.exists()) {
            mergeTreePreservingSymlinks(srcUsr, usrDir)
        }
        stageDir.deleteRecursively()
    }

    private fun extractArDataTar(debFile: File, outDir: File): File? {
        BufferedInputStream(FileInputStream(debFile)).use { inp ->
            val magic = ByteArray(8)
            if (!readFully(inp, magic) || String(magic, Charsets.US_ASCII) != "!<arch>\n") {
                error("Invalid .deb ar header: ${debFile.name}")
            }
            val hdr = ByteArray(60)
            val buf = ByteArray(64 * 1024)
            while (readFully(inp, hdr)) {
                val rawName = String(hdr, 0, 16, Charsets.US_ASCII).trim().trimEnd('/')
                val size = String(hdr, 48, 10, Charsets.US_ASCII).trim().toLongOrNull() ?: 0L
                if (rawName.startsWith("data.tar")) {
                    val out = File(outDir, rawName)
                    FileOutputStream(out).use { fos ->
                        var rem = size
                        while (rem > 0) {
                            val n = inp.read(buf, 0, minOf(buf.size.toLong(), rem).toInt())
                            if (n <= 0) break
                            fos.write(buf, 0, n)
                            rem -= n
                        }
                    }
                    return out
                } else {
                    skipFully(inp, size)
                }
                if (size % 2L == 1L) {
                    inp.read()
                }
            }
        }
        return null
    }

    private fun mergeTreePreservingSymlinks(srcDir: File, dstDir: File) {
        val srcPath = srcDir.toPath()
        val dstPath = dstDir.toPath()
        Files.walk(srcPath).use { stream ->
            stream.forEach { current ->
                val rel = srcPath.relativize(current)
                if (rel.toString().isEmpty()) return@forEach
                val target = dstPath.resolve(rel)
                when {
                    Files.isSymbolicLink(current) -> {
                        target.parent?.let { Files.createDirectories(it) }
                        Files.deleteIfExists(target)
                        val linkTarget = Files.readSymbolicLink(current)
                        Files.createSymbolicLink(target, linkTarget)
                    }
                    Files.isDirectory(current) -> {
                        if (Files.isSymbolicLink(target) || (Files.exists(target) && !Files.isDirectory(target))) {
                            Files.deleteIfExists(target)
                        }
                        Files.createDirectories(target)
                    }
                    else -> {
                        target.parent?.let { Files.createDirectories(it) }
                        Files.deleteIfExists(target)
                        Files.move(current, target)
                    }
                }
            }
        }
    }
}
