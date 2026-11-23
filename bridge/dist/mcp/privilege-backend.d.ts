import type { PrivilegeLevel, PrivilegeModeOverride } from "../types.js";
export interface CommandResult {
    stdout: string;
    stderr: string;
    exitCode: number;
}
export interface CommandRunner {
    run(file: string, args: string[], options?: {
        timeoutMs?: number;
        maxBuffer?: number;
    }): Promise<CommandResult>;
}
export declare const defaultCommandRunner: CommandRunner;
export interface BinaryResult {
    stdout: Buffer;
    stderr: string;
    exitCode: number;
}
export interface BinaryRunner {
    runBinary(file: string, args: string[], options?: {
        timeoutMs?: number;
        maxBuffer?: number;
    }): Promise<BinaryResult>;
}
export declare const defaultBinaryRunner: BinaryRunner;
/**
 * Abstracts privilege escalation across rooted (`su`), ADB-elevated (`rish` / Shizuku),
 * `Termux:API`, and plain Termux userspace environments.
 */
export declare class PrivilegeBackend {
    private readonly runner;
    private readonly binaryRunner;
    private readonly modeOverride;
    private cachedLevel;
    constructor(runner?: CommandRunner, binaryRunner?: BinaryRunner, modeOverride?: PrivilegeModeOverride);
    detect(forceRefresh?: boolean): Promise<PrivilegeLevel>;
    /**
     * Executes a shell command string using the highest available privilege backend.
     */
    execPrivileged(command: string, timeoutMs?: number): Promise<CommandResult>;
    /**
     * Executes a privileged command whose stdout is binary data (e.g. `screencap -p`).
     */
    execPrivilegedBinary(command: string, timeoutMs?: number): Promise<BinaryResult>;
}
