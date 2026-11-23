# Agy Mobile (`com.hambalapps.agymobile`)

A native Kotlin + Jetpack Compose **Material 3 Expressive** Android client for **Antigravity CLI (`agy`)**, speaking the official **Agent Client Protocol (`agy acp`)** through a lightweight bridge daemon (`agy-bridge`) with auto-detecting Android device control (`su` → `Shizuku` → `Termux:API`).

## Architecture

- **`bridge/`** — Node.js + TypeScript daemon (`agy-bridge`) & MCP server (`android-control-mcp`), bundled directly inside the APK (`assets/bridge/`):
  - Spawns official `agy acp` (1st-party Google Antigravity credentials & tools).
  - Exposes authenticated WebSocket (`WS /acp`, `WS /pty`) and HTTP (`/health`, `/api/sessions`, `/api/diff`, `/api/config`) endpoints.
  - Auto-registers `android-control-mcp` when running in Termux (`screenshot`, `ui_tree`, `tap`, `swipe`, `type`, `keyevent`, `launch_app`, `list_apps`, `shell`, `notify`, `clipboard`).
- **`android/`** — Native Android app (`com.hambalapps.agymobile`, `v1.1.0`, minSdk 26, targetSdk 35):
  - **1-Tap Termux Auto-Setup & Auto-Start:** Extracts the bundled `agy-bridge` into Termux, generates and auto-pairs a 256-bit Bearer token, installs Node/dependencies on first launch, and starts the daemon automatically via `com.termux.RUN_COMMAND` or zero-touch root (`su`).
  - **Material 3 Expressive:** Dynamic Color (Monet) + optional AMOLED Pure-Black dark theme, spring physics (`M3ExpressiveMotion`), 10-step corner scale + asymmetric connected button groups (`M3ExpressiveShape`), floating pill toolbar, morphing Send/Stop action button, and `ANIMATOR_DURATION_SCALE` reduced-motion fallback.
  - **Full `agy` CLI parity:** Streaming chat & thinking pills, `/` slash command palette, tool call cards with inline `android_screenshot` previews, interactive Permission Request sheet, Mode/Effort/Model selector, Diff viewer (`/diff`), Artifact review (`/artifact`), Session drawer (`/resume`, `/fork`), embedded PTY terminal (`agy acp --login`), and Android voice dictation (`/voice`).

---

## Setup

### 1. On-Device Termux (1-Tap In-App Setup)
1. Install **Agy Mobile** and ensure **Termux** (`com.termux`) is installed (plus `agy` installed & logged in via `agy acp --login`).
2. In **Agy Mobile**, tap **Start via Termux** (or **Root (su)** for zero-touch setup on rooted phones):
   - **Rooted (`su`):** Automatically enables `allow-external-apps=true`, deploys the bundled bridge into `~/.agy-bridge`, pairs the Bearer token, and starts `agy-bridge`.
   - **Unrooted:** On first use, copy-paste the 1-tap `allow-external-apps=true` command into Termux once and allow the Termux command permission prompt; after that, **Start via Termux** handles setup, token pairing, and startup automatically.

### 2. Remote PC / Server Mode
To control `agy` running on your desktop/server over LAN or Tailscale:

```bash
cd bridge
npm install
npm run build
npm run start:remote
```

Create a Remote profile in **Agy Mobile → `/config`** with your PC's LAN/Tailscale IP, port `4789`, and the printed Bearer token.

### 3. Building the Android APK (`android/`)
The Gradle `syncBridgeAssets` task automatically syncs `bridge/dist/` and `bridge/package.json` into `android/app/src/main/assets/bridge/` on build:

```bash
cd android
.\gradlew.bat testDebugUnitTest assembleDebug
```
