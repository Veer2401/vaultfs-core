#!/bin/bash
# ──────────────────────────────────────────────────────────────
# VaultFS Installer
# Usage: curl -fsSL https://raw.githubusercontent.com/ThreatGuardian/vaultfs-core/main/install.sh | bash
# ──────────────────────────────────────────────────────────────

set -e

# ─── Colors ───────────────────────────────────────────────────
BOLD='\033[1m'
CYAN='\033[36m'
GREEN='\033[32m'
RED='\033[31m'
YELLOW='\033[33m'
DIM='\033[2m'
RESET='\033[0m'

# ─── Helpers ──────────────────────────────────────────────────
print_success() { echo -e "  ${GREEN}✓${RESET} $1"; }
print_error()   { echo -e "  ${RED}✗${RESET} $1"; }
print_info()    { echo -e "  ${CYAN}→${RESET} $1"; }
print_step()    { echo -e "\n${BOLD}$1${RESET}"; }

INSTALL_DIR="$HOME/.vaultfs"
REPO_URL="https://github.com/ThreatGuardian/vaultfs-core.git"

# ─── Banner ───────────────────────────────────────────────────
echo ""
echo -e "${CYAN}${BOLD}  ╔══════════════════════════════════════╗${RESET}"
echo -e "${CYAN}${BOLD}  ║                                      ║${RESET}"
echo -e "${CYAN}${BOLD}  ║         ${GREEN}▓▓ VaultFS Installer${CYAN}         ║${RESET}"
echo -e "${CYAN}${BOLD}  ║                                      ║${RESET}"
echo -e "${CYAN}${BOLD}  ╚══════════════════════════════════════╝${RESET}"
echo -e "  ${DIM}Secure file system simulator for your terminal.${RESET}"
echo ""

# ─── Detect OS ────────────────────────────────────────────────
print_step "Detecting environment..."

OS="unknown"
if [[ "$OSTYPE" == "darwin"* ]]; then
    OS="macos"
elif [[ "$OSTYPE" == "linux"* ]]; then
    if grep -qiE "(Microsoft|WSL)" /proc/version 2>/dev/null; then
        OS="wsl"
    else
        OS="linux"
    fi
fi

if [[ "$OS" == "unknown" ]]; then
    print_error "Unsupported operating system: ${BOLD}$OSTYPE${RESET}"
    exit 1
fi

print_success "OS detected: ${BOLD}$OS${RESET}"

# ─── Check Java 11+ ──────────────────────────────────────────
print_step "Checking prerequisites..."

if ! command -v java &>/dev/null; then
    print_error "Java is not installed."
    echo ""
    if [[ "$OS" == "macos" ]]; then
        echo -e "  Install with Homebrew:"
        echo -e "    ${CYAN}brew install openjdk@17${RESET}"
    else
        echo -e "  Install with apt:"
        echo -e "    ${CYAN}sudo apt update && sudo apt install openjdk-17-jdk${RESET}"
    fi
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n1 | sed -E 's/.*"([0-9]+)(\.[0-9]+)*.*/\1/')
if [[ "$JAVA_VERSION" -lt 11 ]]; then
    print_error "Java 11+ required, found Java ${BOLD}$JAVA_VERSION${RESET}."
    echo ""
    if [[ "$OS" == "macos" ]]; then
        echo -e "  Upgrade with Homebrew:"
        echo -e "    ${CYAN}brew install openjdk@17${RESET}"
    else
        echo -e "  Upgrade with apt:"
        echo -e "    ${CYAN}sudo apt update && sudo apt install openjdk-17-jdk${RESET}"
    fi
    exit 1
fi

print_success "Java ${BOLD}$JAVA_VERSION${RESET} found"

# ─── Check Node.js 18+ ───────────────────────────────────────
if ! command -v node &>/dev/null; then
    print_error "Node.js is not installed."
    echo ""
    if [[ "$OS" == "macos" ]]; then
        echo -e "  Install with Homebrew:"
        echo -e "    ${CYAN}brew install node${RESET}"
    else
        echo -e "  Install with nvm or apt:"
        echo -e "    ${CYAN}curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -${RESET}"
        echo -e "    ${CYAN}sudo apt install -y nodejs${RESET}"
    fi
    exit 1
fi

NODE_VERSION=$(node -v | sed -E 's/v([0-9]+).*/\1/')
if [[ "$NODE_VERSION" -lt 18 ]]; then
    print_error "Node.js 18+ required, found Node ${BOLD}$NODE_VERSION${RESET}."
    echo ""
    if [[ "$OS" == "macos" ]]; then
        echo -e "  Upgrade with Homebrew:"
        echo -e "    ${CYAN}brew install node${RESET}"
    else
        echo -e "  Upgrade with nvm or apt:"
        echo -e "    ${CYAN}curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -${RESET}"
        echo -e "    ${CYAN}sudo apt install -y nodejs${RESET}"
    fi
    exit 1
