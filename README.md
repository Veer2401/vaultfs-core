# 🗂️ File System Directory Simulator

> A CLI-based file system simulator written in **Core Java** — using custom implementations of Tree, LinkedList, HashMap, and MaxHeap — that also mirrors every operation on your **real disk** inside a safe sandbox folder.

<br/>

## 📌 What is this?

This project simulates how operating systems manage files and directories using fundamental data structures. Every command you type:
1. Updates the **in-memory data structures** (Tree, LinkedList, HashMap, Heap)
2. Makes **real changes on disk** inside a `sandbox/` folder
3. Exports the current state to `state.json` for frontend visualization

Built as a DSA mini project demonstrating real-world applications of data structures.

<br/>

## 🧱 Data Structures Used

| Data Structure | Role |
|---|---|
| **Tree** (N-ary) | Models directory hierarchy — root → folders → subfolders |
| **LinkedList** (custom) | Stores files inside each directory in insertion order |
| **HashMap** (custom) | Maps filename → metadata for O(1) lookup per directory |
| **MaxHeap** (custom) | Finds top-k largest files efficiently across the entire tree |

> All four are implemented **from scratch** — no `java.util.LinkedList`, `java.util.HashMap`, or `java.util.PriorityQueue` used.

<br/>

### 3. Build the Frontend
```bash
cd frontend
npm install
npm run build
cd ..
```

### 4. Compile & Run the Java CLI
```bash
# Compile
javac -d out src/models/*.java src/datastructures/*.java src/utils/*.java src/auth/*.java src/sync/*.java src/filesystem/*.java src/Main.java

# Run
java -cp out Main
```

---

## Available Commands

### Navigation & Creation
*   `pwd` - Print working directory
*   `ls [-size|-name|-date]` - List directory contents (supports sorting)
*   `cd <dir>` - Change directory
*   `cd -` - Go back to previous directory (Stack history)
*   `mkdir <name>` - Create directory
*   `touch <name> [content]` - Create file
*   `ln -s <target> <link_name>` - Create a symlink (shortcut)

### File Operations
*   `cat <name>` - Read file contents
*   `rm <name>` - Remove file or directory
*   `find <name>` - Fast global file search (Uses BST)
*   `mv <source> <dest>` - Move or rename

### Authentication & Sync
*   `whoami` - Show logged-in user details
*   `logout` - Clear local auth tokens
*   `sync` - Sync state.json to Firebase (if configured)

### System
*   `clear` - Clear console output
*   `exit` - Save state and exit simulator

---

## Technical Details

The file system is represented as a traditional n-ary tree where each node is a `FileNode` or `DirectoryNode`. The root directory `/` is initialized on startup.
*   **Directory Lookups:** Backed by a global `HashMap<String, DirectoryNode>` for $O(1)$ absolute path resolution.
*   **Search:** Handled by an auxiliary Binary Search Tree maintaining all file names for $O(\log n)$ lookups.
*   **File Storage:** Simulated using an array of disk blocks and a LinkedList mapping the blocks for each file.

---

## Project Structure

```
file-system-simulator/
├── src/
│   ├── models/
│   │   ├── FileMetadata.java     # File metadata (name, size, type, timestamps)
│   │   └── FileNode.java         # Directory node in the tree
│   ├── datastructures/
│   │   ├── DirectoryTree.java    # N-ary tree for directory hierarchy
│   │   ├── FileLinkedList.java   # Custom singly LinkedList for files
│   │   ├── FileHashMap.java      # Custom HashMap with separate chaining
│   │   └── FileHeap.java         # Custom MaxHeap for top-k queries
│   ├── auth/
│   │   ├── OAuth.java            # OAuth 2.0 authentication flow
│   │   └── AuthServer.java       # Embedded HTTP server for OAuth
│   ├── sync/
│   │   └── FirebaseSync.java     # Sync state.json to Firebase
│   ├── filesystem/
│   │   └── FileSystem.java       # Core engine — wires all DS + disk ops
│   ├── utils/
│   │   └── JsonExporter.java     # Serializes state to state.json
│   └── Main.java                 # CLI entry point
├── frontend/
│   ├── public/
│   │   └── index.html            # React frontend entry point
│   ├── src/
│   │   ├── App.js                # React app component
│   │   └── index.js              # React app entry point
│   └── package.json              # React app dependencies
├── PLAN.md                       # Architecture and design decisions
├── EXECUTION_PLAN.md             # Phase-wise build plan
└── .gitignore
```

---

## Build & Run

**Requirements:** Java 11+

