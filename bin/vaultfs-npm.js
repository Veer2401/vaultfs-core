#!/usr/bin/env node
const { execSync, spawn } = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");

// ─── Colors ──────────────────────────────────────────────────
const RESET = "\x1b[0m";
const BOLD = "\x1b[1m";
const RED = "\x1b[31m";
const GREEN = "\x1b[32m";
const CYAN = "\x1b[36m";

const installDir = path.join(os.homedir(), ".vaultfs");
const npmCmd = process.platform === "win32" ? "npm.cmd" : "npm";
const npxCmd = process.platform === "win32" ? "npx.cmd" : "npx";
const versionFile = path.join(installDir, "version.txt");
const args = process.argv.slice(2);
const cmd = args[0];

// ─── --version / -v ──────────────────────────────────────────
if (cmd === "--version" || cmd === "-v") {
  try {
    const ver = fs.readFileSync(versionFile, "utf8").trim();
    console.log(`VaultFS v${ver}`);
  } catch {
    console.log("VaultFS (version unknown)");
  }
  process.exit(0);
}

// ─── update ──────────────────────────────────────────────────
if (cmd === "update") {
  console.log("Checking for updates...");
  try {
    execSync("git fetch origin main", { cwd: installDir, stdio: "inherit" });
    const local = execSync("git rev-parse HEAD", { cwd: installDir, encoding: "utf8" }).trim();
    const remote = execSync("git rev-parse origin/main", { cwd: installDir, encoding: "utf8" }).trim();

    if (local === remote) {
      console.log("✅ Already up to date.");
    } else {
      console.log("Update available! Pulling latest...");
      execSync("git pull origin main", { cwd: installDir, stdio: "inherit" });

      // Rebuild frontend
      const frontendDir = path.join(installDir, "frontend");
      execSync(`${npmCmd} install`, { cwd: frontendDir, stdio: "inherit" });
      execSync(`${npxCmd} vite build`, { cwd: frontendDir, stdio: "inherit" });

      // Recompile Java
      execSync("javac -d out src/models/*.java src/datastructures/*.java src/utils/*.java src/auth/*.java src/sync/*.java src/filesystem/*.java src/Main.java", {
        cwd: installDir,
        stdio: "inherit",
        shell: true
      });

      const ver = fs.readFileSync(versionFile, "utf8").trim();
      console.log(`✅ VaultFS updated to v${ver}!`);
    }
  } catch (err) {
    console.error(`${RED}Update failed: ${err.message}${RESET}`);
    process.exit(1);
  }
  process.exit(0);
}

// ─── doctor ──────────────────────────────────────────────────
if (cmd === "doctor") {
  console.log("");
  console.log("🔍 VaultFS Doctor — Health Check");
  console.log("=================================");

  // Java
  try {
    const javaOut = execSync("java -version 2>&1", { encoding: "utf8" });
    const firstLine = javaOut.split("\n")[0];
    console.log(`✅ Java: ${firstLine}`);
  } catch {
    console.log("❌ Java: NOT FOUND");
  }

  // Node
  try {
    const nodeVer = execSync("node --version", { encoding: "utf8" }).trim();
    console.log(`✅ Node: ${nodeVer}`);
  } catch {
    console.log("❌ Node: NOT FOUND");
  }

  // Git
  try {
    const gitVer = execSync("git --version", { encoding: "utf8" }).trim();
    console.log(`✅ Git: ${gitVer}`);
  } catch {
    console.log("❌ Git: NOT FOUND");
  }

  // Install dir
  if (fs.existsSync(installDir)) {
    console.log(`✅ Install dir: ${installDir} exists`);
  } else {
    console.log(`❌ Install dir: ${installDir} NOT FOUND`);
  }

  // Compiled classes
  if (fs.existsSync(path.join(installDir, "out", "Main.class"))) {
    console.log("✅ Java classes: compiled");
  } else {
    console.log("❌ Java classes: NOT compiled — run 'vaultfs update' to fix");
  }

  // Frontend build
  if (fs.existsSync(path.join(installDir, "frontend", "dist"))) {
    console.log("✅ Frontend: built");
  } else {
    console.log("❌ Frontend: NOT built — run 'vaultfs update' to fix");
  }

  // Version
  if (fs.existsSync(versionFile)) {
    const ver = fs.readFileSync(versionFile, "utf8").trim();
    console.log(`✅ Version: v${ver}`);
  } else {
    console.log("⚠️  Version file missing");
  }

  console.log("");
  process.exit(0);
}

// ─── Default: launch the app ─────────────────────────────────
const outDir = path.join(installDir, "out");

if (!fs.existsSync(path.join(outDir, "Main.class"))) {
  console.error(`${RED}VaultFS is not compiled. Run 'vaultfs doctor' to check your installation.${RESET}`);
  process.exit(1);
}

const child = spawn("java", [`-Dvaultfs.home=${installDir}`, "-cp", outDir, "Main", ...args], {
  stdio: "inherit",
  cwd: installDir
});

child.on("exit", (code) => {
  process.exit(code ?? 0);
});
