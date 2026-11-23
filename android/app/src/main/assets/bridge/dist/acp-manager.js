import { spawn, execFile } from "node:child_process";
import { promisify } from "node:util";
import { EventEmitter } from "node:events";
import fsSync from "node:fs";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
const execFileAsync = promisify(execFile);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
export function isTermuxEnv() {
    return Boolean(process.env.TERMUX_VERSION) || (process.env.PREFIX ?? "").includes("com.termux");
}
export function getAgySettingsPath() {
    return path.join(os.homedir(), ".gemini", "antigravity-cli", "settings.json");
}
export function getAgySessionsDir() {
    return path.join(os.homedir(), ".gemini", "antigravity", "conversations");
}
export function getAgyOAuthTokenPath() {
    return path.join(os.homedir(), ".gemini", "antigravity-cli", "antigravity-oauth-token");
}
export function stripModelEffortSuffix(modelId) {
    return modelId.replace(/-(minimal|low|medium|high|xhigh|none|tiered|sonar)$/i, "");
}
function parseOptionalNumber(val) {
    if (typeof val === "number" && Number.isFinite(val))
        return val;
    if (typeof val === "string") {
        const parsed = Number(val);
        if (Number.isFinite(parsed))
            return parsed;
    }
    return undefined;
}
function parseBucketObject(raw) {
    if (!raw || typeof raw !== "object")
        return null;
    const obj = raw;
    const bucketId = typeof obj.bucketId === "string" ? obj.bucketId : "";
    const displayName = typeof obj.displayName === "string" ? obj.displayName : bucketId;
    if (!bucketId && !displayName)
        return null;
    const disabled = Boolean(obj.disabled);
    const resetTime = typeof obj.resetTime === "string" ? obj.resetTime : undefined;
    let remainingFraction = parseOptionalNumber(obj.remainingFraction);
    // Proto3 JSON omits 0 floats on the wire; a bucket with resetTime and !disabled has 0 remaining.
    if (remainingFraction === undefined && resetTime && !disabled) {
        remainingFraction = 0;
    }
    return {
        bucketId: bucketId || displayName,
        displayName: displayName || bucketId,
        description: typeof obj.description === "string" ? obj.description : undefined,
        window: typeof obj.window === "string" ? obj.window : undefined,
        remainingFraction,
        remainingAmount: parseOptionalNumber(obj.remainingAmount),
        resetTime,
        disabled,
    };
}
/**
 * Normalizes Google Cloud Code `/v1internal:fetchAvailableModels` and
 * `/v1internal:retrieveUserQuotaSummary` responses into a compact UI-friendly payload.
 */
