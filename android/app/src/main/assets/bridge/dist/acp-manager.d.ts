import { EventEmitter } from "node:events";
import type { DiffFileEntry, JsonRpcMessage, QuotaSummaryResponse, SessionSummary } from "./types.js";
export declare function isTermuxEnv(): boolean;
export declare function getAgySettingsPath(): string;
export declare function getAgySessionsDir(): string;
export declare function getAgyOAuthTokenPath(): string;
export declare function stripModelEffortSuffix(modelId: string): string;
/**
 * Normalizes Google Cloud Code `/v1internal:fetchAvailableModels` and
 * `/v1internal:retrieveUserQuotaSummary` responses into a compact UI-friendly payload.
 */
export declare function parseQuotaPayloads(fetchModelsJson: Record<string, unknown> | null, quotaSummaryJson: Record<string, unknown> | null, meta?: {
    email?: string;
    authMethod?: string;
    error?: string;
}): QuotaSummaryResponse;
export declare function getAndroidControlMcpPath(): string;
/**
 * Manages the `agy acp` subprocess speaking line-delimited JSON-RPC 2.0 over stdio,
 * plus auxiliary inspection of Antigravity CLI settings, sessions, and workspace diffs.
 */
export declare class AcpManager extends EventEmitter {
    private readonly agyBin;
    private proc;
    private stdoutBuffer;
    private activeCwd;
    private envOverrides;
    constructor(agyBin?: string);
    get cwd(): string;
    setEnvOverrides(patch: Record<string, string>): void;
    isRunning(): boolean;
    ensureStarted(): void;
    sendToAgy(rawMsg: JsonRpcMessage): void;
    /**
     * Auto-registers `android-control-mcp` in session setup requests when running on Android/Termux
     * (or when `AGY_ENABLE_ANDROID_MCP=1` is explicitly set).
     */
    private maybeInjectAndroidMcp;
    restart(): void;
    getAgyVersion(): Promise<string | null>;
    readSettings(): Promise<Record<string, unknown>>;
    patchSettings(patch: Record<string, unknown>): Promise<Record<string, unknown>>;
    listSessions(): Promise<SessionSummary[]>;
    getGitDiff(cwdOverride?: string): Promise<{
        cwd: string;
        files: DiffFileEntry[];
    }>;
    getQuotaSummary(): Promise<QuotaSummaryResponse>;
}
