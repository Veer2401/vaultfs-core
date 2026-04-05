package sync;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import utils.Colors;

/** Pushes state snapshots to Firestore using Google OAuth service-account JWT flow. */
public class FirestoreSync {
    private static final String PROJECT_ID = "vault-fs";
    private static final String SERVICE_ACCOUNT_PATH = "D:\\projects\\personal-projects\\java\\File-System-Manager-Java\\serviceAccount.json";

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
                System.out.println(Colors.c(Colors.RED, "[sync] Auth failed — skipping push"));
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

            HttpURLConnection conn = (HttpURLConnection) new URL(firestoreUrl).openConnection();
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
            if (responseCode != 200) {
                System.out.println(Colors.c(Colors.RED, "[sync] Push failed: " + responseCode));
            }
        } catch (Exception e) {
            System.out.println(Colors.c(Colors.RED, "[sync] Push error: " + e.getMessage()));
        }
    }

    /** Creates a signed JWT and exchanges it for a Google OAuth access token. */
    private static String getAccessToken() {
        try {
            StringBuilder jsonBuilder = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(SERVICE_ACCOUNT_PATH), "UTF-8"))) {
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

            privateKey = privateKey
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
            String signature = signRsa(signingInput, privateKey);
            String jwt = signingInput + "." + signature;

            HttpURLConnection conn = (HttpURLConnection) new URL("https://oauth2.googleapis.com/token").openConnection();
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
