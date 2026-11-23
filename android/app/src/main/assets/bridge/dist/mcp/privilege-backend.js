import { execFile } from "node:child_process";
import { promisify } from "node:util";
const execFileAsync = promisify(execFile);
export const defaultCommandRunner = {
    async run(file, args, options = {}) {
        try {
            const { stdout, stderr } = await execFileAsync(file, args, {
                timeout: options.timeoutMs ?? 15_000,
                maxBuffer: options.maxBuffer ?? 16 * 1024 * 1024,
                encoding: "utf8",
            });
            return { stdout, stderr, exitCode: 0 };
        }
        catch (err) {
            const e = err;
            return {
                stdout: typeof e.stdout === "string" ? e.stdout : (e.stdout?.toString("utf8") ?? ""),
                stderr: typeof e.stderr === "string"
                    ? e.stderr
                    : (e.stderr?.toString("utf8") ?? e.message ?? "Command failed"),
                exitCode: typeof e.code === "number" ? e.code : 1,
            };
        }
    },
};
export const defaultBinaryRunner = {
    async runBinary(file, args, options = {}) {
        try {
            const { stdout, stderr } = await execFileAsync(file, args, {
                timeout: options.timeoutMs ?? 20_000,
                maxBuffer: options.maxBuffer ?? 24 * 1024 * 1024,
                encoding: "buffer",
            });
            return { stdout, stderr: stderr.toString("utf8"), exitCode: 0 };
        }
        catch (err) {
            const e = err;
            return {
                stdout: e.stdout ?? Buffer.alloc(0),
                stderr: e.stderr?.toString("utf8") ?? e.message ?? "Binary command failed",
                exitCode: typeof e.code === "number" ? e.code : 1,
            };
        }
    },
};
/**
 * Abstracts privilege escalation across rooted (`su`), ADB-elevated (`rish` / Shizuku),
 * `Termux:API`, and plain Termux userspace environments.
 */
export class PrivilegeBackend {
    runner;
    binaryRunner;
    modeOverride;
    cachedLevel = null;
    constructor(runner = defaultCommandRunner, binaryRunner = defaultBinaryRunner, modeOverride = process.env.AGY_PRIVILEGE_MODE || "auto") {
        this.runner = runner;
        this.binaryRunner = binaryRunner;
        this.modeOverride = modeOverride;
    }
    async detect(forceRefresh = false) {
        if (this.cachedLevel && !forceRefresh) {
            return this.cachedLevel;
        }
        if (this.modeOverride !== "auto") {
            this.cachedLevel = this.modeOverride === "su"
                ? "root"
                : this.modeOverride;
            return this.cachedLevel;
        }
        // 1. Check Magisk / KernelSU / APatch root (`su -c id` -> uid=0)
        const suCheck = await this.runner.run("su", ["-c", "id"], { timeoutMs: 4000 });
        if (suCheck.exitCode === 0 && suCheck.stdout.includes("uid=0")) {
            this.cachedLevel = "root";
            return this.cachedLevel;
        }
        // 2. Check Shizuku (`rish -c id` -> uid=2000 or uid=0)
        const rishCheck = await this.runner.run("rish", ["-c", "id"], { timeoutMs: 4000 });
        if (rishCheck.exitCode === 0 &&
            (rishCheck.stdout.includes("uid=2000") || rishCheck.stdout.includes("uid=0"))) {
            this.cachedLevel = "shizuku";
            return this.cachedLevel;
        }
        // 3. Check Termux:API availability
        const termuxApiCheck = await this.runner.run("which", ["termux-notification"], {
            timeoutMs: 3000,
        });
        if (termuxApiCheck.exitCode === 0 && termuxApiCheck.stdout.trim().length > 0) {
            this.cachedLevel = "termux-api";
            return this.cachedLevel;
        }
        this.cachedLevel = "none";
        return this.cachedLevel;
    }
    /**
     * Executes a shell command string using the highest available privilege backend.
     */
    async execPrivileged(command, timeoutMs = 15_000) {
        const level = await this.detect();
        switch (level) {
            case "root":
                return this.runner.run("su", ["-c", command], { timeoutMs });
            case "shizuku":
                return this.runner.run("rish", ["-c", command], { timeoutMs });
            case "termux-api":
            case "none":
                return this.runner.run("sh", ["-c", command], { timeoutMs });
        }
    }
    /**
     * Executes a privileged command whose stdout is binary data (e.g. `screencap -p`).
     */
    async execPrivilegedBinary(command, timeoutMs = 20_000) {
        const level = await this.detect();
        switch (level) {
            case "root":
                return this.binaryRunner.runBinary("su", ["-c", command], { timeoutMs });
            case "shizuku":
                return this.binaryRunner.runBinary("rish", ["-c", command], { timeoutMs });
            case "termux-api":
            case "none":
                return this.binaryRunner.runBinary("sh", ["-c", command], { timeoutMs });
        }
    }
}
