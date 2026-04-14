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
        // Try user.dir first (development), then VAULTFS_HOME / -Dvaultfs.home (global install)
        String userDir = System.getProperty("user.dir");
        String envPath = userDir + "/.env";
        if (!new java.io.File(envPath).exists()) {
            String home = System.getProperty("vaultfs.home");
            if (home == null || home.isEmpty()) {
                home = System.getenv("VAULTFS_HOME");
            }
            if (home != null && !home.isEmpty()) {
                envPath = home + "/.env";
            }
        }
        loadEnvFile(envPath);
    }

    private static void loadEnvFile(String path) {
        // Validate the .env path is within an allowed base directory
        try {
            java.io.File envFile = new java.io.File(path);
            String canonicalEnv = envFile.getCanonicalPath();
            String canonicalBase = new java.io.File(System.getProperty("user.dir")).getCanonicalPath();
            boolean allowed = canonicalEnv.startsWith(canonicalBase);
            if (!allowed) {
                String home = System.getProperty("vaultfs.home");
                if (home == null || home.isEmpty()) home = System.getenv("VAULTFS_HOME");
                if (home != null && !home.isEmpty()) {
                    String canonicalHome = new java.io.File(home).getCanonicalPath();
                    allowed = canonicalEnv.startsWith(canonicalHome);
                }
            }
            if (!allowed) {
                System.err.println("[EnvParser] Warning: .env path outside allowed directories, skipping");
                return;
            }
        } catch (IOException e) {
            // Fall through to normal file load which will handle the error
        }
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
