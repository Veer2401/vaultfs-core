#!/bin/bash
# ──────────────────────────────────────────────────────────────
# VaultFS Uninstaller
# Usage: curl -fsSL https://raw.githubusercontent.com/ThreatGuardian/vaultfs-core/main/uninstall.sh | bash
# ──────────────────────────────────────────────────────────────

set -e

# ─── Colors ───────────────────────────────────────────────────
BOLD='\033[1m'
RED='\033[31m'
YELLOW='\033[33m'
GREEN='\033[32m'
CYAN='\033[36m'
DIM='\033[2m'
RESET='\033[0m'

# ─── Helpers ──────────────────────────────────────────────────
print_success() { echo -e "  ${GREEN}✓${RESET} $1"; }
print_error()   { echo -e "  ${RED}✗${RESET} $1"; }
print_info()    { echo -e "  ${CYAN}→${RESET} $1"; }

INSTALL_DIR="$HOME/.vaultfs"

# ─── Banner ───────────────────────────────────────────────────
echo ""
echo -e "${RED}${BOLD}  ╔══════════════════════════════════════╗${RESET}"
echo -e "${RED}${BOLD}  ║                                      ║${RESET}"
echo -e "${RED}${BOLD}  ║       ${YELLOW}▓▓ VaultFS Uninstaller${RED}        ║${RESET}"
echo -e "${RED}${BOLD}  ║                                      ║${RESET}"
echo -e "${RED}${BOLD}  ╚══════════════════════════════════════╝${RESET}"
echo ""

# ─── Confirm ─────────────────────────────────────────────────
if [[ ! -d "$INSTALL_DIR" ]]; then
    print_info "VaultFS is not installed at ${BOLD}$INSTALL_DIR${RESET}. Nothing to do."
    exit 0
fi

echo -e "  ${YELLOW}Are you sure you want to uninstall VaultFS?${RESET}"
echo -e "  This will delete ${BOLD}$INSTALL_DIR${RESET} and remove it from your PATH."
echo ""
read -rp "  Continue? (y/n) " choice

if [[ "$choice" != "y" && "$choice" != "Y" ]]; then
    echo ""
    print_info "Uninstall cancelled."
    exit 0
fi

echo ""

# ─── Remove install directory ────────────────────────────────
print_info "Removing ${DIM}$INSTALL_DIR${RESET}..."
rm -rf "$INSTALL_DIR"
print_success "Installation directory removed"

# ─── Remove PATH entry from shell config ─────────────────────
SHELL_CONFIG=""
PATH_REMOVED=false

for rc in "$HOME/.zshrc" "$HOME/.bashrc" "$HOME/.bash_profile"; do
    if [[ -f "$rc" ]] && grep -qF '.vaultfs/bin' "$rc" 2>/dev/null; then
        SHELL_CONFIG="$rc"
        # Handle macOS vs Linux sed -i difference
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' '/\.vaultfs\/bin/d' "$rc"
            sed -i '' '/# VaultFS/d' "$rc"
        else
            sed -i '/\.vaultfs\/bin/d' "$rc"
            sed -i '/# VaultFS/d' "$rc"
        fi
        PATH_REMOVED=true
        print_success "PATH entry removed from ${BOLD}$(basename "$rc")${RESET}"
    fi
done

if [[ "$PATH_REMOVED" == false ]]; then
    print_info "No PATH entry found in shell config files"
fi

# Determine which shell config to source
if [[ -n "$SHELL_CONFIG" ]]; then
    SHELL_NAME=$(basename "$SHELL_CONFIG")
else
    if [[ -f "$HOME/.zshrc" ]]; then
        SHELL_NAME=".zshrc"
    elif [[ -f "$HOME/.bashrc" ]]; then
        SHELL_NAME=".bashrc"
    else
        SHELL_NAME=".bash_profile"
    fi
fi

# ─── Done ─────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}${BOLD}  ╔══════════════════════════════════════╗${RESET}"
echo -e "${GREEN}${BOLD}  ║                                      ║${RESET}"
echo -e "${GREEN}${BOLD}  ║     ✅  VaultFS uninstalled!         ║${RESET}"
echo -e "${GREEN}${BOLD}  ║                                      ║${RESET}"
echo -e "${GREEN}${BOLD}  ╚══════════════════════════════════════╝${RESET}"
echo ""
echo -e "  Run ${CYAN}source ~/$SHELL_NAME${RESET} to apply PATH changes."
echo ""
