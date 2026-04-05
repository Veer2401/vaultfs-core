package auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.UUID;
import utils.Colors;

/** Manages local AuthFS login state, device identity, and account display. */
public class AuthManager {
    private static final String TOKEN_DIR = System.getProperty("user.home") + "/.authfs";
    private static final String TOKEN_FILE = TOKEN_DIR + "/token";
    private static final String EMAIL_FILE = TOKEN_DIR + "/email";
    private static final String NAME_FILE = TOKEN_DIR + "/name";
    private static final String CONFIG_FILE = TOKEN_DIR + "/config";

    /** Returns whether a non-empty token file exists. */
    public static boolean isLoggedIn() {
        File token = new File(TOKEN_FILE);
        return token.exists() && token.length() > 0;
    }

    /** Returns the saved user email or Unknown when not available. */
    public static String getUserEmail() {
        String email = readFile(EMAIL_FILE);
        if (email == null || email.isEmpty()) {
            return "Unknown";
        }
        return email;
    }

    /** Returns the saved user name or Unknown when not available. */
    public static String getUserName() {
        String name = readFile(NAME_FILE);
        if (name == null || name.isEmpty()) {
            return "Unknown";
        }
        return name;
    }

    /** Returns the device ID from config or generates and persists a new UUID. */
    public static String getDeviceId() {
        String deviceId = readFile(CONFIG_FILE);
        if (deviceId != null && !deviceId.isEmpty()) {
            return deviceId;
        }

        new File(TOKEN_DIR).mkdirs();
        String generated = UUID.randomUUID().toString();
        writeFile(CONFIG_FILE, generated);
        return generated;
    }

    /** Starts browser-based login, waits for callback, and stores token and email. */
    public static void startLoginFlow() {
        try {
            String sessionToken = UUID.randomUUID().toString();
            String authURL = "http://localhost:9000/login?session=" + sessionToken;

            System.out.println(Colors.c(Colors.WHITE, "Opening browser for authentication..."));
            System.out.println(Colors.c(Colors.GRAY, "→ " + authURL));

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[] {"cmd", "/c", "start", authURL});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[] {"open", authURL});
            } else {
                Runtime.getRuntime().exec(new String[] {"xdg-open", authURL});
            }

            System.out.println(Colors.c(Colors.GRAY, "Waiting for authentication... (timeout: 120s)"));

            final HttpServer server = HttpServer.create(new InetSocketAddress(9000), 0);
            
            server.createContext("/login", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    File file = new File(System.getProperty("user.dir") + "/frontend/login.html");
                    String response = "";
                    if (file.exists()) {
                        response = readFile(file.getAbsolutePath());
                    } else {
                        response = "<html><body><h1>Error: login.html not found</h1></body></html>";
                    }
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes("UTF-8"));
                    os.close();
                }
            });

            server.createContext("/callback", new HttpHandler() {
                /** Handles auth callback, persists credentials, and completes login flow. */
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    String query = exchange.getRequestURI().getQuery();
                    String token = "";
                    String email = "Unknown";
                    String name = "Guest";
                    boolean failed = false;

                    if (query != null && !query.isEmpty()) {
                        String[] parts = query.split("&");
                        for (String part : parts) {
                            String[] kv = part.split("=", 2);
                            if (kv.length == 2) {
                                String key = kv[0];
                                String value = URLDecoder.decode(kv[1], "UTF-8");
                                if ("token".equals(key)) {
                                    token = value;
                                } else if ("email".equals(key)) {
                                    email = value;
                                } else if ("name".equals(key)) {
                                    name = value;
                                } else if ("status".equals(key) && "failed".equals(value)) {
                                    failed = true;
                                }
                            }
                        }
                    }

                    if (failed) {
                        token = "guest-token";
                        email = "guest@local";
                        name = "Guest";
                    }

                    new File(TOKEN_DIR).mkdirs();
                    writeFile(TOKEN_FILE, token);
                    writeFile(EMAIL_FILE, email);
                    writeFile(NAME_FILE, name);

                    File successPage = new File(System.getProperty("user.dir") + "/frontend/success.html");
                    String response;
                    if (successPage.exists()) {
                        response = readFile(successPage.getAbsolutePath());
                    } else {
                        response = "<html><body><h1>Authentication successful!</h1><p>You can close this tab.</p></body></html>";
                    }
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes("UTF-8"));
                    os.close();

                    System.out.println(Colors.c(Colors.GREEN, "✓") + " Logged in as " + Colors.c(Colors.YELLOW, name) + " (" + email + ")");
                    server.stop(0);
                }
            });
            server.setExecutor(null);
            server.start();

            for (int i = 0; i < 120; i++) {
                Thread.sleep(1000);
                if (isLoggedIn()) {
                    break;
                }
            }

            if (!isLoggedIn()) {
                System.out.println(Colors.c(Colors.RED, "Login timeout. Please try again."));
                server.stop(0);
            }
        } catch (Exception e) {
            System.out.println(Colors.c(Colors.RED, "Login failed: " + e.getMessage()));
        }
    }

    /** Clears local auth files and logs the user out. */
    public static void logout() {
        new File(TOKEN_FILE).delete();
        new File(EMAIL_FILE).delete();
        new File(NAME_FILE).delete();
        System.out.println(Colors.c(Colors.GREEN, "✓") + " Logged out successfully");
    }

    /** Prints formatted account details when logged in. */
    public static void whoami() {
        if (!isLoggedIn()) {
            System.out.println(Colors.c(Colors.RED, "Not logged in."));
            return;
        }

        String email = getUserEmail();
        String deviceId = getDeviceId();

        String borderTop = "┌─────────────────────────────────────┐";
        String borderMid = "├─────────────────────────────────────┤";
        String borderBottom = "└─────────────────────────────────────┘";

        System.out.println(Colors.c(Colors.GRAY, borderTop));
        System.out.println(Colors.c(Colors.GRAY, "│") + "         Account Details             " + Colors.c(Colors.GRAY, "│"));
        System.out.println(Colors.c(Colors.GRAY, borderMid));
        System.out.println(Colors.c(Colors.GRAY, "│") + " "
                + Colors.c(Colors.GRAY, "Email") + "     : "
                + Colors.c(Colors.YELLOW, email)
                + "          " + Colors.c(Colors.GRAY, "│"));
        System.out.println(Colors.c(Colors.GRAY, "│") + " "
                + Colors.c(Colors.GRAY, "Device ID") + " : "
                + Colors.c(Colors.CYAN, deviceId)
                + "             " + Colors.c(Colors.GRAY, "│"));
        System.out.println(Colors.c(Colors.GRAY, "│") + " "
                + Colors.c(Colors.GRAY, "Status") + "    : "
                + Colors.c(Colors.GREEN, "● Online")
                + "                " + Colors.c(Colors.GRAY, "│"));
        System.out.println(Colors.c(Colors.GRAY, borderBottom));
    }

    /** Writes file content to the given path and reports failures. */
    private static void writeFile(String path, String content) {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(content == null ? "" : content);
        } catch (IOException e) {
            System.out.println(Colors.c(Colors.RED, "Failed to write file: " + path));
        }
    }

    /** Reads and trims file content or returns null on failure. */
    private static String readFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return null;
        }
    }
}