fi

print_success "Node.js ${BOLD}$NODE_VERSION${RESET} found"

if ! command -v npm &>/dev/null; then
    print_error "npm is not installed. It usually comes with Node.js."
    exit 1
fi

print_success "npm $(npm -v) found"

if ! command -v git &>/dev/null; then
    print_error "git is not installed."
    echo ""
    if [[ "$OS" == "macos" ]]; then
        echo -e "  Install with: ${CYAN}xcode-select --install${RESET}"
    else
        echo -e "  Install with: ${CYAN}sudo apt install git${RESET}"
    fi
    exit 1
fi

print_success "git found"

# ─── Install directory ────────────────────────────────────────
print_step "Setting up VaultFS..."

UPGRADE_MODE=false

if [[ -d "$INSTALL_DIR" ]]; then
    echo ""
    echo -e "  ${YELLOW}VaultFS is already installed at ${BOLD}$INSTALL_DIR${RESET}"
    read -rp "  Reinstall? (y/n) " choice
    if [[ "$choice" != "y" && "$choice" != "Y" ]]; then
        read -rp "  Would you like to upgrade instead? (y/n) " upgrade_choice
        if [[ "$upgrade_choice" != "y" && "$upgrade_choice" != "Y" ]]; then
            echo ""
            print_info "Installation cancelled."
            exit 0
        fi
        UPGRADE_MODE=true
    else
        print_info "Removing existing installation..."
        rm -rf "$INSTALL_DIR"
    fi
fi

# ─── Clone or pull repo ──────────────────────────────────────
if [[ "$UPGRADE_MODE" == true ]]; then
    print_info "Pulling latest changes..."
    cd "$INSTALL_DIR"
    if ! git pull 2>/dev/null; then
        print_error "git pull failed"
        exit 1
    fi
    print_success "Repository updated"
else
    print_info "Cloning repository..."
    if ! git clone --depth 1 "$REPO_URL" "$INSTALL_DIR" 2>/dev/null; then
        print_error "Failed to clone repository from ${BOLD}$REPO_URL${RESET}"
        exit 1
    fi
    print_success "Repository cloned to ${DIM}$INSTALL_DIR${RESET}"
fi

# ─── Build frontend ──────────────────────────────────────────
print_step "Building frontend..."

cd "$INSTALL_DIR/frontend"

print_info "Installing npm dependencies..."
if ! npm install --silent 2>/dev/null; then
    print_error "npm install failed."
    exit 1
fi
print_success "Dependencies installed"

print_info "Building React app..."
if ! npm run build --silent 2>/dev/null; then
    print_error "Frontend build failed."
    exit 1
fi
print_success "Frontend built"

# ─── Compile Java ─────────────────────────────────────────────
print_step "Compiling Java sources..."

cd "$INSTALL_DIR"

