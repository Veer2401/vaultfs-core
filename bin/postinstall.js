#!/usr/bin/env node
const { execSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");

// ─── Colors ──────────────────────────────────────────────────
const RESET = "\x1b[0m";
const BOLD = "\x1b[1m";
const RED = "\x1b[31m";
const GREEN = "\x1b[32m";
const CYAN = "\x1b[36m";
const DIM = "\x1b[2m";

function success(msg) { console.log(`  ${GREEN}✓${RESET} ${msg}`); }
function error(msg) { console.log(`  ${RED}✗${RESET} ${msg}`); }
function info(msg) { console.log(`  ${CYAN}→${RESET} ${msg}`); }

// ─── Banner ──────────────────────────────────────────────────
console.log("");
console.log(`${CYAN}${BOLD}  ╔══════════════════════════════════════════════╗${RESET}`);
console.log(`${CYAN}${BOLD}  ║                                              ║${RESET}`);
console.log(`${CYAN}${BOLD}  ║   ${GREEN}▓▓ VaultFS — Running post-install setup${CYAN}   ║${RESET}`);
console.log(`${CYAN}${BOLD}  ║                                              ║${RESET}`);
console.log(`${CYAN}${BOLD}  ╚══════════════════════════════════════════════╝${RESET}`);
console.log("");

try {
  // ─── Detect OS ─────────────────────────────────────────────
  const platform = process.platform; // win32, darwin, linux
  const platformName =
    platform === "win32" ? "Windows" :
    platform === "darwin" ? "macOS" : "Linux";
  success(`OS detected: ${BOLD}${platformName}${RESET}`);

  // ─── Check Java 11+ ───────────────────────────────────────
  let javaVersion;
  try {
    const javaOut = execSync("java -version 2>&1", { encoding: "utf8" });
    const match = javaOut.match(/"(\d+)/);
    javaVersion = match ? parseInt(match[1], 10) : 0;
  } catch {
    javaVersion = 0;
  }

  if (javaVersion < 11) {
    error(`${RED}Java 11+ is required${javaVersion > 0 ? ` (found Java ${javaVersion})` : " (not found)"}.${RESET}`);
    console.log("");
    console.log(`  Download Java from: ${CYAN}https://adoptium.net${RESET}`);
    console.log("");
    process.exit(1);
  }
  success(`Java ${BOLD}${javaVersion}${RESET} found`);

  // ─── Install directory ─────────────────────────────────────
  const installDir = path.join(os.homedir(), ".vaultfs");

  if (fs.existsSync(installDir) && fs.existsSync(path.join(installDir, "out", "Main.class"))) {
    info("Already installed, skipping build.");
    console.log("");
    console.log(`  ${GREEN}${BOLD}✅ VaultFS is ready!${RESET} Type ${CYAN}vaultfs${RESET} to launch.`);
    console.log("");
    process.exit(0);
  }

  // ─── Copy package contents into install dir ────────────────
  info(`Setting up ${DIM}${installDir}${RESET}...`);

  if (!fs.existsSync(installDir)) {
    fs.mkdirSync(installDir, { recursive: true });
  }

  // Resolve the package root (one level up from bin/)
  const pkgRoot = path.resolve(__dirname, "..");

  const toCopy = ["src", "frontend", "version.txt", ".env.example"];
  for (const item of toCopy) {
    const src = path.join(pkgRoot, item);
    const dest = path.join(installDir, item);
    if (!fs.existsSync(src)) continue;

    const stat = fs.statSync(src);
    if (stat.isDirectory()) {
      fs.cpSync(src, dest, { recursive: true, force: true });
    } else {
      // Ensure parent dir exists
      const parent = path.dirname(dest);
      if (!fs.existsSync(parent)) fs.mkdirSync(parent, { recursive: true });
      fs.copyFileSync(src, dest);
    }
  }
  success("Source files copied");

  // ─── Platform-aware commands ───────────────────────────────
  const npmCmd = platform === "win32" ? "npm.cmd" : "npm";
  const npxCmd = platform === "win32" ? "npx.cmd" : "npx";

  // ─── Build frontend ────────────────────────────────────────
  info("Installing frontend dependencies...");
  const frontendDir = path.join(installDir, "frontend");
  execSync(`${npmCmd} install`, { cwd: frontendDir, stdio: "inherit" });
  success("Dependencies installed");

  info("Building React app...");
  execSync(`${npxCmd} vite build`, { cwd: frontendDir, stdio: "inherit" });
  success("Frontend built");

  // ─── Compile Java ──────────────────────────────────────────
  info("Compiling Java sources...");
  execSync("javac -d out src/models/*.java src/datastructures/*.java src/utils/*.java src/auth/*.java src/sync/*.java src/filesystem/*.java src/Main.java", {
    cwd: installDir,
    stdio: "inherit",
    shell: true
  });
  success("All sources compiled");

  // ─── Done ──────────────────────────────────────────────────
  console.log("");
  console.log(`  ${GREEN}${BOLD}✅ VaultFS is ready!${RESET} Type ${CYAN}vaultfs${RESET} to launch.`);
  console.log("");

} catch (err) {
  console.log("");
  error(`${RED}Post-install failed: ${err.message}${RESET}`);
  console.log("");
  console.log(`  Run ${CYAN}vaultfs doctor${RESET} to diagnose the issue.`);
  console.log("");
  process.exit(1);
}