export function parseQuotaPayloads(fetchModelsJson, quotaSummaryJson, meta) {
    const bucketMap = new Map();
    const addBucket = (raw) => {
        const parsed = parseBucketObject(raw);
        if (parsed && !bucketMap.has(parsed.bucketId)) {
            bucketMap.set(parsed.bucketId, parsed);
        }
    };
    if (quotaSummaryJson) {
        if (Array.isArray(quotaSummaryJson.buckets)) {
            for (const b of quotaSummaryJson.buckets)
                addBucket(b);
        }
        if (Array.isArray(quotaSummaryJson.groups)) {
            for (const grp of quotaSummaryJson.groups) {
                if (grp && typeof grp === "object" && Array.isArray(grp.buckets)) {
                    for (const b of grp.buckets) {
                        addBucket(b);
                    }
                }
            }
        }
    }
    const models = {};
    const rawModels = fetchModelsJson?.models && typeof fetchModelsJson.models === "object"
        ? fetchModelsJson.models
        : {};
    for (const [modelKey, rawDetails] of Object.entries(rawModels)) {
        if (!rawDetails || typeof rawDetails !== "object")
            continue;
        const details = rawDetails;
        const disabled = Boolean(details.disabled);
        const quotaInfo = details.quotaInfo && typeof details.quotaInfo === "object"
            ? details.quotaInfo
            : null;
        const beyondInfo = details.beyondModelInfo && typeof details.beyondModelInfo === "object"
            ? details.beyondModelInfo
            : null;
        const resetTime = typeof quotaInfo?.resetTime === "string" ? quotaInfo.resetTime : undefined;
        let remainingFraction = parseOptionalNumber(quotaInfo?.remainingFraction);
        if (remainingFraction === undefined && quotaInfo && resetTime && !disabled) {
            remainingFraction = 0;
        }
        const entry = {
            modelId: modelKey,
            displayName: typeof details.displayName === "string" ? details.displayName : undefined,
            remainingFraction,
            resetTime,
            quotaBucketName: typeof beyondInfo?.quotaBucketName === "string" ? beyondInfo.quotaBucketName : undefined,
            disabled,
        };
        models[modelKey] = entry;
        const lowerKey = modelKey.toLowerCase();
        if (!(lowerKey in models)) {
            models[lowerKey] = entry;
        }
        const baseFamily = stripModelEffortSuffix(lowerKey);
        if (baseFamily && !(baseFamily in models)) {
            models[baseFamily] = entry;
        }
    }
    // Gemini models (gemini-4-neon, gemini-4-argon, gemini-4-hydrogen, orchestrator) share the 1P Gemini pool.
    const tiered = fetchModelsJson?.tieredModelIds && typeof fetchModelsJson.tieredModelIds === "object"
        ? fetchModelsJson.tieredModelIds
        : null;
    const preferredTierIds = [
        ...(Array.isArray(tiered?.pro) ? tiered.pro : []),
        ...(Array.isArray(tiered?.flash) ? tiered.flash : []),
        "gemini-3.8-flash-tiered",
        "gemini-4-hydrogen-sonar",
        "gemini-3-flash",
        "gemini-2.5-pro",
    ].filter((v) => typeof v === "string");
    const geminiPoolEntry = preferredTierIds.map((id) => models[id]).find((m) => m && m.remainingFraction !== undefined) ??
        Object.values(models).find((m) => m.modelId.toLowerCase().startsWith("gemini-") && m.remainingFraction !== undefined);
    if (geminiPoolEntry) {
        models["__gemini_pool__"] = geminiPoolEntry;
        if (!bucketMap.has("gemini-models")) {
            bucketMap.set("gemini-models", {
                bucketId: "gemini-models",
                displayName: "Gemini Models",
                window: "1P Pool",
                remainingFraction: geminiPoolEntry.remainingFraction,
                resetTime: geminiPoolEntry.resetTime,
                disabled: geminiPoolEntry.disabled,
            });
        }
    }
    const buckets = Array.from(bucketMap.values());
    return {
        ok: !meta?.error && (buckets.length > 0 || Object.keys(models).length > 0),
        email: meta?.email,
        authMethod: meta?.authMethod,
        buckets,
        models,
        error: meta?.error,
    };
}
export function getAndroidControlMcpPath() {
    return path.resolve(__dirname, "mcp", "android-control-mcp.js");
}
/**
 * Manages the `agy acp` subprocess speaking line-delimited JSON-RPC 2.0 over stdio,
 * plus auxiliary inspection of Antigravity CLI settings, sessions, and workspace diffs.
 */