```bash
# Clone the repo
git clone https://github.com/pranavdadhe1806/file-system-simulator.git
cd file-system-simulator

# Compile
javac -d out src/models/*.java src/datastructures/*.java src/utils/*.java src/auth/*.java src/sync/*.java src/filesystem/*.java src/Main.java

# Run
java -cp out Main
```

---

## Commands

### Navigation
| Command | Example | Description |
|---|---|---|
| `pwd` | `pwd` | Print current directory path |
| `cd <name>` | `cd photos` | Navigate into a directory |
| `cd ..` | `cd ..` | Go one level up |
| `cd /` | `cd /` | Go back to root |
| `cd <path>` | `cd home/user/docs` | Navigate directly to a path |

### Directory Operations
| Command | Example | Description |
|---|---|---|
| `mkdir <name>` | `mkdir photos` | Create a directory |
| `rmdir <name>` | `rmdir photos` | Delete an empty directory |
| `rmdir -f <name>` | `rmdir -f photos` | Force delete with all contents |
| `rename <old> <new>` | `rename photos pics` | Rename a directory |

### File Operations
| Command | Example | Description |
|---|---|---|
| `create <name> <bytes>` | `create photo.jpg 2048` | Create a file with size in bytes |
| `delete <name>` | `delete photo.jpg` | Delete a file |
| `rename <old> <new>` | `rename a.txt b.txt` | Rename a file |
| `info <name>` | `info photo.jpg` | Show full file metadata |

### Listing & Search
| Command | Example | Description |
|---|---|---|
| `ls` | `ls` | List files and folders |
| `ls -l` | `ls -l` | Detailed listing with metadata |
| `tree` | `tree` | Print full ASCII directory tree |
| `tree <path>` | `tree home/user` | Print subtree from a path |
| `find <name>` | `find photo.jpg` | Find file anywhere in tree |
| `search -t <type>` | `search -t jpg` | Find all files of a type |
| `topk <k>` | `topk 5` | Top k largest files (uses MaxHeap) |
| `topk <k> <path>` | `topk 3 home/user` | Top k largest in a specific path |

### Utility
| Command | Description |
|---|---|
| `help` | Show all commands |
| `clear` | Clear the terminal |
| `exit` | Exit the program |

<br/>

## 🖥️ Example Session

```
Welcome to File System Simulator!
/sandbox> mkdir home
Directory 'home' created successfully.

/sandbox> cd home
/sandbox/home> create resume.pdf 51200
File 'resume.pdf' (50.0 KB) created successfully.

/sandbox/home> create photo.jpg 2097152
File 'photo.jpg' (2.0 MB) created successfully.

/sandbox/home> ls -l
NAME                         SIZE        TYPE      MODIFIED
photo.jpg                    2.0 MB      jpg       2024-03-29 10:22:05
resume.pdf                   50.0 KB     pdf       2024-03-29 10:22:01

/sandbox/home> topk 1
1. photo.jpg — 2.0 MB — /sandbox/home/photo.jpg

/sandbox/home> find resume.pdf
Found: /sandbox/home/resume.pdf

/sandbox/home> tree
/
└── home/
    ├── photo.jpg
    └── resume.pdf

/sandbox/home> cd ..
/sandbox> rmdir -f home
Directory 'home' removed successfully.

/sandbox> exit
Goodbye!
```

<br/>

## 🔄 How It Works

```
User types command in terminal
        ↓
   Main.java parses input
        ↓
   FileSystem.java executes:
    ├── Updates Tree / LinkedList / HashMap / Heap
    ├── Mirrors operation on disk (sandbox/)
    └── Writes state.json via JsonExporter
        ↓
   Prompt updates to reflect current directory
```

<br/>

## 📤 state.json

After every command, the simulator exports a `state.json` snapshot:

```json
{
  "currentPath": "/sandbox/home",
  "tree": {
    "name": "/",
    "path": "/sandbox",
    "isDirectory": true,
    "files": [],
    "children": [
      {
        "name": "home",
        "path": "/sandbox/home",
        "isDirectory": true,
        "files": [
          {
            "filename": "resume.pdf",
            "sizeBytes": 51200,
            "formattedSize": "50.0 KB",
            "type": "pdf",
            "createdAt": "2024-03-29 10:22:01",
            "modifiedAt": "2024-03-29 10:22:01"
          }
        ],
        "children": []
      }
    ]
  },
  "heap": [
    {
      "filename": "resume.pdf",
      "absolutePath": "/sandbox/home/resume.pdf",
      "sizeBytes": 51200
    }
  ]
}
```

This is consumed by the **React frontend** (separate repo) to visualize the Tree, Heap, and HashMap in real time.

<br/>

## 🔗 Related

- **Frontend Visualization Repo** — React app that reads `state.json` and renders interactive DS visualizations *(coming soon)*

<br/>

## 📄 License

MIT
