#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

echo "=== Agy Mobile Bridge: Termux Setup ==="

pkg update -y
pkg install -y nodejs-lts git

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BRIDGE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$BRIDGE_DIR"
npm install
npm run build

mkdir -p "$HOME/bin"
cat > "$HOME/bin/agy-bridge-start" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
export PATH="\$HOME/bin:\$PATH"
cd "$BRIDGE_DIR"
exec node dist/server.js "\$@"
EOF
chmod +x "$HOME/bin/agy-bridge-start"

echo ""
echo "Setup complete!"
echo "1. Ensure 'agy' is installed and authenticated (run: agy acp --login)"
echo "2. Optional non-root phone control: install Shizuku and link 'rish' into \$PREFIX/bin"
echo "3. Start the bridge with: ~/bin/agy-bridge-start"
echo "   Or for LAN/Tailscale access: ~/bin/agy-bridge-start --host 0.0.0.0"