if ! javac -d out \
    src/models/*.java \
    src/datastructures/*.java \
    src/utils/*.java \
    src/auth/*.java \
    src/sync/*.java \
    src/filesystem/*.java \
    src/Main.java 2>/dev/null; then
    print_error "Java compilation failed."
    exit 1
fi

print_success "All sources compiled"

# ─── Read version ────────────────────────────────────────────
VAULTFS_VERSION=$(cat "$INSTALL_DIR/version.txt" 2>/dev/null || echo "unknown")
print_success "Version: ${BOLD}v$VAULTFS_VERSION${RESET}"

# ─── Create launcher ─────────────────────────────────────────
print_step "Creating launcher..."

mkdir -p "$INSTALL_DIR/bin"

cat > "$INSTALL_DIR/bin/vaultfs" << 'LAUNCHER'
#!/bin/bash
INSTALL_DIR="$HOME/.vaultfs"

if [ "$1" = "--version" ] || [ "$1" = "-v" ]; then
  echo "VaultFS v$(cat $INSTALL_DIR/version.txt)"
  exit 0
fi

if [ "$1" = "update" ]; then
  echo "Checking for updates..."
  cd $INSTALL_DIR
  git fetch origin main
  LOCAL=$(git rev-parse HEAD)
  REMOTE=$(git rev-parse origin/main)
  if [ "$LOCAL" = "$REMOTE" ]; then
    echo "✅ Already up to date."
  else
    echo "Update available! Pulling latest..."
    git pull origin main
    # rebuild frontend
    cd frontend && npm install && npm run build && cd ..
    # recompile java
    javac -d out src/models/*.java src/datastructures/*.java src/utils/*.java src/auth/*.java src/sync/*.java src/filesystem/*.java src/Main.java
    echo "✅ VaultFS updated to v$(cat $INSTALL_DIR/version.txt)!"
  fi
  exit 0
fi

if [ "$1" = "doctor" ]; then
  echo ""
  echo "🔍 VaultFS Doctor — Health Check"
  echo "================================="
  # Check Java
  if command -v java &>/dev/null; then
    echo "✅ Java: $(java -version 2>&1 | head -1)"
  else
    echo "❌ Java: NOT FOUND"
  fi
  # Check Node
  if command -v node &>/dev/null; then
    echo "✅ Node: $(node --version)"
  else
    echo "❌ Node: NOT FOUND"
  fi
  # Check Git
  if command -v git &>/dev/null; then
    echo "✅ Git: $(git --version)"
  else
    echo "❌ Git: NOT FOUND"
  fi
  # Check install dir
  if [ -d "$INSTALL_DIR" ]; then
    echo "✅ Install dir: $INSTALL_DIR exists"
  else
    echo "❌ Install dir: $INSTALL_DIR NOT FOUND"
  fi
  # Check compiled classes
  if [ -f "$INSTALL_DIR/out/Main.class" ]; then
    echo "✅ Java classes: compiled"
  else
    echo "❌ Java classes: NOT compiled — run 'vaultfs update' to fix"
  fi
  # Check frontend build
  if [ -d "$INSTALL_DIR/frontend/dist" ]; then
    echo "✅ Frontend: built"
  else
    echo "❌ Frontend: NOT built — run 'vaultfs update' to fix"
  fi
  # Check version
  if [ -f "$INSTALL_DIR/version.txt" ]; then
    echo "✅ Version: v$(cat $INSTALL_DIR/version.txt)"
  else
    echo "⚠️  Version file missing"
  fi
  echo ""
  exit 0
fi

# Default: launch the app
java -cp "$INSTALL_DIR/out" Main "$@"
LAUNCHER

chmod +x "$INSTALL_DIR/bin/vaultfs"
print_success "Launcher created at ${DIM}$INSTALL_DIR/bin/vaultfs${RESET}"

# ─── Add to PATH ─────────────────────────────────────────────
print_step "Configuring PATH..."

EXPORT_LINE='export PATH="$HOME/.vaultfs/bin:$PATH"'
SHELL_CONFIG=""

if [[ -f "$HOME/.zshrc" ]]; then
    SHELL_CONFIG="$HOME/.zshrc"
elif [[ -f "$HOME/.bashrc" ]]; then
    SHELL_CONFIG="$HOME/.bashrc"
elif [[ -f "$HOME/.bash_profile" ]]; then
    SHELL_CONFIG="$HOME/.bash_profile"
else
    # Create .bashrc as fallback
    SHELL_CONFIG="$HOME/.bashrc"
    touch "$SHELL_CONFIG"
fi

if ! grep -qF '.vaultfs/bin' "$SHELL_CONFIG" 2>/dev/null; then
    echo "" >> "$SHELL_CONFIG"
    echo "# VaultFS" >> "$SHELL_CONFIG"
    echo "$EXPORT_LINE" >> "$SHELL_CONFIG"
    print_success "PATH added to ${BOLD}$(basename "$SHELL_CONFIG")${RESET}"
else
    print_success "PATH already configured in ${BOLD}$(basename "$SHELL_CONFIG")${RESET}"
fi

SHELL_NAME=$(basename "$SHELL_CONFIG")

# ─── Done ─────────────────────────────────────────────────────
echo ""
if [[ "$UPGRADE_MODE" == true ]]; then
    echo -e "${GREEN}${BOLD}  ╔══════════════════════════════════════╗${RESET}"
    echo -e "${GREEN}${BOLD}  ║                                      ║${RESET}"
    echo -e "${GREEN}${BOLD}  ║   ✅  VaultFS upgraded to v${VAULTFS_VERSION}!     ║${RESET}"
    echo -e "${GREEN}${BOLD}  ║                                      ║${RESET}"
    echo -e "${GREEN}${BOLD}  ╚══════════════════════════════════════╝${RESET}"
    echo ""
    echo -e "  Type ${CYAN}vaultfs${RESET} to start."
else
    echo -e "${GREEN}${BOLD}  ╔══════════════════════════════════════╗${RESET}"
    echo -e "${GREEN}${BOLD}  ║                                      ║${RESET}"
    echo -e "${GREEN}${BOLD}  ║     ✅  VaultFS installed!           ║${RESET}"
    echo -e "${GREEN}${BOLD}  ║                                      ║${RESET}"
    echo -e "${GREEN}${BOLD}  ╚══════════════════════════════════════╝${RESET}"
    echo ""
    echo -e "  To get started, run:"
    echo ""
    echo -e "    ${CYAN}source ~/$SHELL_NAME${RESET}"
    echo -e "    ${CYAN}vaultfs${RESET}"
fi
echo ""
echo -e "  ${DIM}Installed to: $INSTALL_DIR${RESET}"
echo -e "  ${DIM}Launcher:     $INSTALL_DIR/bin/vaultfs${RESET}"
echo ""
