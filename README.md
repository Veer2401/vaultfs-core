<div align="center">

# 🔐 VaultFS

[![npm version](https://img.shields.io/npm/v/vaultfs?color=green&label=npm)](https://www.npmjs.com/package/vaultfs)
[![npm downloads](https://img.shields.io/npm/dm/vaultfs?color=blue)](https://www.npmjs.com/package/vaultfs)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Node.js](https://img.shields.io/badge/Node.js-18%2B-brightgreen)](https://nodejs.org)
[![Java](https://img.shields.io/badge/Java-11%2B-orange)](https://adoptium.net)
[![Auth Server](https://img.shields.io/badge/Auth%20Server-Live%20on%20Render-purple)](https://vaultfs-auth-server.onrender.com)

**A CLI-based secure file system simulator built in Core Java**
**with real OAuth 2.0 authentication and 9 advanced data structures**

[Install](#-install) • [Usage](#-usage) • [Architecture](#-architecture) • [Data Structures](#-data-structures) • [Commands](#-commands)

</div>

---

## ⚡ Install

```bash
npm install -g vaultfs
```
Then just type:
```bash
vaultfs
```
> Requires Java 11+ and Node.js 18+

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────┐
│                   User's Machine                    │
│                                                     │
│   npm install -g vaultfs                            │
│          ↓                                          │
│   vaultfs (Node.js launcher)                        │
│          ↓                                          │
│   java -cp ~/.vaultfs/out Main                      │
│          ↓                                          │
│   Opens browser ──────────────────────────────┐     │
└───────────────────────────────────────────────│─────┘
                                                │
                                                ↓
┌─────────────────────────────────────────────────────┐
│            Render Cloud (Auth Server)               │
│                                                     │
│   vaultfs-auth-server.onrender.com                  │
│          ↓                                          │
│   /auth/login → Google or GitHub OAuth              │
│          ↓                                          │
│   Credentials NEVER leave this server               │
│          ↓                                          │
│   /auth/poll → sends token back to CLI              │
└─────────────────────────────────────────────────────┘
                                                │
                                                ↓
┌─────────────────────────────────────────────────────┐
│                   User's Terminal                   │
│                                                     │
│   ✅ Logged in as John (google)                     │
│   Welcome back, John!                               │
│                                                     │
│   ~/.vaultfs> _                                     │
└─────────────────────────────────────────────────────┘
```

---

## 🔐 Authentication

VaultFS uses a **centralized auth server** deployed on Render.
Your OAuth credentials never touch the user's machine.

| Provider | Status |
|---|---|
| Google OAuth 2.0 | ✅ Live |
| GitHub OAuth | ✅ Live |
| Guest Mode | ✅ No setup needed |

Auth server: https://vaultfs-auth-server.onrender.com

---

## 🧠 Data Structures

| Data Structure | Feature | Complexity |
|---|---|---|
| **Stack** | `cd -` directory history | O(1) |
| **Queue** | Chained commands with `;` | O(1) |
| **Binary Search Tree** | `find <filename>` global search | O(log n) |
| **HashMap** | `cd /deep/path` instant jump | O(1) |
| **Merge/Quick Sort** | `ls -size`, `ls -name`, `ls -date` | O(n log n) |
| **Graph + HashSet** | `ln -s` symlinks + cycle detection | O(V+E) |
| **LinkedList + Array** | `info` disk block simulation | O(n) |
| **N-ary Tree** | Directory hierarchy | O(n) |
| **MaxHeap** | `topk <k>` largest files | O(n log k) |

---

## 💻 Commands

### Navigation
| Command | Description |
|---|---|
| `pwd` | Print current directory |
| `cd <dir>` | Navigate into directory |
| `cd ..` | Go one level up |
| `cd /` | Go to root |
| `cd -` | Go back (Stack) |

### File Operations
| Command | Description |
|---|---|
| `create <name>` | Create a file |
| `delete <name>` | Delete a file |
| `mkdir <name>` | Create a directory |
| `rmdir <name>` | Delete empty directory |
| `rmdir -f <name>` | Force delete directory |
| `rename file <old> <new>` | Rename a file |
| `rename dir <old> <new>` | Rename a directory |
| `info <name>` | File metadata + disk blocks |

### Search & Listing
| Command | Description |
|---|---|
| `ls` | List contents |
| `ls -l` | Detailed listing |
| `ls -size` | Sort by size |
| `ls -name` | Sort alphabetically |
| `ls -date` | Sort by date |
| `find <name>` | Global search via BST |
| `search -t <type>` | Find by file type |
| `tree <path>` | ASCII directory tree |
| `topk <k> <path>` | Top k largest files |

### Symlinks & System
| Command | Description |
|---|---|
| `ln -s <target> <link>` | Create symlink |
| `whoami` | Show logged-in user |
| `logout` | Clear auth and exit |
| `help` | Show all commands |
| `clear` | Clear terminal |
| `exit` | Save and exit |

---

## 🛠 CLI Tool

| Command | Description |
|---|---|
| `vaultfs` | Launch VaultFS |
| `vaultfs --version` | Show version |
| `vaultfs update` | Pull latest and rebuild |
| `vaultfs doctor` | Health check |

---

## 🖥 Compatibility

| Platform | Status |
|---|---|
| macOS (Intel + Apple Silicon) | ✅ |
| Linux (Ubuntu, Debian, Arch) | ✅ |
| WSL2 | ✅ |
| Windows (CMD / PowerShell) | ✅ |

---

## 📁 Project Structure

```
vaultfs-core/
├── src/                    # Core Java source
│   ├── auth/               # OAuth + auth flow
│   ├── datastructures/     # All 9 DS implementations
│   ├── filesystem/         # File system engine
│   ├── models/             # File + directory models
│   ├── sync/               # Firestore sync
│   ├── utils/              # Logger, colors, banner
│   └── Main.java           # CLI entry point
├── auth-server/            # Node.js OAuth server (Render)
│   └── server.js           # Express auth server
├── frontend/               # React login UI
├── bin/                    # npm launcher scripts
│   ├── vaultfs-npm.js      # CLI entry point
│   └── postinstall.js      # Auto build + compile
├── install.sh              # Bash installer
├── install.bat             # Windows installer
├── uninstall.sh            # Uninstaller
├── package.json            # npm package config
└── version.txt             # Current version
```

---

## 📄 License

MIT © [ThreatGuardian](https://github.com/ThreatGuardian)
