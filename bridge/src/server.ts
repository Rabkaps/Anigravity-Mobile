#!/usr/bin/env node
import crypto from "node:crypto";
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import { WebSocketServer, type WebSocket } from "ws";
import { AcpManager, isTermuxEnv } from "./acp-manager.js";
import { PrivilegeBackend } from "./mcp/privilege-backend.js";
import type { HealthPayload, JsonRpcMessage } from "./types.js";

const BRIDGE_VERSION = "1.0.0";

function parseArgs(argv: string[]) {
  let host = process.env.AGY_BRIDGE_HOST || "127.0.0.1";
  let port = Number(process.env.AGY_BRIDGE_PORT || 4789);
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--host" && argv[i + 1]) {
      host = argv[++i];
    } else if (argv[i] === "--port" && argv[i + 1]) {
      port = Number(argv[++i]);
    }
  }
  return { host, port };
}

async function loadOrCreateToken(): Promise<string> {
  if (process.env.AGY_BRIDGE_TOKEN) {
    return process.env.AGY_BRIDGE_TOKEN;
  }
  const tokenPath = path.join(os.homedir(), ".config", "agy-bridge", "token");
  try {
    const existing = (await fs.readFile(tokenPath, "utf8")).trim();
    if (existing.length >= 32) return existing;
  } catch {
    // Generate fresh token below.
  }

  const generated = crypto.randomBytes(32).toString("hex");
  await fs.mkdir(path.dirname(tokenPath), { recursive: true });
  await fs.writeFile(tokenPath, generated + "\n", { encoding: "utf8", mode: 0o600 });
  return generated;
}

function isAuthorized(req: http.IncomingMessage, expectedToken: string): boolean {
  const authHeader = req.headers.authorization;
  if (authHeader?.startsWith("Bearer ")) {
    const provided = authHeader.slice("Bearer ".length).trim();
    if (safeEqual(provided, expectedToken)) return true;
  }

  try {
    const url = new URL(req.url || "/", "http://localhost");
    const queryToken = url.searchParams.get("token");
    if (queryToken && safeEqual(queryToken, expectedToken)) return true;
  } catch {
    return false;
  }
  return false;
}

function safeEqual(a: string, b: string): boolean {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

async function readJsonBody(req: http.IncomingMessage): Promise<Record<string, unknown>> {
  const MAX_BYTES = 1024 * 1024;
  let totalBytes = 0;
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    const buf = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    totalBytes += buf.length;
    if (totalBytes > MAX_BYTES) {
      throw Object.assign(new Error("Payload too large"), { statusCode: 413 });
    }
    chunks.push(buf);
  }
  const raw = Buffer.concat(chunks).toString("utf8").trim();
  if (!raw) return {};
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw Object.assign(new Error("Invalid JSON body"), { statusCode: 400 });
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw Object.assign(new Error("JSON body must be an object"), { statusCode: 400 });
  }
  return parsed as Record<string, unknown>;
}

function sendJson(res: http.ServerResponse, status: number, body: unknown) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(body));
}

