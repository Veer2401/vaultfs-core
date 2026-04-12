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
import java.nio.file.Files;
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

            final String sessionToken = UUID.randomUUID().toString();

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

            

            // Serve the React build index.html at /login
            server.createContext("/login", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    File file = new File(System.getProperty("user.dir") + "/frontend/dist/index.html");
                    String response = "";
                    if (file.exists()) {
                        response = readFile(file.getAbsolutePath());
                    } else {
                        response = "<html><body><h1>Error: frontend build not found. Run npm run build in frontend/</h1></body></html>";
                    }
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes("UTF-8"));
                    os.close();
                }
            });

            // Serve static assets (JS, CSS) from frontend/dist/assets/
            server.createContext("/assets", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    String path = exchange.getRequestURI().getPath();
                    File file = new File(System.getProperty("user.dir") + "/frontend/dist" + path);
                    if (file.exists() && file.isFile()) {
                        String contentType = "application/octet-stream";
                        if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
                        else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
                        else if (path.endsWith(".svg")) contentType = "image/svg+xml";
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        exchange.getResponseHeaders().set("Content-Type", contentType);
                        exchange.sendResponseHeaders(200, bytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(bytes);
                        os.close();
                    } else {
                        String msg = "Not found";
                        exchange.sendResponseHeaders(404, msg.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(msg.getBytes());
                        os.close();
                    }
                }
            });

            // Redirect to Google OAuth
            server.createContext("/auth/google", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    if (!OAuthConfig.isGoogleConfigured()) {
                        String msg = "<html><body style='font-family:sans-serif;background:#000;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh'><div style='text-align:center'><h2>Google OAuth not configured</h2><p style='color:#86868b;margin-top:12px'>Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in .env</p></div></div></body></html>";
                        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                        exchange.sendResponseHeaders(200, msg.getBytes("UTF-8").length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(msg.getBytes("UTF-8"));
                        os.close();
                        return;
                    }
                    String url = OAuthHandler.getGoogleAuthUrl(sessionToken);
                    exchange.getResponseHeaders().set("Location", url);
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                }
            });

            // Redirect to GitHub OAuth
            server.createContext("/auth/github", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    if (!OAuthConfig.isGitHubConfigured()) {
                        String msg = "<html><body style='font-family:sans-serif;background:#000;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh'><div style='text-align:center'><h2>GitHub OAuth not configured</h2><p style='color:#86868b;margin-top:12px'>Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET in .env</p></div></div></body></html>";
                        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                        exchange.sendResponseHeaders(200, msg.getBytes("UTF-8").length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(msg.getBytes("UTF-8"));
                        os.close();
                        return;
                    }
                    String url = OAuthHandler.getGitHubAuthUrl(sessionToken);
                    exchange.getResponseHeaders().set("Location", url);
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                }
            });

            // Google OAuth callback — exchange code for user info
            server.createContext("/callback/google", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    String state = extractQueryParam(exchange.getRequestURI().getQuery(), "state");
                    if (state == null || !state.equals(sessionToken)) {
                        serveError(exchange, "Invalid session. Please restart login.");
                        exchange.close();
                        return;
                    }

                    String code = extractQueryParam(exchange.getRequestURI().getQuery(), "code");
                    String error = extractQueryParam(exchange.getRequestURI().getQuery(), "error");

                    if (error != null || code == null) {
                        serveError(exchange, "Google authentication was cancelled or failed.");
                        return;
                    }

                    String[] result = OAuthHandler.handleGoogleCallback(code);
                    if (result == null) {
                        serveError(exchange, "Failed to verify Google credentials. Please try again.");
                        return;
                    }

                    persistLogin(result[2], result[1], result[0]);
                    serveSuccess(exchange, result[0]);
                    System.out.println(Colors.c(Colors.GREEN, "✓") + " Logged in via Google as "
                        + Colors.c(Colors.YELLOW, result[0]) + " (" + result[1] + ")");
                    server.stop(0);
                }
            });

            // GitHub OAuth callback — exchange code for user info
            server.createContext("/callback/github", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    String state = extractQueryParam(exchange.getRequestURI().getQuery(), "state");
                    if (state == null || !state.equals(sessionToken)) {
                        serveError(exchange, "Invalid session. Please restart login.");
                        exchange.close();
                        return;
                    }

                    String code = extractQueryParam(exchange.getRequestURI().getQuery(), "code");
                    String error = extractQueryParam(exchange.getRequestURI().getQuery(), "error");

                    if (error != null || code == null) {
                        serveError(exchange, "GitHub authentication was cancelled or failed.");
                        return;
                    }

                    String[] result = OAuthHandler.handleGitHubCallback(code);
                    if (result == null) {
                        serveError(exchange, "Failed to verify GitHub credentials. Please try again.");
                        return;
                    }

                    persistLogin(result[2], result[1], result[0]);
                    serveSuccess(exchange, result[0]);
                    System.out.println(Colors.c(Colors.GREEN, "✓") + " Logged in via GitHub as "
                        + Colors.c(Colors.YELLOW, result[0]) + " (" + result[1] + ")");
                    server.stop(0);
                }
            });

            // Guest login callback
            server.createContext("/callback", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    persistLogin("guest-token", "guest@local", "Guest");
                    serveSuccess(exchange, "Guest");
                    System.out.println(Colors.c(Colors.GREEN, "✓") + " Logged in as " + Colors.c(Colors.YELLOW, "Guest"));
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
        System.out.println(Colors.c(Colors.GRAY, "==== Account Details ===="));
        System.out.println("Email    : " + Colors.c(Colors.YELLOW, getUserEmail()));
        System.out.println("Device ID: " + Colors.c(Colors.CYAN, getDeviceId()));
        System.out.println("Status   : " + Colors.c(Colors.GREEN, "● Online"));
        System.out.println(Colors.c(Colors.GRAY, "========================="));
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

    /** Extracts a query parameter value by key from a URL query string. */
    private static String extractQueryParam(String query, String key) {
        if (query == null || query.isEmpty()) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                try {
                    return URLDecoder.decode(kv[1], "UTF-8");
                } catch (Exception e) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    /** Persists login credentials to local auth files. */
    private static void persistLogin(String token, String email, String name) {
        new File(TOKEN_DIR).mkdirs();
        writeFile(TOKEN_FILE, token);
        writeFile(EMAIL_FILE, email);
        writeFile(NAME_FILE, name);
    }

    /** Serves the success HTML page after authentication. */
    private static void serveSuccess(HttpExchange exchange, String name) throws IOException {
        File successPage = new File(System.getProperty("user.dir") + "/frontend/success.html");
        String response;
        if (successPage.exists()) {
            response = readFile(successPage.getAbsolutePath());
        } else {
            response = "<html><head><style>*{margin:0;padding:0;box-sizing:border-box}"
                + "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#000;"
                + "color:#fff;height:100vh;display:flex;align-items:center;justify-content:center;"
                + "text-align:center}h1{font-size:28px;font-weight:600;margin-bottom:12px}"
                + "p{color:#86868b;font-size:17px}</style></head><body><div>"
                + "<h1>You're all set.</h1>"
                + "<p>Welcome, " + name + ". You can close this tab.</p>"
                + "</div></body></html>";
        }
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes("UTF-8"));
        os.close();
    }

    /** Serves a styled error page when authentication fails. */
    private static void serveError(HttpExchange exchange, String message) throws IOException {
        String response = "<html><head><style>*{margin:0;padding:0;box-sizing:border-box}"
            + "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#000;"
            + "color:#fff;height:100vh;display:flex;align-items:center;justify-content:center;"
            + "text-align:center}h1{font-size:24px;font-weight:600;margin-bottom:12px}"
            + "p{color:#86868b;font-size:17px;max-width:360px}"
            + "a{color:#fff;margin-top:24px;display:inline-block;font-size:15px}</style></head>"
            + "<body><div><h1>Authentication Failed</h1>"
            + "<p>" + message + "</p>"
            + "<a href='/login'>Try again</a>"
            + "</div></body></html>";
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes("UTF-8"));
        os.close();
    }
}

