export type PrivilegeLevel = "root" | "shizuku" | "termux-api" | "none";
export type PrivilegeModeOverride = "auto" | "su" | "shizuku" | "termux-api" | "none";
export type JsonRpcId = string | number | null;
export interface JsonRpcRequest {
    jsonrpc: "2.0";
    id?: JsonRpcId;
    method: string;
    params?: Record<string, unknown>;
}
export interface JsonRpcResponse {
    jsonrpc: "2.0";
    id: JsonRpcId;
    result?: unknown;
    error?: {
        code: number;
        message: string;
        data?: unknown;
    };
}
export interface JsonRpcNotification {
    jsonrpc: "2.0";
    method: string;
    params?: Record<string, unknown>;
}
export type JsonRpcMessage = JsonRpcRequest | JsonRpcResponse | JsonRpcNotification;
export interface HealthPayload {
    ok: boolean;
    bridgeVersion: string;
    agyVersion: string | null;
    platform: string;
    arch: string;
    isTermux: boolean;
    privilegeLevel: PrivilegeLevel;
    defaultWorkspace: string;
}
export interface SessionSummary {
    id: string;
    title: string;
    cwd: string;
    updatedAt: string;
}
export interface DiffFileEntry {
    path: string;
    status: "modified" | "added" | "deleted" | "renamed" | "untracked";
    diff: string;
}
export interface ModelQuotaEntry {
    modelId: string;
    displayName?: string;
    remainingFraction?: number;
    resetTime?: string;
    quotaBucketName?: string;
    disabled?: boolean;
}
export interface QuotaBucketEntry {
    bucketId: string;
    displayName: string;
    description?: string;
    window?: string;
    remainingFraction?: number;
    remainingAmount?: number;
    resetTime?: string;
    disabled?: boolean;
}
export interface QuotaSummaryResponse {
    ok: boolean;
    email?: string;
    authMethod?: string;
    buckets: QuotaBucketEntry[];
    models: Record<string, ModelQuotaEntry>;
    error?: string;
}
