# VaultFS — Secure File System Simulator

A CLI-based file system simulator built entirely in **Core Java** with real **OAuth 2.0 authentication** (Google & GitHub), a built-in **React login frontend**, and advanced **data structure implementations** — Stack, Queue, BST, HashMap, LinkedList, Graphs, and Sorting.

---

## Quick Start

### Prerequisites
- **Java 11+**
- **Node.js** (only needed once, to build the frontend)

### 1. Clone the Repository
```bash
git clone https://github.com/ThreatGuardian/vaultfs-core.git
cd vaultfs-core
```

### 2. Set Up Environment Variables
The project uses a `.env` file for OAuth credentials. **This file is gitignored for security.**

```bash
# Copy the example template
cp .env.example .env
```

> **Without a `.env` file, the app still runs perfectly** — you'll just log in as a Guest. OAuth (Google/GitHub) login requires valid credentials in `.env`.

### 3. Build the Frontend
```bash
cd frontend
npm install
npm run build
cd ..
```

### 4. Compile & Run
```bash
# Compile
javac -d out src/models/*.java src/datastructures/*.java src/utils/*.java src/auth/*.java src/sync/*.java src/filesystem/*.java src/Main.java

# Run
java -cp out Main
```

The app will open a browser for login. Choose **Google**, **GitHub**, or **Continue as Guest**.

---

## Setting Up OAuth (Optional)

If you want Google/GitHub login instead of Guest mode, follow these steps.

### Google OAuth Setup

1. Go to [Google Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials)
2. Click **Create Credentials** → **OAuth client ID**
3. Select **Web application** as the type
4. Under **Authorized redirect URIs**, add:
   ```
   http://localhost:9000/callback/google
   ```
5. Click **Create** and copy the **Client ID** and **Client Secret**
6. Paste them into your `.env` file:
   ```env
   GOOGLE_CLIENT_ID=your-client-id-here.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=your-client-secret-here
   ```

### GitHub OAuth Setup

1. Go to [GitHub → Developer Settings → OAuth Apps](https://github.com/settings/developers)
2. Click **New OAuth App**
3. Fill in:
   - **Application name:** `VaultFS`
   - **Homepage URL:** `http://localhost:9000`
   - **Authorization callback URL:** `http://localhost:9000/callback/github`
4. Click **Register application**
5. Copy the **Client ID**, then click **Generate a new client secret** and copy it
6. Paste them into your `.env` file:
   ```env
   GITHUB_CLIENT_ID=your-client-id-here
   GITHUB_CLIENT_SECRET=your-client-secret-here
   ```

After saving `.env`, restart the app and the Google/GitHub buttons will work.

---

## Data Structures Used

| Data Structure | Feature | Concept |
|---|---|---|
| **Stack** | `cd -` (go back to previous directory) | LIFO directory history |
| **Queue** | `mkdir a ; cd a ; create f.txt` (chained commands) | FIFO command buffer |
| **Binary Search Tree** | `find <filename>` (global search) | O(log n) name lookup |
| **HashMap** | `cd /deep/absolute/path` (instant jump) | O(1) path resolution |
| **Sorting (Merge/Quick)** | `ls -size`, `ls -name`, `ls -date` | Custom comparator sorting |
| **Graph + HashSet** | `ln -s <target> <link>` (symlinks) | Directed graph + cycle detection |
| **LinkedList + Array** | `info <file>` (shows disk blocks) | File fragmentation simulation |
| **N-ary Tree** | Directory hierarchy | Parent-child file system |
| **MaxHeap** | `topk <k> <path>` | Top-k largest files |

---

## Commands

### Navigation
| Command | Description |
|---|---|
| `pwd` | Print current directory path |
| `cd <dir>` | Navigate into a directory |
| `cd ..` | Go one level up |
| `cd /` | Go to filesystem root |
| `cd -` | Go back to previous directory (Stack) |

### Directory Operations
| Command | Description |
|---|---|
| `mkdir <name>` | Create a directory |
| `rmdir <name>` | Delete an empty directory |
| `rmdir -f <name>` | Force delete directory and all contents |
| `rename dir <old> <new>` | Rename a directory |

### File Operations
| Command | Description |
|---|---|
| `create <name>` | Create an empty file |
| `delete <name>` | Delete a file |
| `rename file <old> <new>` | Rename a file |
| `info <name>` | Show file metadata + disk block allocation |

### Listing & Search
| Command | Description |
|---|---|
| `ls` | List files and folders |
| `ls -l` | Detailed listing with size, type, date |
| `ls -size` | Sort by file size (descending) |
| `ls -name` | Sort alphabetically |
| `ls -date` | Sort by modified date (newest first) |
| `find <name>` | Fast global search using BST |
| `search -t <type>` | Find all files of a given type |
| `tree <path>` | Print ASCII directory tree |
| `topk <k> <path>` | Top k largest files in a path |

### Symlinks
| Command | Description |
|---|---|
| `ln -s <target> <link_name>` | Create a symbolic link (with cycle detection) |

### System
| Command | Description |
|---|---|
| `whoami` | Show logged-in user details |
| `logout` | Clear auth tokens and exit |
| `help` | Show all commands |
| `clear` | Clear terminal |
| `exit` | Save state and exit |

> **Tip:** You can chain multiple commands with `;` — e.g. `mkdir test ; cd test ; create hello.txt`

---

## Project Structure

```
vaultfs-core/
├── src/
│   ├── models/
│   │   ├── FileMetadata.java        # File metadata + disk block reference
│   │   └── FileNode.java            # Directory node in the tree
│   ├── datastructures/
│   │   ├── DirectoryTree.java       # N-ary tree + HashMap lookups + BST
│   │   ├── BinarySearchTree.java    # Custom BST for O(log n) file search
│   │   ├── DiskSimulator.java       # LinkedList + Array block fragmentation
│   │   ├── FileLinkedList.java      # Custom singly LinkedList for files
│   │   ├── FileHashMap.java         # Custom HashMap with separate chaining
│   │   └── FileHeap.java            # Custom MaxHeap for top-k queries
│   ├── auth/
│   │   ├── AuthManager.java         # Login flow, whoami, logout
│   │   ├── OAuthConfig.java         # Reads credentials from .env
│   │   └── OAuthHandler.java        # Google/GitHub OAuth 2.0 code exchange
│   ├── sync/
│   │   └── FirestoreSync.java       # Push state.json to Firebase
│   ├── filesystem/
│   │   └── FileSystem.java          # Core engine — all DS + disk ops
│   ├── utils/
│   │   ├── EnvParser.java           # .env file parser
│   │   ├── JsonExporter.java        # Serializes state to state.json
│   │   ├── Colors.java              # ANSI color codes
│   │   └── Banner.java              # ASCII art banner
│   └── Main.java                    # CLI entry point + command router
├── frontend/                        # React login UI (served by Java)
├── .env.example                     # Template for environment variables
├── .gitignore
├── FEATURE_SPEC.md                  # Data structure feature specifications
└── README.md
```

---

## License

MIT
