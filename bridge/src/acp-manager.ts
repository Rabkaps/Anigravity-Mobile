import { spawn, execFile, type ChildProcessWithoutNullStreams } from "node:child_process";
import { promisify } from "node:util";
import { EventEmitter } from "node:events";
import fsSync from "node:fs";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type {
  DiffFileEntry,
  JsonRpcMessage,
  ModelQuotaEntry,
  QuotaBucketEntry,
  QuotaSummaryResponse,
  SessionSummary,
} from "./types.js";

const execFileAsync = promisify(execFile);
const __dirname = path.dirname(fileURLToPath(import.meta.url));

export function isTermuxEnv(): boolean {
  return Boolean(process.env.TERMUX_VERSION) || (process.env.PREFIX ?? "").includes("com.termux");
}

export function getAgySettingsPath(): string {
  return path.join(os.homedir(), ".gemini", "antigravity-cli", "settings.json");
}

export function getAgySessionsDir(): string {
  return path.join(os.homedir(), ".gemini", "antigravity", "conversations");
}

export function getAgyOAuthTokenPath(): string {
  return path.join(os.homedir(), ".gemini", "antigravity-cli", "antigravity-oauth-token");
}

export function stripModelEffortSuffix(modelId: string): string {
  return modelId.replace(/-(minimal|low|medium|high|xhigh|none|tiered|sonar)$/i, "");
}

