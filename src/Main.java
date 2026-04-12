import auth.AuthManager;
import java.util.Scanner;
import utils.Banner;
import utils.Colors;

/** Entry point for the File System Simulator CLI. */
public class Main {
    private static final int CLEAR_SCREEN_LINES = 50;

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

    /** Executes one parsed command; returns false when the loop should terminate. */
    private static boolean executeCommand(String currentCmd, filesystem.FileSystem fs) {
        String[] tokens = currentCmd.split("\\s+");
        String command = tokens[0];

        try {
            if ("pwd".equals(command)) {
                fs.pwd();
            } else if ("cd".equals(command)) {
                if (tokens.length < 2) {
                    System.out.println(Colors.c(Colors.RED, "Missing argument for 'cd'. Type 'help' for usage."));
                } else {
                    fs.cd(tokens[1]);
                }
            } else if ("mkdir".equals(command)) {
                if (tokens.length < 2) {
                    System.out.println(Colors.c(Colors.RED, "Missing argument for 'mkdir'. Type 'help' for usage."));
                } else {
                    fs.mkdir(tokens[1]);
                }
            } else if ("rmdir".equals(command)) {
                if (tokens.length < 2) {
                    System.out.println(Colors.c(Colors.RED, "Missing argument for 'rmdir'. Type 'help' for usage."));
                } else if (tokens.length > 2 && "-f".equals(tokens[1])) {
                    fs.rmdir(tokens[2], true);
                } else {
                    fs.rmdir(tokens[1], false);
                }
            } else if ("rename".equals(command)) {
                if (tokens.length < 4) {
                    System.out.println(Colors.c(Colors.RED, "Usage: rename <file|directory> <old> <new>"));
                } else if ("file".equalsIgnoreCase(tokens[1])) {
                    fs.renameFile(tokens[2], tokens[3]);
                } else if ("directory".equalsIgnoreCase(tokens[1]) || "dir".equalsIgnoreCase(tokens[1])) {
                    fs.renameDirectory(tokens[2], tokens[3]);
                } else {
                    System.out.println(Colors.c(Colors.RED, "Specify rename target: file or directory."));
                }
            } else if ("create".equals(command)) {
                if (tokens.length < 2) {
                    System.out.println(Colors.c(Colors.RED, "Missing argument for 'create'. Type 'help' for usage."));
                } else {
                    fs.createFile(tokens[1]);
                }
            } else if ("delete".equals(command)) {
                if (tokens.length < 2) {
                    System.out.println(Colors.c(Colors.RED, "Missing argument for 'delete'. Type 'help' for usage."));
                } else {
                    fs.deleteFile(tokens[1]);
                }
            } else if ("info".equals(command)) {
                if (tokens.length < 2) {
                    System.out.println(Colors.c(Colors.RED, "Missing argument for 'info'. Type 'help' for usage."));
                } else {
                    fs.info(tokens[1]);
                }
            } else if ("find".equals(command) || "search".equals(command)) {
                if (tokens.length < 2) {
                    System.out.println(Colors.c(Colors.RED, "Missing argument. Type 'help' for usage."));
                } else if ("search".equals(command) && "-t".equals(tokens[1])) {
                    if (tokens.length < 3) {
                        System.out.println(Colors.c(Colors.RED, "Missing argument for 'search -t'. Type 'help' for usage."));
                    } else {
                        fs.searchByType(tokens[2]);
                    }
                } else {
                    fs.find(tokens[1]);
                }
            } else if ("ls".equals(command)) {
                boolean detailed = false;
                String sortFlag = null;
                for (int i = 1; i < tokens.length; i++) {
                    if ("-l".equals(tokens[i])) {
                        detailed = true;
                    } else if ("-name".equals(tokens[i]) || "-size".equals(tokens[i]) || "-date".equals(tokens[i])) {
                        sortFlag = tokens[i];
                    }
                }
                fs.ls(detailed, sortFlag);
            } else if ("tree".equals(command)) {
                if (tokens.length > 1) {
                    fs.tree(tokens[1]);
                } else {
                    System.out.println(Colors.c(Colors.RED, "Path is required. Usage: tree <path>"));
                }
            } else if ("ln".equals(command)) {
                if (tokens.length > 3 && "-s".equals(tokens[1])) {
                    fs.createSymlink(tokens[2], tokens[3]);
                } else {
                    System.out.println(Colors.c(Colors.RED, "Usage: ln -s <target> <link>"));
                }
            } else if ("topk".equals(command)) {
                if (tokens.length > 2) {
                    try {
                        fs.topK(Integer.parseInt(tokens[1]), tokens[2]);
                    } catch (NumberFormatException e) {
                        System.out.println(Colors.c(Colors.RED, "Invalid number. Usage: topk <k> <path>"));
                    }
                } else {
                    System.out.println(Colors.c(Colors.RED, "Path is required. Usage: topk <k> <path>"));
                }
            } else if ("help".equals(command)) {
                printHelp();
            } else if ("whoami".equals(command)) {
                AuthManager.whoami();
            } else if ("clear".equals(command)) {
                clearScreen();
            } else if ("exit".equals(command) || "logout".equals(command)) {
                AuthManager.logout();
                System.out.println(Colors.c(Colors.GREEN, "Goodbye!"));
                return false;
            } else {
                System.out.println(Colors.c(Colors.RED, "[command not found] type 'help' to see all commands"));
            }
        } catch (Exception e) {
            System.out.println(Colors.c(Colors.RED, "Error: " + e.getMessage()));
        }

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
        printHelpRow("whoami", "Show account details");
        printHelpRow("clear", "Clear terminal");
        printHelpRow("exit", "Logout and exit program");
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