export class AcpManager extends EventEmitter {
    agyBin;
    proc = null;
    stdoutBuffer = "";
    activeCwd = process.env.AGY_DEFAULT_CWD || os.homedir();
    envOverrides = {};
    constructor(agyBin = process.env.AGY_BIN || "agy") {
        super();
        this.agyBin = agyBin;
    }
    get cwd() {
        return this.activeCwd;
    }
    setEnvOverrides(patch) {
        for (const [k, v] of Object.entries(patch)) {
            if (v)
                this.envOverrides[k] = v;
            else
                delete this.envOverrides[k];
        }
    }
    isRunning() {
        return this.proc !== null && !this.proc.killed;
    }
    ensureStarted() {
        if (this.isRunning())
            return;
        const termuxExecLib = "/data/data/com.termux/files/usr/lib/libtermux-exec.so";
        const childEnv = { ...process.env };
        if (isTermuxEnv() && fsSync.existsSync("/system/bin/sh")) {
            // agy is a Go binary whose raw SYS_execve bypasses LD_PRELOAD;
            // pointing SHELL at /system/bin/sh lets Bionic libc honor LD_PRELOAD.
            childEnv.SHELL = "/system/bin/sh";
        }
        if (isTermuxEnv() && fsSync.existsSync(termuxExecLib)) {
            const existingPreload = childEnv.LD_PRELOAD || "";
            if (!existingPreload.includes("libtermux-exec.so")) {
                childEnv.LD_PRELOAD = existingPreload ? `${existingPreload}:${termuxExecLib}` : termuxExecLib;
            }
        }
        Object.assign(childEnv, this.envOverrides);
        const proc = spawn(this.agyBin, ["acp"], {
            cwd: this.activeCwd,
            env: childEnv,
            stdio: ["pipe", "pipe", "pipe"],
        });
        this.proc = proc;
        this.stdoutBuffer = "";
        proc.stdout.setEncoding("utf8");
        proc.stdout.on("data", (chunk) => {
            this.stdoutBuffer += chunk;
            let nlIndex;
            while ((nlIndex = this.stdoutBuffer.indexOf("\n")) !== -1) {
                const line = this.stdoutBuffer.slice(0, nlIndex).trim();
                this.stdoutBuffer = this.stdoutBuffer.slice(nlIndex + 1);
                if (!line)
                    continue;
                try {
                    const parsed = JSON.parse(line);
                    this.emit("message", parsed);
                }
                catch (err) {
                    this.emit("stderr", `[agy-bridge] Non-JSON stdout from agy acp: ${line}\n`);
                }
            }
        });
        proc.stderr.setEncoding("utf8");
        proc.stderr.on("data", (chunk) => {
            this.emit("stderr", chunk);
        });
        proc.on("exit", (code, signal) => {
            this.proc = null;
            this.emit("exit", { code, signal });
        });
        proc.on("error", (err) => {
            this.proc = null;
            this.emit("error", err);
        });
    }
    sendToAgy(rawMsg) {
        this.ensureStarted();
        const msg = this.maybeInjectAndroidMcp(rawMsg);
        if ("method" in msg &&
            (msg.method === "session/new" || msg.method === "session/load" || msg.method === "session/resume") &&
            msg.params &&
            typeof msg.params.cwd === "string" &&
            msg.params.cwd.trim().length > 0) {
            this.activeCwd = msg.params.cwd;
        }
        if (!this.proc?.stdin.writable) {
            throw new Error("agy acp stdin is not writable");
        }
        this.proc.stdin.write(JSON.stringify(msg) + "\n", "utf8");
    }
    /**
     * Auto-registers `android-control-mcp` in session setup requests when running on Android/Termux
     * (or when `AGY_ENABLE_ANDROID_MCP=1` is explicitly set).
     */
    maybeInjectAndroidMcp(msg) {
        const shouldInject = isTermuxEnv() || process.env.AGY_ENABLE_ANDROID_MCP === "1";
        const isSessionSetup = "method" in msg &&
            (msg.method === "session/new" || msg.method === "session/load" || msg.method === "session/resume");
        if (!shouldInject || !isSessionSetup) {
            return msg;
        }
        const params = { ...(msg.params ?? {}) };
        const existingServers = Array.isArray(params.mcpServers)
            ? [...params.mcpServers]
            : [];
        const alreadyRegistered = existingServers.some((s) => s?.name === "android-control");
        if (!alreadyRegistered) {
            const termuxPrefix = process.env.PREFIX || "/data/data/com.termux/files/usr";
            const nodeBin = process.execPath.includes("linker64")
                ? (process.env.TERMUX_EXEC__PROC_SELF_EXE || path.join(termuxPrefix, "bin", "node"))
                : process.execPath;
            const useLinker = isTermuxEnv() && fsSync.existsSync("/system/bin/linker64");
            const envEntries = [];
            const termuxExecLib = path.join(termuxPrefix, "lib", "libtermux-exec.so");
            const preload = process.env.LD_PRELOAD || (fsSync.existsSync(termuxExecLib) ? termuxExecLib : "");
            if (preload) {
                envEntries.push({ name: "LD_PRELOAD", value: preload });
            }
            if (process.env.PATH) {
                envEntries.push({ name: "PATH", value: process.env.PATH });
            }
            existingServers.push(useLinker
                ? {
                    name: "android-control",
                    command: "/system/bin/linker64",
                    args: [nodeBin, getAndroidControlMcpPath()],
                    env: envEntries,
                }
                : {
                    name: "android-control",
                    command: nodeBin,
                    args: [getAndroidControlMcpPath()],
                    env: envEntries,
                });
        }
        return {
            ...msg,
            params: {
                ...params,
                mcpServers: existingServers,
            },
        };
    }
    restart() {
        if (this.proc) {
            this.proc.kill("SIGTERM");
            this.proc = null;
        }
        this.ensureStarted();
    }
    async getAgyVersion() {
        try {
            const { stdout } = await execFileAsync(this.agyBin, ["--version"], { timeout: 4000 });
            return stdout.trim() || null;
        }
        catch {
            return null;
        }
    }
    async readSettings() {
        try {
            const raw = await fs.readFile(getAgySettingsPath(), "utf8");
            return JSON.parse(raw);
        }
        catch {
            return {};
        }
    }
    async patchSettings(patch) {
        const current = await this.readSettings();
        const updated = { ...current, ...patch };
        const settingsPath = getAgySettingsPath();
        await fs.mkdir(path.dirname(settingsPath), { recursive: true });
        await fs.writeFile(settingsPath, JSON.stringify(updated, null, 2) + "\n", "utf8");
        return updated;
    }
    async listSessions() {
        const dir = getAgySessionsDir();
        try {
            const entries = await fs.readdir(dir, { withFileTypes: true });
            const summaries = [];
            for (const entry of entries.slice(0, 50)) {
                const fullPath = path.join(dir, entry.name);
                const stat = await fs.stat(fullPath);
                summaries.push({
                    id: entry.name.replace(/\.json$/, ""),
                    title: entry.name.replace(/\.json$/, ""),
                    cwd: this.activeCwd,
                    updatedAt: stat.mtime.toISOString(),
                });
            }
            return summaries.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
        }
        catch {
            return [];
        }
    }
    async getGitDiff(cwdOverride) {
        const targetCwd = cwdOverride || this.activeCwd;
        try {
            const { stdout: statusOut } = await execFileAsync("git", ["status", "--porcelain"], {
                cwd: targetCwd,
                timeout: 6000,
            });
            const lines = statusOut
                .split(/\r?\n/)
                .map((l) => l.trimEnd())
                .filter(Boolean);
            const files = [];
            for (const line of lines.slice(0, 40)) {
                const code = line.slice(0, 2).trim();
                const filePath = line.slice(3).trim();
                let diffText = "";
                try {
                    const { stdout } = await execFileAsync("git", ["diff", "HEAD", "--", filePath], {
                        cwd: targetCwd,
                        timeout: 5000,
                        maxBuffer: 2 * 1024 * 1024,
                    });
                    diffText = stdout;
                }
                catch {
                    diffText = "";
                }
                const status = code === "??"
                    ? "untracked"
                    : code.includes("A")
                        ? "added"
                        : code.includes("D")
                            ? "deleted"
                            : code.includes("R")
                                ? "renamed"
                                : "modified";
                files.push({ path: filePath, status, diff: diffText });
            }
            return { cwd: targetCwd, files };
        }
        catch {
            return { cwd: targetCwd, files: [] };
        }
    }
    async getQuotaSummary() {
        try {
            const rawFile = await fs.readFile(getAgyOAuthTokenPath(), "utf8");
            const outer = JSON.parse(rawFile);
            const tokObj = typeof outer.token === "string"
                ? JSON.parse(outer.token)
                : outer.token;
            const accessToken = typeof tokObj?.access_token === "string" ? tokObj.access_token : "";
            const email = typeof outer.id_token_email === "string" ? outer.id_token_email : undefined;
            const authMethod = typeof outer.auth_method === "string" ? outer.auth_method : undefined;
            if (!accessToken) {
                return {
                    ok: false,
                    email,
                    authMethod,
                    buckets: [],
                    models: {},
                    error: "No OAuth access token found. Sign in to Antigravity first.",
                };
            }
            const headers = {
                Authorization: `Bearer ${accessToken}`,
                "Content-Type": "application/json",
                "User-Agent": "antigravity",
            };
            const [modelsRes, summaryRes] = await Promise.all([
                fetch("https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels", {
                    method: "POST",
                    headers,
                    body: "{}",
                    signal: AbortSignal.timeout(10_000),
                }),
                fetch("https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary", {
                    method: "POST",
                    headers,
                    body: "{}",
                    signal: AbortSignal.timeout(10_000),
                }),
            ]);
            if (modelsRes.status === 401 || summaryRes.status === 401) {
                return {
                    ok: false,
                    email,
                    authMethod,
                    buckets: [],
                    models: {},
                    error: "OAuth token expired (HTTP 401). Start/reconnect agy session to refresh.",
                };
            }
            if (modelsRes.status === 403 && summaryRes.status === 403) {
                return {
                    ok: false,
                    email,
                    authMethod,
                    buckets: [],
                    models: {},
                    error: "Quota endpoint returned HTTP 403 (check network/VPN access).",
                };
            }
            const modelsJson = modelsRes.ok ? (await modelsRes.json()) : null;
            const summaryJson = summaryRes.ok ? (await summaryRes.json()) : null;
            const errorMsg = !modelsRes.ok && !summaryRes.ok
                ? `Quota HTTP ${modelsRes.status}/${summaryRes.status}`
                : undefined;
            return parseQuotaPayloads(modelsJson, summaryJson, {
                email,
                authMethod,
                error: errorMsg,
            });
        }
        catch (err) {
            const msg = err instanceof Error ? err.message : "Failed to load quota";
            return {
                ok: false,
                buckets: [],
                models: {},
                error: msg,
            };
        }
    }
}
