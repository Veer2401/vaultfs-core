package auth;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Handles OAuth 2.0 authorization code exchange and user info retrieval for Google and GitHub. */
public class OAuthHandler {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    private static final String GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USERINFO_URL = "https://api.github.com/user";
    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

    private static final String REDIRECT_BASE = "http://localhost:9000/callback";

    /** Builds the Google OAuth authorization URL for browser redirect with state for CSRF protection. */
    public static String getGoogleAuthUrl(String state) {
        try {
            return GOOGLE_AUTH_URL
                + "?client_id=" + URLEncoder.encode(OAuthConfig.googleClientId(), "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_BASE + "/google", "UTF-8")
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode("openid email profile", "UTF-8")
                + "&access_type=offline"
                + "&prompt=select_account"
                + "&state=" + URLEncoder.encode(state, "UTF-8");
        } catch (Exception e) {
            return GOOGLE_AUTH_URL;
        }
    }

    /** Builds the GitHub OAuth authorization URL for browser redirect with state for CSRF protection. */
    public static String getGitHubAuthUrl(String state) {
        try {
            return GITHUB_AUTH_URL
                + "?client_id=" + URLEncoder.encode(OAuthConfig.githubClientId(), "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_BASE + "/github", "UTF-8")
                + "&scope=" + URLEncoder.encode("read:user user:email", "UTF-8")
                + "&state=" + URLEncoder.encode(state, "UTF-8");
        } catch (Exception e) {
            return GITHUB_AUTH_URL;
        }
    }

    /**
     * Exchanges a Google authorization code for user info.
     * Returns a String array: [name, email, token] or null on failure.
     */
    public static String[] handleGoogleCallback(String code) {
        try {
            // Exchange code for access token
            String tokenBody = "code=" + URLEncoder.encode(code, "UTF-8")
                + "&client_id=" + URLEncoder.encode(OAuthConfig.googleClientId(), "UTF-8")
                + "&client_secret=" + URLEncoder.encode(OAuthConfig.googleClientSecret(), "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_BASE + "/google", "UTF-8")
                + "&grant_type=authorization_code";

            String tokenResponse = httpPost(GOOGLE_TOKEN_URL, tokenBody, "application/x-www-form-urlencoded", null);
            String accessToken = extractJsonValue(tokenResponse, "access_token");

            if (accessToken == null || accessToken.isEmpty()) {
                return null;
            }

            // Get user info
            String userInfo = httpGet(GOOGLE_USERINFO_URL, "Bearer " + accessToken);
            String name = extractJsonValue(userInfo, "name");
            String email = extractJsonValue(userInfo, "email");

            if (name == null || name.isEmpty()) {
                name = extractJsonValue(userInfo, "given_name");
            }
            if (name == null || name.isEmpty()) {
                name = email != null ? email.split("@")[0] : "Google User";
            }
            if (email == null || email.isEmpty()) {
                email = "unknown@gmail.com";
            }

            return new String[] { name, email, accessToken };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Exchanges a GitHub authorization code for user info.
     * Returns a String array: [name, email, token] or null on failure.
     */
    public static String[] handleGitHubCallback(String code) {
        try {
            // Exchange code for access token
            String tokenBody = "client_id=" + URLEncoder.encode(OAuthConfig.githubClientId(), "UTF-8")
                + "&client_secret=" + URLEncoder.encode(OAuthConfig.githubClientSecret(), "UTF-8")
                + "&code=" + URLEncoder.encode(code, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_BASE + "/github", "UTF-8");

            String tokenResponse = httpPost(GITHUB_TOKEN_URL, tokenBody, "application/x-www-form-urlencoded", "application/json");
            String accessToken = extractJsonValue(tokenResponse, "access_token");

            if (accessToken == null || accessToken.isEmpty()) {
                return null;
            }

            // Get user info
            String userInfo = httpGet(GITHUB_USERINFO_URL, "Bearer " + accessToken);
            String name = extractJsonValue(userInfo, "name");
            String login = extractJsonValue(userInfo, "login");

            if (name == null || name.isEmpty()) {
                name = login != null ? login : "GitHub User";
            }

            // Get primary email
            String email = extractJsonValue(userInfo, "email");
            if (email == null || email.isEmpty() || "null".equals(email)) {
                try {
                    String emailsJson = httpGet(GITHUB_EMAILS_URL, "Bearer " + accessToken);
                    email = extractPrimaryEmail(emailsJson);
                } catch (Exception e) {
                    // fallback
                }
            }
            if (email == null || email.isEmpty() || "null".equals(email)) {
                email = (login != null ? login : "user") + "@github.com";
            }

            return new String[] { name, email, accessToken };
        } catch (Exception e) {
            return null;
        }
    }

    /** Sends an HTTP POST and returns the response body. */
    private static String httpPost(String urlStr, String body, String contentType, String accept) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", contentType);
        if (accept != null) {
            conn.setRequestProperty("Accept", accept);
        }
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        return readResponse(conn);
    }

    /** Sends an HTTP GET with an Authorization header and returns the response body. */
    private static String httpGet(String urlStr, String authHeader) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", authHeader);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "VaultFS-CLI");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        return readResponse(conn);
    }

    /** Reads the full response body from an HttpURLConnection. */
    private static String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader;
        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    /**
     * Extracts a string value from a JSON object by key.
     * Simple parser — works for flat JSON responses from OAuth APIs.
     */
    static String extractJsonValue(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;

        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return null;

        // Skip whitespace after colon
        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;

        if (start >= json.length()) return null;

        char ch = json.charAt(start);
        if (ch == '"') {
            // String value
            int end = json.indexOf('"', start + 1);
            if (end < 0) return null;
            return json.substring(start + 1, end);
        } else if (ch == 'n') {
            return null; // null value
        } else {
            // Number or other
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }

    /** Extracts the primary email from GitHub's /user/emails JSON array response. */
    private static String extractPrimaryEmail(String json) {
        if (json == null || !json.contains("\"primary\"")) return null;
        // Find entries with "primary":true
        int idx = 0;
        while (idx < json.length()) {
            int emailIdx = json.indexOf("\"email\"", idx);
            if (emailIdx < 0) break;
            int primaryIdx = json.indexOf("\"primary\"", emailIdx);
            if (primaryIdx < 0) break;

            // Check if primary is true within the same object (before next })
            int objEnd = json.indexOf('}', emailIdx);
            if (objEnd < 0) break;

            String segment = json.substring(emailIdx, objEnd);
            if (segment.contains("\"primary\":true") || segment.contains("\"primary\": true")) {
                String email = extractJsonValue(segment, "email");
                if (email != null && !email.isEmpty()) return email;
            }
            idx = objEnd + 1;
        }
        return null;
    }
}