function parseOptionalNumber(val: unknown): number | undefined {
  if (typeof val === "number" && Number.isFinite(val)) return val;
  if (typeof val === "string") {
    const trimmed = val.trim();
    if (trimmed.length === 0) return undefined;
    const parsed = Number(trimmed);
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
}

function parseBucketObject(raw: unknown): QuotaBucketEntry | null {
  if (!raw || typeof raw !== "object") return null;
  const obj = raw as Record<string, unknown>;
  const bucketId = typeof obj.bucketId === "string" ? obj.bucketId : "";
  const displayName = typeof obj.displayName === "string" ? obj.displayName : bucketId;
  if (!bucketId && !displayName) return null;

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
export function parseQuotaPayloads(
  fetchModelsJson: Record<string, unknown> | null,
  quotaSummaryJson: Record<string, unknown> | null,
  meta?: { email?: string; authMethod?: string; error?: string }
): QuotaSummaryResponse {
  const bucketMap = new Map<string, QuotaBucketEntry>();
  const addBucket = (raw: unknown) => {
    const parsed = parseBucketObject(raw);
    if (parsed && !bucketMap.has(parsed.bucketId)) {
      bucketMap.set(parsed.bucketId, parsed);
    }
  };

  if (quotaSummaryJson) {
    if (Array.isArray(quotaSummaryJson.buckets)) {
      for (const b of quotaSummaryJson.buckets) addBucket(b);
    }
    if (Array.isArray(quotaSummaryJson.groups)) {
      for (const grp of quotaSummaryJson.groups) {
        if (grp && typeof grp === "object" && Array.isArray((grp as Record<string, unknown>).buckets)) {
          for (const b of (grp as Record<string, unknown>).buckets as unknown[]) {
            addBucket(b);
          }
        }
      }
    }
  }

  const models: Record<string, ModelQuotaEntry> = {};
  const rawModels =
    fetchModelsJson?.models && typeof fetchModelsJson.models === "object"
      ? (fetchModelsJson.models as Record<string, unknown>)
      : {};

  for (const [modelKey, rawDetails] of Object.entries(rawModels)) {
    if (!rawDetails || typeof rawDetails !== "object") continue;
    const details = rawDetails as Record<string, unknown>;
    const disabled = Boolean(details.disabled);
    const quotaInfo =
      details.quotaInfo && typeof details.quotaInfo === "object"
        ? (details.quotaInfo as Record<string, unknown>)
        : null;
    const beyondInfo =
      details.beyondModelInfo && typeof details.beyondModelInfo === "object"
        ? (details.beyondModelInfo as Record<string, unknown>)
        : null;

    const resetTime = typeof quotaInfo?.resetTime === "string" ? quotaInfo.resetTime : undefined;
    let remainingFraction = parseOptionalNumber(quotaInfo?.remainingFraction);
    if (remainingFraction === undefined && quotaInfo && resetTime && !disabled) {
      remainingFraction = 0;
    }

    const entry: ModelQuotaEntry = {
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
  const tiered =
    fetchModelsJson?.tieredModelIds && typeof fetchModelsJson.tieredModelIds === "object"
      ? (fetchModelsJson.tieredModelIds as Record<string, unknown>)
      : null;
  const preferredTierIds = [
    ...(Array.isArray(tiered?.pro) ? (tiered.pro as unknown[]) : []),
    ...(Array.isArray(tiered?.flash) ? (tiered.flash as unknown[]) : []),
    "gemini-3.8-flash-tiered",
    "gemini-4-hydrogen-sonar",
    "gemini-3-flash",
    "gemini-2.5-pro",
  ].filter((v): v is string => typeof v === "string");

  const geminiPoolEntry =
    preferredTierIds.map((id) => models[id]).find((m) => m && m.remainingFraction !== undefined) ??
    Object.values(models).find(
      (m) => m.modelId.toLowerCase().startsWith("gemini-") && m.remainingFraction !== undefined
    );

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

export function getAndroidControlMcpPath(): string {
  return path.resolve(__dirname, "mcp", "android-control-mcp.js");
}

/**
 * Manages the `agy acp` subprocess speaking line-delimited JSON-RPC 2.0 over stdio,
 * plus auxiliary inspection of Antigravity CLI settings, sessions, and workspace diffs.
 */
export class AcpManager extends EventEmitter {
  private proc: ChildProcessWithoutNullStreams | null = null;
  private stdoutBuffer = "";
  private activeCwd: string = process.env.AGY_DEFAULT_CWD || os.homedir();
  private envOverrides: Record<string, string> = {};

  constructor(private readonly agyBin: string = process.env.AGY_BIN || "agy") {
    super();
  }

  get cwd(): string {
    return this.activeCwd;
  }

  setEnvOverrides(patch: Record<string, string>): void {
    for (const [k, v] of Object.entries(patch)) {
      if (v) this.envOverrides[k] = v;
      else delete this.envOverrides[k];
    }
  }

  isRunning(): boolean {
    return this.proc !== null && !this.proc.killed;
  }

  ensureStarted(): void {
    if (this.isRunning()) return;

    const termuxExecLib = "/data/data/com.termux/files/usr/lib/libtermux-exec.so";
    const childEnv: NodeJS.ProcessEnv = { ...process.env };
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
    proc.stdout.on("data", (chunk: string) => {
      this.stdoutBuffer += chunk;
      let start = 0;
      let nlIndex: number;
      while ((nlIndex = this.stdoutBuffer.indexOf("\n", start)) !== -1) {
        const line = this.stdoutBuffer.slice(start, nlIndex).trim();
        start = nlIndex + 1;
        if (!line) continue;
        try {
          const parsed = JSON.parse(line) as JsonRpcMessage;
          this.emit("message", parsed);
        } catch {
          this.emit("stderr", `[agy-bridge] Non-JSON stdout from agy acp: ${line}\n`);
        }
      }
      if (start > 0) {
        this.stdoutBuffer = this.stdoutBuffer.slice(start);
      }
      if (this.stdoutBuffer.length > 32 * 1024 * 1024) {
        this.stdoutBuffer = "";
      }
    });

    proc.stdin.on("error", () => {
      // Ignore EPIPE when agy acp exits mid-write.
    });

    proc.stderr.setEncoding("utf8");
    proc.stderr.on("data", (chunk: string) => {
      this.emit("stderr", chunk);
    });

    proc.on("exit", (code, signal) => {
      if (this.proc === proc) {
        this.proc = null;
        this.emit("exit", { code, signal });
      }
    });

    proc.on("error", (err) => {
      if (this.proc === proc) {
        this.proc = null;
        this.emit("error", err);
      }
    });
  }

  sendToAgy(rawMsg: JsonRpcMessage): void {
    this.ensureStarted();

    const msg = this.maybeInjectAndroidMcp(rawMsg);
    if (
      "method" in msg &&
      (msg.method === "session/new" || msg.method === "session/load" || msg.method === "session/resume") &&
      msg.params &&
      typeof msg.params.cwd === "string" &&
      msg.params.cwd.trim().length > 0
    ) {
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
  private maybeInjectAndroidMcp(msg: JsonRpcMessage): JsonRpcMessage {
    const shouldInject = isTermuxEnv() || process.env.AGY_ENABLE_ANDROID_MCP === "1";
    const isSessionSetup =
      "method" in msg &&
      (msg.method === "session/new" || msg.method === "session/load" || msg.method === "session/resume");
    if (!shouldInject || !isSessionSetup) {
      return msg;
    }

    const params = { ...(msg.params ?? {}) };
    const existingServers = Array.isArray(params.mcpServers)
      ? [...(params.mcpServers as Array<Record<string, unknown>>)]
      : [];

    const alreadyRegistered = existingServers.some((s) => s?.name === "android-control");
    if (!alreadyRegistered) {
      const termuxPrefix = process.env.PREFIX || "/data/data/com.termux/files/usr";
      const nodeBin = process.execPath.includes("linker64")
        ? (process.env.TERMUX_EXEC__PROC_SELF_EXE || path.join(termuxPrefix, "bin", "node"))
        : process.execPath;
      const useLinker = isTermuxEnv() && fsSync.existsSync("/system/bin/linker64");
      const envEntries: Array<{ name: string; value: string }> = [];
      const termuxExecLib = path.join(termuxPrefix, "lib", "libtermux-exec.so");
      const preload = process.env.LD_PRELOAD || (fsSync.existsSync(termuxExecLib) ? termuxExecLib : "");
      if (preload) {
        envEntries.push({ name: "LD_PRELOAD", value: preload });
      }
      if (process.env.PATH) {
        envEntries.push({ name: "PATH", value: process.env.PATH });
      }

      existingServers.push(
        useLinker
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
            }
      );
    }

    return {
      ...msg,
      params: {
        ...params,
        mcpServers: existingServers,
      },
    };
  }

  restart(): void {
    if (this.proc) {
      this.proc.kill("SIGTERM");
      this.proc = null;
    }
    this.ensureStarted();
  }

  async getAgyVersion(): Promise<string | null> {
    try {
      const { stdout } = await execFileAsync(this.agyBin, ["--version"], { timeout: 4000 });
      return stdout.trim() || null;
    } catch {
      return null;
    }
  }

  async readSettings(): Promise<Record<string, unknown>> {
    try {
      const raw = await fs.readFile(getAgySettingsPath(), "utf8");
      return JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return {};
    }
  }

  async patchSettings(patch: Record<string, unknown>): Promise<Record<string, unknown>> {
    const current = await this.readSettings();
    const updated = { ...current, ...patch };
    const settingsPath = getAgySettingsPath();
    await fs.mkdir(path.dirname(settingsPath), { recursive: true });
    await fs.writeFile(settingsPath, JSON.stringify(updated, null, 2) + "\n", "utf8");
    return updated;
  }

  async listSessions(): Promise<SessionSummary[]> {
    const dir = getAgySessionsDir();
    try {
      const entries = await fs.readdir(dir, { withFileTypes: true });
      const stats = await Promise.allSettled(
        entries.map(async (entry) => {
          const fullPath = path.join(dir, entry.name);
          const stat = await fs.stat(fullPath);
          return {
            id: entry.name.replace(/\.(json|db)$/, ""),
            title: entry.name.replace(/\.(json|db)$/, ""),
            cwd: this.activeCwd,
            updatedAt: stat.mtime.toISOString(),
          };
        })
      );
      const summaries = stats
        .filter((r): r is PromiseFulfilledResult<SessionSummary> => r.status === "fulfilled")
        .map((r) => r.value);
      return summaries.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 50);
    } catch {
      return [];
    }
  }

  async getGitDiff(cwdOverride?: string): Promise<{ cwd: string; files: DiffFileEntry[] }> {
    const targetCwd = cwdOverride || this.activeCwd;
    try {
      const { stdout: statusOut } = await execFileAsync("git", ["status", "--porcelain", "-z"], {
        cwd: targetCwd,
        timeout: 6000,
      });
      const tokens = statusOut.split("\u0000").filter(Boolean);
      const files: DiffFileEntry[] = [];
      let i = 0;
      while (i < tokens.length && files.length < 40) {
        const entry = tokens[i++];
        const code = entry.slice(0, 2).trim();
        const filePath = entry.slice(3);
        if (code.includes("R") || code.includes("C")) {
          // Skip original path token for renames/copies in -z format
          i++;
        }
        let diffText = "";
        try {
          const args =
            code === "??"
              ? ["diff", "--no-index", "/dev/null", "--", filePath]
              : ["diff", "HEAD", "--", filePath];
          const { stdout } = await execFileAsync("git", args, {
            cwd: targetCwd,
            timeout: 5000,
            maxBuffer: 2 * 1024 * 1024,
          });
          diffText = stdout;
        } catch (err: unknown) {
          // git diff --no-index exits 1 when differences exist, placing diff in stdout
          const execErr = err as { stdout?: string };
          diffText = typeof execErr?.stdout === "string" ? execErr.stdout : "";
        }

        const status: DiffFileEntry["status"] =
          code === "??"
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
    } catch {
      return { cwd: targetCwd, files: [] };
    }
  }

  async getQuotaSummary(): Promise<QuotaSummaryResponse> {
    try {
      const rawFile = await fs.readFile(getAgyOAuthTokenPath(), "utf8");
      const outer = JSON.parse(rawFile) as Record<string, unknown>;
      const tokObj =
        typeof outer.token === "string"
          ? (JSON.parse(outer.token) as Record<string, unknown>)
          : (outer.token as Record<string, unknown> | undefined);

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

      const modelsJson = modelsRes.ok ? ((await modelsRes.json()) as Record<string, unknown>) : null;
      const summaryJson = summaryRes.ok ? ((await summaryRes.json()) as Record<string, unknown>) : null;
      const errorMsg =
        !modelsRes.ok && !summaryRes.ok
          ? `Quota HTTP ${modelsRes.status}/${summaryRes.status}`
          : undefined;

      return parseQuotaPayloads(modelsJson, summaryJson, {
        email,
        authMethod,
        error: errorMsg,
      });
    } catch (err: unknown) {
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
