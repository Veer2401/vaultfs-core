import auth.AuthManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import utils.Banner;
import utils.Colors;

/** Entry point for the File System Simulator CLI. */
public class Main {
    private static final int CLEAR_SCREEN_LINES = 50;

    /** Functional interface for a single CLI command handler. */
    @FunctionalInterface
    private interface Command {
        /** Returns false to terminate the REPL loop. */
        boolean run(String[] tokens, filesystem.FileSystem fs);
    }

    /** Ordered registry of command name -> handler. */
    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("pwd",    (t, fs) -> { fs.pwd(); return true; });
        COMMANDS.put("cd",     (t, fs) -> { requireArg(t, "cd"); if (t.length >= 2) fs.cd(t[1]); return true; });
        COMMANDS.put("mkdir",  (t, fs) -> { requireArg(t, "mkdir"); if (t.length >= 2) fs.mkdir(t[1]); return true; });
        COMMANDS.put("rmdir",  (t, fs) -> {
            requireArg(t, "rmdir");
            if (t.length >= 2) {
                if (t.length > 2 && "-f".equals(t[1])) fs.rmdir(t[2], true);
                else fs.rmdir(t[1], false);
            }
            return true;
        });
        COMMANDS.put("rename", (t, fs) -> {
            if (t.length < 4) { System.out.println(Colors.c(Colors.RED, "Usage: rename <file|directory> <old> <new>")); }
            else if ("file".equalsIgnoreCase(t[1])) fs.renameFile(t[2], t[3]);
            else if ("directory".equalsIgnoreCase(t[1]) || "dir".equalsIgnoreCase(t[1])) fs.renameDirectory(t[2], t[3]);
            else System.out.println(Colors.c(Colors.RED, "Specify rename target: file or directory."));
            return true;
        });
        COMMANDS.put("create", (t, fs) -> { requireArg(t, "create"); if (t.length >= 2) fs.createFile(t[1]); return true; });
        COMMANDS.put("delete", (t, fs) -> { requireArg(t, "delete"); if (t.length >= 2) fs.deleteFile(t[1]); return true; });
        COMMANDS.put("info",   (t, fs) -> { requireArg(t, "info"); if (t.length >= 2) fs.info(t[1]); return true; });
        COMMANDS.put("find",   (t, fs) -> { requireArg(t, "find"); if (t.length >= 2) fs.find(t[1]); return true; });
        COMMANDS.put("search", (t, fs) -> {
            if (t.length < 2) { System.out.println(Colors.c(Colors.RED, "Missing argument. Type 'help' for usage.")); }
            else if ("-t".equals(t[1])) {
                if (t.length < 3) System.out.println(Colors.c(Colors.RED, "Missing argument for 'search -t'. Type 'help' for usage."));
                else fs.searchByType(t[2]);
            } else { fs.find(t[1]); }
            return true;
        });
        COMMANDS.put("ls", (t, fs) -> {
            boolean detailed = false; String sortFlag = null;
            for (int i = 1; i < t.length; i++) {
                if ("-l".equals(t[i])) detailed = true;
                else if ("-name".equals(t[i]) || "-size".equals(t[i]) || "-date".equals(t[i])) sortFlag = t[i];
            }
            fs.ls(detailed, sortFlag);
            return true;
        });
        COMMANDS.put("tree", (t, fs) -> {
            if (t.length > 1) fs.tree(t[1]);
            else System.out.println(Colors.c(Colors.RED, "Path is required. Usage: tree <path>"));
            return true;
        });
        COMMANDS.put("ln", (t, fs) -> {
            if (t.length > 3 && "-s".equals(t[1])) fs.createSymlink(t[2], t[3]);
            else System.out.println(Colors.c(Colors.RED, "Usage: ln -s <target> <link>"));
            return true;
        });
        COMMANDS.put("topk", (t, fs) -> {
            if (t.length > 2) {
                try { fs.topK(Integer.parseInt(t[1]), t[2]); }
                catch (NumberFormatException e) { System.out.println(Colors.c(Colors.RED, "Invalid number. Usage: topk <k> <path>")); }
            } else { System.out.println(Colors.c(Colors.RED, "Path is required. Usage: topk <k> <path>")); }
            return true;
        });
        COMMANDS.put("duplicates", (t, fs) -> { fs.findDuplicates(); return true; });
        COMMANDS.put("dedupe", (t, fs) -> { fs.removeDuplicates(); return true; });
        COMMANDS.put("cp", (t, fs) -> {
            if (t.length < 3) { System.out.println(Colors.c(Colors.RED, "Usage: cp <source> <dest> or cp -r <source> <dest>")); }
            else if (t.length > 3 && "-r".equals(t[1])) fs.copy(t[2], t[3], true);
            else fs.copy(t[1], t[2], false);
            return true;
        });
        COMMANDS.put("mv", (t, fs) -> {
            if (t.length < 3) { System.out.println(Colors.c(Colors.RED, "Usage: mv <source> <dest>")); }
            else fs.move(t[1], t[2]);
            return true;
        });
        COMMANDS.put("trash", (t, fs) -> {
            if (t.length < 2) { fs.listTrash(); }
            else if ("list".equalsIgnoreCase(t[1])) { fs.listTrash(); }
            else if ("empty".equalsIgnoreCase(t[1])) { fs.emptyTrash(); }
            else if ("size".equalsIgnoreCase(t[1])) { fs.trashSize(); }
            else if ("restore".equalsIgnoreCase(t[1])) {
                if (t.length < 3) System.out.println(Colors.c(Colors.RED, "Usage: trash restore <filename>"));
                else fs.restoreFromTrashByName(t[2]);
            }
            else { System.out.println(Colors.c(Colors.RED, "Usage: trash [list|empty|size|restore <filename>]")); }
            return true;
        });
        COMMANDS.put("help",   (t, fs) -> { printHelp(); return true; });
        COMMANDS.put("whoami", (t, fs) -> { AuthManager.whoami(); return true; });
        COMMANDS.put("clear",  (t, fs) -> { clearScreen(); return true; });
        COMMANDS.put("exit",   (t, fs) -> { AuthManager.logout(); System.out.println(Colors.c(Colors.GREEN, "Goodbye!")); return false; });
        COMMANDS.put("logout", (t, fs) -> { AuthManager.logout(); System.out.println(Colors.c(Colors.GREEN, "Goodbye!")); return false; });
    }

    /** Starts the command loop and routes input to FileSystem operations. */
    public static void main(String[] args) {
        Banner.print();

        // Auto login if not logged in
        if (!AuthManager.isLoggedIn()) {
            AuthManager.startLoginFlow();
        }

        // Show welcome
        System.out.println(Colors.c(Colors.WHITE, "Welcome back, ") + Colors.c(Colors.YELLOW, AuthManager.getUserName()) + Colors.c(Colors.WHITE, "!"));
        System.out.println(Colors.c(Colors.GRAY, "─────────────────────────────────────"));
        System.out.println();

        filesystem.FileSystem fs = new filesystem.FileSystem();
        Scanner scanner = new Scanner(System.in);
        java.util.Queue<String> commandBuffer = new java.util.LinkedList<>();

        while (true) {
            if (commandBuffer.isEmpty()) {
                System.out.print(Colors.c(Colors.GREEN + Colors.BOLD, fs.currentDirectory.absolutePath + "> "));
                String line = scanner.nextLine();
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }

                enqueueCommands(line, commandBuffer);
            }

            if (commandBuffer.isEmpty()) {
                continue;
            }

            String currentCmd = commandBuffer.poll();
            if (!executeCommand(currentCmd, fs)) {
                break;
            }
        }

        scanner.close();
    }

    /** Splits a line into semicolon-separated commands and appends non-empty values. */
    private static void enqueueCommands(String line, java.util.Queue<String> commandBuffer) {
        String[] cmds = line.split(";");
        for (String cmd : cmds) {
            String trimmed = cmd.trim();
            if (!trimmed.isEmpty()) {
                commandBuffer.offer(trimmed);
            }
        }
    }

    /** Prints a missing-argument message if fewer than 2 tokens are provided. */
    private static void requireArg(String[] tokens, String cmdName) {
        if (tokens.length < 2) {
            System.out.println(Colors.c(Colors.RED, "Missing argument for '" + cmdName + "'. Type 'help' for usage."));
        }
    }

    /** Dispatches a parsed command through the registry; returns false to exit. */
    private static boolean executeCommand(String currentCmd, filesystem.FileSystem fs) {
        String[] tokens = currentCmd.split("\\s+");
        String command = tokens[0];

        Command handler = COMMANDS.get(command);
        if (handler != null) {
            try {
                return handler.run(tokens, fs);
            } catch (Exception e) {
                System.out.println(Colors.c(Colors.RED, "Error: " + e.getMessage()));
                return true;
            }
        }

        System.out.println(Colors.c(Colors.RED, "[command not found] type 'help' to see all commands"));
        return true;
    }

    /** Prints the complete command help table. */
    private static void printHelp() {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              File System Simulator — Commands              ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        printHelpRow("pwd", "Print current path");
        printHelpRow("cd <name>", "Navigate into directory");
        printHelpRow("cd ..", "Go one level up");
        printHelpRow("cd /", "Go to root");
        printHelpRow("mkdir <name>", "Create directory");
        printHelpRow("rmdir <name>", "Delete empty directory");
        printHelpRow("rmdir -f <name>", "Force delete directory");
        printHelpRow("rename file <old> <new>", "Rename a file");
        printHelpRow("rename directory <old> <new>", "Rename a directory");
        printHelpRow("create <name>", "Create empty file");
        printHelpRow("delete <name>", "Delete file");
        printHelpRow("info <name>", "Show file metadata");
        printHelpRow("find <name>", "Find file in tree");
        printHelpRow("search -t <type>", "Search files by type");
        printHelpRow("ls", "List current directory");
        printHelpRow("ls -l", "Detailed listing");
        printHelpRow("tree <path>", "Print subtree");
        printHelpRow("topk <k> <path>", "Top k in specific path");
        printHelpRow("duplicates", "Find duplicate files by hash");
        printHelpRow("dedupe", "Remove duplicate files (keep first)");
        printHelpRow("cp <src> <dest>", "Copy file");
        printHelpRow("cp -r <src> <dest>", "Copy directory recursively");
        printHelpRow("mv <src> <dest>", "Move/rename file or directory");
        printHelpRow("trash", "List trash/recycle bin");
        printHelpRow("trash empty", "Empty entire trash");
        printHelpRow("trash size", "Show trash size");
        printHelpRow("trash restore <file>", "Restore file to original location");
        printHelpRow("whoami", "Show account details");
        printHelpRow("clear", "Clear terminal");
        printHelpRow("exit", "Logout and exit program");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        printHelpRow("vaultfs --version", "Show installed version");
        printHelpRow("vaultfs update", "Pull latest updates and rebuild");
        printHelpRow("vaultfs doctor", "Run health checks on install");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    private static void printHelpRow(String cmd, String desc) {
        String paddedCmd = String.format("%-28s", cmd);
        String paddedDesc = String.format("%-27s", desc);
        System.out.println("║ " + Colors.c(Colors.YELLOW, paddedCmd) + "   " + Colors.c(Colors.WHITE, paddedDesc) + " ║");
    }

    /** Prints 50 newlines to clear the terminal view. */
    private static void clearScreen() {
        for (int i = 0; i < CLEAR_SCREEN_LINES; i++) {
            System.out.println();
        }
    }
}
