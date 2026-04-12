package utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Simple parser for .env files. */
public class EnvParser {
    private static final Map<String, String> envMap = new HashMap<>();

    static {
        loadEnvFile(System.getProperty("user.dir") + "/.env");
    }

    private static void loadEnvFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();
                    // Remove surrounding quotes if present
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    envMap.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("[EnvParser] Warning: Failed to load .env file - " + e.getMessage());
        }
    }

    /** Returns the value of the environment variable, or null if not found. */
    public static String get(String key) {
        // Prefer actual system environment variables if set, otherwise fallback to .env map
        String sysEnv = System.getenv(key);
        if (sysEnv != null && !sysEnv.isEmpty()) {
            return sysEnv;
        }
        return envMap.get(key);
    }

    /** Returns the value of the environment variable, or defaultValue if not found. */
    public static String get(String key, String defaultValue) {
        String val = get(key);
        return val != null ? val : defaultValue;
    }
}
