package sync;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import utils.Logger;

/** Pushes state snapshots to Firestore using Google OAuth service-account JWT flow. */
public class FirestoreSync {
    private static final String PROJECT_ID = resolveProjectId();

    private static String resolveProjectId() {
        String id = utils.EnvParser.get("FIREBASE_PROJECT_ID", "");
        return (id != null && !id.isEmpty()) ? id : "vault-fs";
    }

    /**
     * Resolves the service account JSON path from, in order:
     * 1. GOOGLE_APPLICATION_CREDENTIALS environment variable
     * 2. .env GOOGLE_APPLICATION_CREDENTIALS key
     * Returns null if neither is set.
     */
    private static String getServiceAccountPath() {
        String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (path == null || path.isEmpty()) {
            path = utils.EnvParser.get("GOOGLE_APPLICATION_CREDENTIALS", "");
        }
        if (path == null || path.isEmpty()) {
            return null;
        }
        return path;
    }

    /** Pushes the latest serialized state document to the user/device Firestore location. */
    public static void push(String userEmail, String deviceId, String stateJson) {
        try {
            String user = URLEncoder.encode(userEmail, "UTF-8");
            String device = URLEncoder.encode(deviceId, "UTF-8");
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/"
                    + PROJECT_ID
                    + "/databases/(default)/documents/users/"
                    + user
                    + "/devices/"
                    + device;

            String accessToken = getAccessToken();
            if (accessToken == null) {
                Logger.debug("[sync] Skipping push - no valid credentials");
                return;
            }

            StringBuilder body = new StringBuilder();
            body.append("{");
            body.append("\"fields\":{");
            body.append("\"state\":{\"stringValue\":\"").append(escapeJson(stateJson)).append("\"},");
            body.append("\"deviceName\":{\"stringValue\":\"").append(escapeJson(getOsName())).append("\"},");
            body.append("\"lastActive\":{\"stringValue\":\"").append(escapeJson(getCurrentTimestamp())).append("\"}");
            body.append("}");
            body.append("}");

            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) URI.create(firestoreUrl).toURL().openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.toString().getBytes("UTF-8"));
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode >= 200 && responseCode < 300) {
                        break;
                    } else if (responseCode >= 500) {
                        throw new java.io.IOException("Server error: " + responseCode);
                    } else {
                        Logger.warn("[sync] Push failed with status " + responseCode);
                        break;
                    }
                } catch (Exception e) {
                    if (attempt == maxRetries) {
                        Logger.warn("[sync] Push failed after " + maxRetries + " attempts");
                    } else {
                        try {
                            Thread.sleep((long) Math.pow(2, attempt) * 1000); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            Logger.warn("[sync] Push interrupted");
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.warn("[sync] Push skipped: " + e.getClass().getSimpleName());
        }
    }

    /** Creates a signed JWT and exchanges it for a Google OAuth access token. */
    private static String getAccessToken() {
        try {
            String serviceAccountPath = getServiceAccountPath();
            if (serviceAccountPath == null) {
                return null;
            }
            java.io.File saFile = new java.io.File(serviceAccountPath);
            if (!saFile.exists()) {
                Logger.debug("[sync] Service account file not found");
                return null;
            }

            StringBuilder jsonBuilder = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(saFile), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    jsonBuilder.append(line);
                }
            }

            String json = jsonBuilder.toString();
            String clientEmail = extractJsonField(json, "client_email");
            String privateKey = extractJsonField(json, "private_key");
            if (clientEmail == null || privateKey == null) {
                return null;
            }

            // Best-effort zeroing: char array can be wiped, but intermediate String
            // instances (strippedKey, jwt) remain in the JVM string pool until GC.
            // For stronger guarantees, consider an HSM or vault-based signer.
            char[] privateKeyChars = privateKey.toCharArray();
            privateKey = null; // Release string reference

            try {
                String strippedKey = new String(privateKeyChars)
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\\n", "")
                        .replace("\n", "")
                        .trim();

                long now = System.currentTimeMillis() / 1000;
                long exp = now + 3600;

                String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
                String payloadJson = "{"
                        + "\"iss\":\"" + escapeJson(clientEmail) + "\"," 
                        + "\"scope\":\"https://www.googleapis.com/auth/datastore\"," 
                        + "\"aud\":\"https://oauth2.googleapis.com/token\"," 
                        + "\"exp\":" + exp + ","
                        + "\"iat\":" + now
                        + "}";

                String encodedHeader = base64url(headerJson.getBytes("UTF-8"));
                String encodedPayload = base64url(payloadJson.getBytes("UTF-8"));
                String signingInput = encodedHeader + "." + encodedPayload;
                String signature = signRsa(signingInput, strippedKey);
                String jwt = signingInput + "." + signature;

                HttpURLConnection conn = (HttpURLConnection) URI.create("https://oauth2.googleapis.com/token").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String form = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion="
                        + URLEncoder.encode(jwt, "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(form.getBytes("UTF-8"));
                }

                InputStream responseStream;
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    responseStream = conn.getInputStream();
                } else {
                    responseStream = conn.getErrorStream();
                }

                if (responseStream == null) {
                    return null;
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(responseStream, "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }

                String responseBody = response.toString();
                return extractJsonField(responseBody, "access_token");
            } finally {
                // Zero out private key material
                java.util.Arrays.fill(privateKeyChars, '\0');
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Signs input data with RSA SHA-256 using the provided service-account private key. */
    private static String signRsa(String data, String pemKey) throws Exception {
        String stripped = pemKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .replace("\n", "")
                .trim();

        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(spec);

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes("UTF-8"));
        byte[] signed = signature.sign();
        return base64url(signed);
    }

    /** Encodes bytes in URL-safe base64 format without padding. */
    private static String base64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** Extracts a JSON string field value while handling escaped characters in that value. */
    private static String extractJsonField(String json, String field) {
        if (json == null || field == null) {
            return null;
        }

        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) {
            return null;
        }

        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) {
            return null;
        }

        int i = colonIdx + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }

        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }

        i++;
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (escaping) {
                if (c == 'n') {
                    out.append('\n');
                } else if (c == 'r') {
                    out.append('\r');
                } else if (c == 't') {
                    out.append('\t');
                } else {
                    out.append(c);
                }
                escaping = false;
            } else {
                if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    return out.toString();
                } else {
                    out.append(c);
                }
            }
            i++;
        }

        return null;
    }

    /** Escapes JSON-unsafe characters inside string values. */
    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /** Returns the current local timestamp in yyyy-MM-dd HH:mm:ss format. */
    private static String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** Returns the current operating system name. */
    private static String getOsName() {
        return System.getProperty("os.name");
    }
}
