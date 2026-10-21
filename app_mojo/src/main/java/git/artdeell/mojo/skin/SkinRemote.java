package git.artdeell.mojo.skin;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;

/** Small, dependency-free HTTP helpers for skin and cape previews. */
public final class SkinRemote {
    public static final String USER_AGENT =
            "MojoRemake/" + BuildConfig.VERSION_NAME
                    + " (github.com/thedeadsoul108/MojoLauncherRemake)";
    public static final String ELY_MANAGE_URL = "https://ely.by/skins";

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;
    private static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;

    private SkinRemote() {
    }

    public static String crafatarAvatar(String uuid, int size) {
        return "https://crafatar.com/avatars/" + normalizedUuid(uuid)
                + "?size=" + size + "&overlay";
    }

    public static String crafatarBody(String uuid, int size) {
        return "https://crafatar.com/renders/body/" + normalizedUuid(uuid)
                + "?size=" + size + "&overlay";
    }

    public static String crafatarCape(String uuid) {
        return "https://crafatar.com/capes/" + normalizedUuid(uuid);
    }

    public static String elySkin(String username) {
        return "https://skinsystem.ely.by/skins/"
                + encode(username) + ".png";
    }

    public static String elyCape(String username) {
        return "https://skinsystem.ely.by/cloaks/"
                + encode(username) + ".png";
    }

    public static byte[] download(String url) throws IOException {
        if (url == null || !url.startsWith("https://")) {
            throw new IOException("Only HTTPS image URLs are allowed");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setUseCaches(false);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for " + url);
            }
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count == 0) continue;
                    output.write(buffer, 0, count);
                    if (output.size() > MAX_IMAGE_BYTES) {
                        throw new IOException("Image is too large");
                    }
                }
                return output.toByteArray();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Resolves a Mojang username to a UUID, or accepts a UUID directly. */
    public static String resolveUuid(String input) throws IOException {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IOException("Empty player name");
        if (isUuid(value)) return normalizedUuid(value);
        if (!value.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IOException("Invalid player name");
        }
        String url = "https://api.mojang.com/users/profiles/minecraft/"
                + encode(value);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int code = connection.getResponseCode();
            if (code == 204 || code == 404) {
                throw new IOException("Player not found");
            }
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            String body = Tools.read(connection.getInputStream());
            JSONObject json = new JSONObject(body);
            String id = json.optString("id", "");
            if (id.isEmpty()) throw new IOException("Player not found");
            return normalizedUuid(id);
        } catch (org.json.JSONException e) {
            throw new IOException("Malformed profile response", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static byte[] skinFromPlayer(Account account, String player) throws IOException {
        if (account.authType == AuthType.ELY_BY) {
            String name = player == null ? "" : player.trim();
            if (!name.matches("[A-Za-z0-9_]{1,16}")) {
                throw new IOException("Invalid Ely.by player name");
            }
            return download(elySkin(name));
        }
        String uuid = resolveUuid(player);
        return download("https://crafatar.com/skins/" + uuid);
    }

    public static String normalizedUuid(String value) {
        if (value == null) return "";
        return value.replace("-", "").toLowerCase(Locale.US);
    }

    public static boolean isUuid(String value) {
        return value != null && value.matches(
                "([0-9a-fA-F]{32})|([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                        + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (IOException impossible) {
            return "";
        }
    }
}
