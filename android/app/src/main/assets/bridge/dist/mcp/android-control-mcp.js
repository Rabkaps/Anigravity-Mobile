#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { PrivilegeBackend } from "./privilege-backend.js";
import { parseUiTreeXml } from "./ui-tree-parser.js";
const backend = new PrivilegeBackend();
function shellQuote(arg) {
    return "'" + arg.replaceAll("'", "'\\''") + "'";
}
function formatExecOutput(stdout, stderr, exitCode) {
    return [
        `exitCode: ${exitCode}`,
        stdout.trim() ? `stdout:\n${stdout.trim()}` : "",
        stderr.trim() ? `stderr:\n${stderr.trim()}` : "",
    ]
        .filter(Boolean)
        .join("\n\n");
}
async function requireElevatedOrExplain(actionName) {
    const level = await backend.detect();
    if (level === "root" || level === "shizuku") {
        return null;
    }
    return (`Action "${actionName}" requires root (su) or Shizuku (rish), ` +
        `but detected privilege level is "${level}". ` +
        `Grant su or start Shizuku and link rish in Termux.`);
}
const server = new McpServer({
    name: "android-control-mcp",
    version: "1.0.0",
});
server.tool("android_status", "Get active Android privilege backend (root/shizuku/termux-api/none), foreground app, and battery state.", {}, async () => {
    const privilegeLevel = await backend.detect(true);
    if (privilegeLevel === "root" || privilegeLevel === "shizuku") {
        const [focus, battery, props] = await Promise.all([
            backend.execPrivileged("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -n 4"),
            backend.execPrivileged("dumpsys battery | head -n 15"),
            backend.execPrivileged("getprop ro.product.model; getprop ro.build.version.release"),
        ]);
        return {
            content: [
                {
                    type: "text",
                    text: JSON.stringify({
                        privilegeLevel,
                        device: props.stdout.trim().split("\n"),
                        focus: focus.stdout.trim(),
                        battery: battery.stdout.trim(),
                    }, null, 2),
                },
            ],
        };
    }
    const batteryFallback = privilegeLevel === "termux-api"
        ? await backend.execPrivileged("termux-battery-status")
        : null;
    return {
        content: [
            {
                type: "text",
                text: JSON.stringify({
                    privilegeLevel,
                    battery: batteryFallback?.stdout.trim() || "unavailable without su/shizuku/termux-api",
                }, null, 2),
            },
        ],
    };
});
server.tool("android_screenshot", "Capture the current Android screen as a PNG image (requires su or Shizuku).", {}, async () => {
    const blocked = await requireElevatedOrExplain("android_screenshot");
    if (blocked) {
        return { content: [{ type: "text", text: blocked }], isError: true };
    }
    const res = await backend.execPrivilegedBinary("screencap -p");
    if (res.exitCode !== 0 || res.stdout.length === 0) {
        return {
            content: [
                {
                    type: "text",
                    text: `screencap failed (exit ${res.exitCode}): ${res.stderr}`,
                },
            ],
            isError: true,
        };
    }
    return {
        content: [
            {
                type: "image",
                data: res.stdout.toString("base64"),
                mimeType: "image/png",
            },
        ],
    };
});
server.tool("android_ui_tree", "Dump and parse the current Android UI hierarchy into compact clickable/text nodes with center coordinates.", {}, async () => {
    const blocked = await requireElevatedOrExplain("android_ui_tree");
    if (blocked) {
        return { content: [{ type: "text", text: blocked }], isError: true };
    }
    const dumpPath = `/data/local/tmp/agy_ui_dump_${process.pid}_${Date.now()}.xml`;
    const res = await backend.execPrivileged(`uiautomator dump ${dumpPath} >/dev/null 2>&1 && cat ${dumpPath}; rm -f ${dumpPath}`);
    if (res.exitCode !== 0 || !res.stdout.includes("<hierarchy")) {
        return {
            content: [
                {
                    type: "text",
                    text: `uiautomator dump failed: ${formatExecOutput(res.stdout, res.stderr, res.exitCode)}`,
                },
            ],
            isError: true,
        };
    }
    const nodes = parseUiTreeXml(res.stdout);
    return {
        content: [
            {
                type: "text",
                text: JSON.stringify({ nodeCount: nodes.length, nodes }, null, 2),
            },
        ],
    };
});
server.tool("android_tap", "Tap screen coordinates (x, y) on Android.", {
    x: z.number().int().nonnegative(),
    y: z.number().int().nonnegative(),
}, async ({ x, y }) => {
    const blocked = await requireElevatedOrExplain("android_tap");
    if (blocked)
        return { content: [{ type: "text", text: blocked }], isError: true };
    const res = await backend.execPrivileged(`input tap ${x} ${y}`);
    return {
        content: [{ type: "text", text: formatExecOutput(res.stdout, res.stderr, res.exitCode) }],
        isError: res.exitCode !== 0,
    };
});
server.tool("android_swipe", "Swipe from (x1, y1) to (x2, y2) over durationMs milliseconds.", {
    x1: z.number().int().nonnegative(),
    y1: z.number().int().nonnegative(),
    x2: z.number().int().nonnegative(),
    y2: z.number().int().nonnegative(),
    durationMs: z.number().int().min(50).max(5000).default(300),
}, async ({ x1, y1, x2, y2, durationMs }) => {
    const blocked = await requireElevatedOrExplain("android_swipe");
    if (blocked)
        return { content: [{ type: "text", text: blocked }], isError: true };
    const res = await backend.execPrivileged(`input swipe ${x1} ${y1} ${x2} ${y2} ${durationMs}`);
    return {
        content: [{ type: "text", text: formatExecOutput(res.stdout, res.stderr, res.exitCode) }],
        isError: res.exitCode !== 0,
    };
});
server.tool("android_type", "Type text into the currently focused Android input field.", {
    text: z.string().min(1),
}, async ({ text }) => {
    const blocked = await requireElevatedOrExplain("android_type");
    if (blocked)
        return { content: [{ type: "text", text: blocked }], isError: true };
    // Android `input text` treats `%s` as space and requires careful shell escaping.
    const formatted = text.replaceAll(" ", "%s");
    const res = await backend.execPrivileged(`input text ${shellQuote(formatted)}`);
    return {
        content: [{ type: "text", text: formatExecOutput(res.stdout, res.stderr, res.exitCode) }],
        isError: res.exitCode !== 0,
    };
});
server.tool("android_keyevent", "Send an Android keyevent (e.g. KEYCODE_HOME, KEYCODE_BACK, KEYCODE_ENTER, KEYCODE_APP_SWITCH).", {
    keycode: z.string().regex(/^[A-Z0-9_]+$/),
}, async ({ keycode }) => {
    const blocked = await requireElevatedOrExplain("android_keyevent");
    if (blocked)
        return { content: [{ type: "text", text: blocked }], isError: true };
    const res = await backend.execPrivileged(`input keyevent ${keycode}`);
    return {
        content: [{ type: "text", text: formatExecOutput(res.stdout, res.stderr, res.exitCode) }],
        isError: res.exitCode !== 0,
    };
});
server.tool("android_launch_app", "Launch an Android application by package name (e.g. com.android.settings).", {
    packageName: z.string().regex(/^[a-zA-Z0-9_.]+$/),
    activity: z.string().regex(/^[a-zA-Z0-9_.$]+$/).optional(),
}, async ({ packageName, activity }) => {
    const blocked = await requireElevatedOrExplain("android_launch_app");
    if (blocked)
        return { content: [{ type: "text", text: blocked }], isError: true };
    const cmd = activity
        ? `am start -n ${shellQuote(`${packageName}/${activity}`)}`
        : `monkey -p ${shellQuote(packageName)} -c android.intent.category.LAUNCHER 1`;
    const res = await backend.execPrivileged(cmd);
    return {
        content: [{ type: "text", text: formatExecOutput(res.stdout, res.stderr, res.exitCode) }],
        isError: res.exitCode !== 0,
    };
});
server.tool("android_list_apps", "List installed Android packages (third-party by default, or all packages).", {
    includeSystem: z.boolean().default(false),
}, async ({ includeSystem }) => {
    const blocked = await requireElevatedOrExplain("android_list_apps");
    if (blocked)
        return { content: [{ type: "text", text: blocked }], isError: true };
    const flag = includeSystem ? "" : "-3";
    const res = await backend.execPrivileged(`pm list packages ${flag} | sed 's/^package://' | sort`);
    return {
        content: [{ type: "text", text: formatExecOutput(res.stdout, res.stderr, res.exitCode) }],
        isError: res.exitCode !== 0,
    };
});
server.tool("android_shell", "Execute an Android shell command using the highest available privilege backend (su -> Shizuku rish -> sh).", {
    command: z.string().min(1),
    timeoutMs: z.number().int().min(1000).max(60_000).default(15_000),
}, async ({ command, timeoutMs }) => {
    const level = await backend.detect();
    const res = await backend.execPrivileged(command, timeoutMs);
    return {
        content: [
            {
                type: "text",
                text: `[backend=${level}]\n${formatExecOutput(res.stdout, res.stderr, res.exitCode)}`,
            },
        ],
        isError: res.exitCode !== 0,
    };
});
server.tool("android_notify", "Post an Android notification (via Termux:API or `cmd notification` on root/Shizuku).", {
    title: z.string().min(1),
    content: z.string().min(1),
}, async ({ title, content }) => {
    const level = await backend.detect();
    if (level === "termux-api") {
        const res = await backend.execPrivileged(`termux-notification --title ${shellQuote(title)} --content ${shellQuote(content)}`);
        return {
            content: [{ type: "text", text: formatExecOutput(res.stdout, res.stderr, res.exitCode) }],
            isError: res.exitCode !== 0,
        };
    }
    if (level === "root" || level === "shizuku") {
        const res = await backend.execPrivileged(`cmd notification post -S bigtext -t ${shellQuote(title)} agy_mobile ${shellQuote(content)}`);
        return {
            content: [{ type: "text", text: formatExecOutput(res.stdout, res.stderr, res.exitCode) }],
            isError: res.exitCode !== 0,
        };
    }
    return {
        content: [{ type: "text", text: "No notification backend (Termux:API, Shizuku, or root) available." }],
        isError: true,
    };
});
server.tool("android_clipboard", "Get or set the Android clipboard text via Termux:API.", {
    action: z.enum(["get", "set"]),
    text: z.string().optional(),
}, async ({ action, text }) => {
    if (action === "get") {
        const res = await backend.execPrivileged("termux-clipboard-get");
        return {
            content: [{ type: "text", text: formatExecOutput(res.stdout, res.stderr, res.exitCode) }],
            isError: res.exitCode !== 0,
        };
    }
    const res = await backend.execPrivileged(`termux-clipboard-set ${shellQuote(text ?? "")}`);
    return {
        content: [{ type: "text", text: formatExecOutput(res.stdout, res.stderr, res.exitCode) }],
        isError: res.exitCode !== 0,
    };
});
const transport = new StdioServerTransport();
await server.connect(transport);
