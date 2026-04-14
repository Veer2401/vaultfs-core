#!/usr/bin/env node
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');

const isWin = process.platform === 'win32';
const installDir = path.join(os.homedir(), '.vaultfs');

console.log('\n  \u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557');
console.log('  \u2551   VaultFS \u2014 Running post-install setup       \u2551');
console.log('  \u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D\n');

try {
    // Step 1 — Detect OS
    const os_name = isWin ? 'Windows' : process.platform === 'darwin' ? 'macOS' : 'Linux';
    console.log(`  \u2713 OS detected: ${os_name}`);

    // Step 2 — Check Java 11+
    try {
        execSync('java -version 2>&1', { shell: true });
        console.log('  \u2713 Java found');
    } catch (e) {
        console.error('  \u2717 Java 11+ is required.');
        console.error('  Download from: https://adoptium.net');
        process.exit(1);
    }

    // Step 3 — Copy files to ~/.vaultfs
    console.log(`  \u2192 Setting up ${installDir}...`);
    if (!fs.existsSync(installDir)) {
        fs.mkdirSync(installDir, { recursive: true });
    }

    const pkgDir = path.join(__dirname, '..');
    const itemsToCopy = ['src', 'frontend', 'version.txt', '.env.example'];
    for (const item of itemsToCopy) {
        const src = path.join(pkgDir, item);
        const dest = path.join(installDir, item);
        if (fs.existsSync(src)) {
            if (fs.existsSync(dest)) {
                fs.rmSync(dest, { recursive: true, force: true });
            }
            fs.cpSync(src, dest, { recursive: true });
        }
    }
    console.log('  \u2713 Source files copied');

    // Step 4 — Create .env if missing
    const envFile = path.join(installDir, '.env');
    const envExample = path.join(installDir, '.env.example');
    if (!fs.existsSync(envFile) && fs.existsSync(envExample)) {
        fs.copyFileSync(envExample, envFile);
    }

    // Step 5 — Verify pre-built frontend dist exists
    const distDir = path.join(installDir, 'frontend', 'dist');
    if (fs.existsSync(distDir)) {
        console.log('  \u2713 Frontend ready (pre-built)');
    } else {
        console.log('  \u26A0 Frontend dist not found - login UI may not work');
    }

    // Step 6 — Compile Java
    console.log('  \u2192 Compiling Java sources...');
    const outDir = path.join(installDir, 'out');
    if (!fs.existsSync(outDir)) {
        fs.mkdirSync(outDir, { recursive: true });
    }
    execSync(
        'javac -d out src/models/*.java src/datastructures/*.java src/utils/*.java src/auth/*.java src/sync/*.java src/filesystem/*.java src/Main.java',
        { cwd: installDir, stdio: 'inherit', shell: true }
    );
    console.log('  \u2713 Java compiled');

    console.log('\n  \u2705 VaultFS is ready! Type vaultfs to launch.\n');

} catch (err) {
    console.error(`\n  \u2717 Post-install failed: ${err.message}`);
    console.error('  Run vaultfs doctor to diagnose the issue.\n');
    process.exit(1);
}
