package auth;

import utils.EnvParser;

/** Reads OAuth client credentials from .env in the project root. */
public class OAuthConfig {

    public static String googleClientId() {
        return EnvParser.get("GOOGLE_CLIENT_ID", "");
    }

    public static String googleClientSecret() {
        return EnvParser.get("GOOGLE_CLIENT_SECRET", "");
    }

    public static String githubClientId() {
        return EnvParser.get("GITHUB_CLIENT_ID", "");
    }

    public static String githubClientSecret() {
        return EnvParser.get("GITHUB_CLIENT_SECRET", "");
    }

    public static boolean isGoogleConfigured() {
        return !googleClientId().isEmpty() && !googleClientSecret().isEmpty();
    }

    public static boolean isGitHubConfigured() {
        return !githubClientId().isEmpty() && !githubClientSecret().isEmpty();
    }
}