async function main() {
  const { host, port } = parseArgs(process.argv.slice(2));
  const token = await loadOrCreateToken();
  const acpManager = new AcpManager();
  const privilegeBackend = new PrivilegeBackend();

  const acpClients = new Set<WebSocket>();

  acpManager.on("message", (msg: JsonRpcMessage) => {
    const encoded = JSON.stringify(msg);
    for (const ws of acpClients) {
      if (ws.readyState === ws.OPEN) {
        ws.send(encoded);
      }
    }
  });

  acpManager.on("stderr", (text: string) => {
    process.stderr.write(`[agy acp] ${text}`);
  });

  acpManager.on("exit", ({ code, signal }: { code: number | null; signal: string | null }) => {
    const notice = JSON.stringify({
      jsonrpc: "2.0",
      method: "bridge/agy_exited",
      params: { code, signal },
    });
    for (const ws of acpClients) {
      if (ws.readyState === ws.OPEN) ws.send(notice);
    }
  });

  acpManager.on("error", (err: Error & { code?: string }) => {
    const message =
      err.code === "ENOENT"
        ? "agy CLI not found in PATH. Open the Terminal drawer and install agy (or set AGY_BIN)."
        : `Failed to start agy acp: ${err.message}`;
    process.stderr.write(`[agy-bridge] ${message}\n`);
    const notice = JSON.stringify({
      jsonrpc: "2.0",
      method: "bridge/error",
      params: { message },
    });
    for (const ws of acpClients) {
      if (ws.readyState === ws.OPEN) ws.send(notice);
    }
  });

  const server = http.createServer(async (req, res) => {
    if (!isAuthorized(req, token)) {
      sendJson(res, 401, { error: "Unauthorized: invalid or missing Bearer token" });
      return;
    }

    let url: URL;
    try {
      url = new URL(req.url || "/", "http://localhost");
    } catch {
      sendJson(res, 400, { error: "Invalid request URL" });
      return;
    }

    try {
      if (req.method === "GET" && url.pathname === "/health") {
        const [agyVersion, privilegeLevel] = await Promise.all([
          acpManager.getAgyVersion(),
          privilegeBackend.detect(true),
        ]);
        const payload: HealthPayload = {
          ok: true,
          bridgeVersion: BRIDGE_VERSION,
          agyVersion,
          platform: process.platform,
          arch: process.arch,
          isTermux: isTermuxEnv(),
          privilegeLevel,
          defaultWorkspace: acpManager.cwd,
        };
        sendJson(res, 200, payload);
        return;
      }

      if (req.method === "GET" && url.pathname === "/api/sessions") {
        const sessions = await acpManager.listSessions();
        sendJson(res, 200, { sessions });
        return;
      }

      if (req.method === "GET" && url.pathname === "/api/diff") {
        const cwdParam = url.searchParams.get("cwd") || undefined;
        const diff = await acpManager.getGitDiff(cwdParam);
        sendJson(res, 200, diff);
        return;
      }

      if (req.method === "GET" && url.pathname === "/api/quota") {
        const quota = await acpManager.getQuotaSummary();
        sendJson(res, 200, quota);
        return;
      }

      if (req.method === "GET" && url.pathname === "/api/config") {
        const settings = await acpManager.readSettings();
        sendJson(res, 200, { settings });
        return;
      }

      if (req.method === "PATCH" && url.pathname === "/api/config") {
        const patch = await readJsonBody(req);
        const settings = await acpManager.patchSettings(patch);
        sendJson(res, 200, { settings });
        return;
      }

      if (req.method === "POST" && url.pathname === "/api/restart") {
        acpManager.restart();
        sendJson(res, 200, { restarted: true });
        return;
      }

      if (req.method === "POST" && url.pathname === "/api/env") {
        const body = await readJsonBody(req);
        const envPatch: Record<string, string> = {};
        for (const [k, v] of Object.entries(body)) {
          if (typeof v === "string") envPatch[k] = v;
        }
        acpManager.setEnvOverrides(envPatch);
        acpManager.restart();
        sendJson(res, 200, { updated: true });
        return;
      }

      sendJson(res, 404, { error: "Not found" });
    } catch (err: unknown) {
      const typed = err as { statusCode?: number; message?: string };
      const status = typeof typed?.statusCode === "number" ? typed.statusCode : 500;
      const message = err instanceof Error ? err.message : "Internal server error";
      sendJson(res, status, { error: message });
    }
  });

  const acpWss = new WebSocketServer({ noServer: true, maxPayload: 16 * 1024 * 1024 });
  const ptyWss = new WebSocketServer({ noServer: true, maxPayload: 1024 * 1024 });

  acpWss.on("connection", (ws) => {
    acpClients.add(ws);
    ws.on("error", () => {
      acpClients.delete(ws);
    });
    try {
      acpManager.ensureStarted();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to start agy acp";
      ws.send(
        JSON.stringify({
          jsonrpc: "2.0",
          method: "bridge/error",
          params: { message },
        })
      );
    }

    ws.on("message", (data) => {
      try {
        const msg = JSON.parse(data.toString("utf8")) as JsonRpcMessage;
        acpManager.sendToAgy(msg);
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : "Invalid JSON-RPC payload";
        ws.send(
          JSON.stringify({
            jsonrpc: "2.0",
            method: "bridge/error",
            params: { message },
          })
        );
      }
    });

    ws.on("close", () => {
      acpClients.delete(ws);
    });
  });

  ptyWss.on("connection", (ws) => {
    const shellBin = process.env.SHELL || (process.platform === "win32" ? "powershell.exe" : "bash");
    const shellArgs = process.platform === "win32" ? ["-NoLogo"] : ["-i"];

    const shellProc = spawn(shellBin, shellArgs, {
      cwd: acpManager.cwd,
      env: { ...process.env, TERM: "xterm-256color" },
      stdio: ["pipe", "pipe", "pipe"],
    });

    ws.on("error", () => {
      // Handled on close
    });
    shellProc.stdin.on("error", () => {
      // Ignore EPIPE on shell exit
    });
    shellProc.on("error", (err) => {
      if (ws.readyState === ws.OPEN) {
        ws.send(`\r\n[shell error: ${err.message}]\r\n`);
        ws.close();
      }
    });

    const forwardOut = (chunk: Buffer) => {
      if (ws.readyState === ws.OPEN) {
        ws.send(chunk.toString("utf8"));
      }
    };

    shellProc.stdout.on("data", forwardOut);
    shellProc.stderr.on("data", forwardOut);
    shellProc.on("exit", (code) => {
      if (ws.readyState === ws.OPEN) {
        ws.send(`\r\n[shell exited with code ${code ?? 0}]\r\n`);
        ws.close();
      }
    });

    ws.on("message", (data) => {
      const text = data.toString("utf8");
      if (shellProc.stdin.writable) {
        shellProc.stdin.write(text);
      }
    });

    ws.on("close", () => {
      runCleanupShell(shellProc);
    });
  });

  function runCleanupShell(shellProc: ReturnType<typeof spawn>) {
    try {
      shellProc.stdin?.end();
    } catch {
      // ignore
    }
    try {
      shellProc.kill("SIGHUP");
    } catch {
      // ignore
    }
    const timer = setTimeout(() => {
      try {
        if (!shellProc.killed) shellProc.kill("SIGKILL");
      } catch {
        // ignore
      }
    }, 1000);
    timer.unref();
  }

  server.on("upgrade", (req, socket, head) => {
    if (!isAuthorized(req, token)) {
      socket.write("HTTP/1.1 401 Unauthorized\r\n\r\n");
      socket.destroy();
      return;
    }

    let url: URL;
    try {
      url = new URL(req.url || "/", "http://localhost");
    } catch {
      socket.write("HTTP/1.1 400 Bad Request\r\n\r\n");
      socket.destroy();
      return;
    }
    if (url.pathname === "/acp") {
      acpWss.handleUpgrade(req, socket, head, (ws) => {
        acpWss.emit("connection", ws, req);
      });
    } else if (url.pathname === "/pty") {
      ptyWss.handleUpgrade(req, socket, head, (ws) => {
        ptyWss.emit("connection", ws, req);
      });
    } else {
      socket.write("HTTP/1.1 404 Not Found\r\n\r\n");
      socket.destroy();
    }
  });

  server.listen(port, host, async () => {
    const detected = await privilegeBackend.detect();
    console.log("========================================");
    console.log(` agy-bridge v${BRIDGE_VERSION} listening`);
    console.log(` HTTP/WS : http://${host}:${port}`);
    console.log(` Backend : ${detected}`);
    console.log(` Token   : ${token}`);
    console.log("========================================");
  });
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
