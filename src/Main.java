import auth.AuthManager;
import java.util.Scanner;
import utils.Banner;
import utils.Colors;

/** Entry point for the File System Simulator CLI. */
public class Main {
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

        while (true) {
            System.out.print(Colors.c(Colors.GREEN + Colors.BOLD, fs.currentDirectory.absolutePath + "> "));
            String line = scanner.nextLine();
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            String[] tokens = line.trim().split("\\s+");
            String command = tokens[0];
            try {
                if ("pwd".equals(command)) {
                    fs.pwd();
                } else if ("cd".equals(command)) {
                    fs.cd(tokens[1]);
                } else if ("mkdir".equals(command)) {
                    fs.mkdir(tokens[1]);
                } else if ("rmdir".equals(command) && tokens.length > 2 && "-f".equals(tokens[1])) {
                    fs.rmdir(tokens[2], true);
                } else if ("rmdir".equals(command)) {
                    fs.rmdir(tokens[1], false);
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
                    fs.createFile(tokens[1]);
                } else if ("delete".equals(command)) {
                    fs.deleteFile(tokens[1]);
                } else if ("info".equals(command)) {
                    fs.info(tokens[1]);
                } else if ("find".equals(command)) {
                    fs.find(tokens[1]);
                } else if ("search".equals(command) && tokens.length > 2 && "-t".equals(tokens[1])) {
                    fs.searchByType(tokens[2]);
                } else if ("search".equals(command)) {
                    fs.find(tokens[1]);
                } else if ("ls".equals(command) && tokens.length > 1 && "-l".equals(tokens[1])) {
                    fs.ls(true);
                } else if ("ls".equals(command)) {
                    fs.ls(false);
                } else if ("tree".equals(command) && tokens.length > 1) {
                    fs.tree(tokens[1]);
                } else if ("tree".equals(command)) {
                    System.out.println(Colors.c(Colors.RED, "Path is required. Usage: tree <path>"));
                } else if ("topk".equals(command) && tokens.length > 2) {
                    fs.topK(Integer.parseInt(tokens[1]), tokens[2]);
                } else if ("topk".equals(command)) {
                    System.out.println(Colors.c(Colors.RED, "Path is required. Usage: topk <k> <path>"));
                } else if ("help".equals(command)) {
                    printHelp();
                } else if ("whoami".equals(command)) {
                    AuthManager.whoami();
                } else if ("clear".equals(command)) {
                    clearScreen();
                } else if ("exit".equals(command) || "logout".equals(command)) {
                    AuthManager.logout();
                    System.out.println(Colors.c(Colors.GREEN, "Goodbye!"));
                    break;
                } else {
                    System.out.println(Colors.c(Colors.RED, "[command not found] type 'help' to see all commands"));
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println(Colors.c(Colors.RED, "Missing argument for '" + command + "'. Type 'help' for usage."));
            } catch (NumberFormatException e) {
                System.out.println(Colors.c(Colors.RED, "Invalid number. Usage: topk <k>"));
            } catch (Exception e) {
                System.out.println(Colors.c(Colors.RED, "Error: " + e.getMessage()));
            }
        }

        scanner.close();
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
        for (int i = 0; i < 50; i++) {
            System.out.println();
        }
    }
}
