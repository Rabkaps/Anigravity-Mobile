import test from "node:test";
import assert from "node:assert/strict";
import { PrivilegeBackend } from "../dist/mcp/privilege-backend.js";
import { parseUiTreeXml } from "../dist/mcp/ui-tree-parser.js";
import { parseQuotaPayloads } from "../dist/acp-manager.js";

function makeMockRunner(responses) {
  return {
    async run(file, args) {
      const key = `${file} ${args.join(" ")}`;
      if (key in responses) {
        return responses[key];
      }
      return { stdout: "", stderr: "not mocked", exitCode: 1 };
    },
  };
}

const dummyBinaryRunner = {
  async runBinary() {
    return { stdout: Buffer.alloc(0), stderr: "", exitCode: 1 };
  },
};

test("PrivilegeBackend selects root when su -c id returns uid=0", async () => {
  const runner = makeMockRunner({
    "su -c id": { stdout: "uid=0(root) gid=0(root)", stderr: "", exitCode: 0 },
  });
  const backend = new PrivilegeBackend(runner, dummyBinaryRunner, "auto");
  assert.equal(await backend.detect(), "root");
});

test("PrivilegeBackend falls back to shizuku when su fails and rish returns uid=2000", async () => {
  const runner = makeMockRunner({
    "su -c id": { stdout: "", stderr: "permission denied", exitCode: 1 },
    "rish -c id": { stdout: "uid=2000(shell) gid=2000(shell)", stderr: "", exitCode: 0 },
  });
  const backend = new PrivilegeBackend(runner, dummyBinaryRunner, "auto");
  assert.equal(await backend.detect(), "shizuku");
});

test("PrivilegeBackend falls back to termux-api when neither su nor rish is active", async () => {
  const runner = makeMockRunner({
    "which termux-notification": {
      stdout: "/data/data/com.termux/files/usr/bin/termux-notification\n",
      stderr: "",
      exitCode: 0,
    },
  });
  const backend = new PrivilegeBackend(runner, dummyBinaryRunner, "auto");
  assert.equal(await backend.detect(), "termux-api");
});

test("PrivilegeBackend falls back to none when no elevated backend exists", async () => {
  const backend = new PrivilegeBackend(makeMockRunner({}), dummyBinaryRunner, "auto");
  assert.equal(await backend.detect(), "none");
});

test("PrivilegeBackend respects explicit AGY_PRIVILEGE_MODE override", async () => {
  const backend = new PrivilegeBackend(makeMockRunner({}), dummyBinaryRunner, "shizuku");
  assert.equal(await backend.detect(), "shizuku");
});

test("parseUiTreeXml filters empty containers and calculates center coordinates", () => {
  const sampleXml = `<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
  <hierarchy rotation="0">
    <node index="0" text="" class="android.widget.FrameLayout" clickable="false" scrollable="false" bounds="[0,0][1080,2400]">
      <node index="0" text="Settings &amp; Privacy" resource-id="com.android.settings:id/title" class="android.widget.TextView" package="com.android.settings" clickable="true" scrollable="false" enabled="true" bounds="[100,200][500,400]" />
      <node index="1" text="" content-desc="Navigate up" class="android.widget.ImageButton" clickable="true" scrollable="false" enabled="true" bounds="[0,80][120,200]" />
    </node>
  </hierarchy>`;

  const nodes = parseUiTreeXml(sampleXml);
  assert.equal(nodes.length, 2);

  assert.equal(nodes[0].text, "Settings & Privacy");
  assert.equal(nodes[0].className, "TextView");
  assert.equal(nodes[0].clickable, true);
  assert.deepEqual(nodes[0].center, { x: 300, y: 300 });

  assert.equal(nodes[1].contentDesc, "Navigate up");
  assert.deepEqual(nodes[1].center, { x: 60, y: 140 });
});

test("parseQuotaPayloads normalizes buckets, group buckets, proto3 zero quotas, and family slugs", () => {
  const fetchModels = {
    models: {
      "gemini-4-argon-medium": {
        displayName: "Gemini 4 Argon (Medium)",
        quotaInfo: {
          remainingFraction: 0.72,
          resetTime: "2026-11-23T12:00:00Z",
        },
        beyondModelInfo: {
          quotaBucketName: "Gemini Pro",
        },
      },
      "gemini-4-neon-high": {
        displayName: "Gemini 4 Neon (High)",
        quotaInfo: {
          // proto3 omits remainingFraction when 0
          resetTime: "2026-11-23T11:30:00Z",
        },
      },
    },
  };
  const summary = {
    buckets: [
      {
        bucketId: "gemini-pro",
        displayName: "Gemini Pro",
        window: "5h",
        remainingFraction: 0.72,
        resetTime: "2026-11-23T12:00:00Z",
      },
    ],
    groups: [
      {
        displayName: "Partner Models",
        buckets: [
          {
            bucketId: "gemini-pro",
            displayName: "Gemini Pro",
          },
          {
            bucketId: "claude-sonnet",
            displayName: "Claude Sonnet",
            disabled: true,
          },
        ],
      },
    ],
  };

  const parsed = parseQuotaPayloads(fetchModels, summary, { email: "user@example.com" });
  assert.equal(parsed.ok, true);
  assert.equal(parsed.email, "user@example.com");
  assert.equal(parsed.buckets.length, 3);
  assert.equal(parsed.buckets[0].remainingFraction, 0.72);
  assert.equal(parsed.buckets[1].disabled, true);
  assert.equal(parsed.buckets[2].bucketId, "gemini-models");
  assert.equal(parsed.buckets[2].remainingFraction, 0.72);
  assert.equal(parsed.models["gemini-4-argon-medium"].remainingFraction, 0.72);
  assert.equal(parsed.models["gemini-4-argon"].remainingFraction, 0.72);
  assert.equal(parsed.models["gemini-4-neon-high"].remainingFraction, 0);
  assert.equal(parsed.models["gemini-4-neon"].remainingFraction, 0);
  assert.ok(parsed.models["__gemini_pool__"]);
});
