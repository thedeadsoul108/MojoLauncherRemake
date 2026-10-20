package git.artdeell.mojo.skin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.impl.MicrosoftBackgroundLogin;

/** Microsoft profile and skin upload calls. Cape changes have no public API. */
public final class MicrosoftSkinClient {
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String SKINS_URL = "https://api.minecraftservices.com/minecraft/profile/skins";
    private static final long EXPIRY_MARGIN_MS = 5 * 60 * 1000L;

    private MicrosoftSkinClient() {
    }

    public static final class Cape {
        public String id;
        public String alias;
        public String textureUrl;
        public boolean active;
    }

    public static final class Profile {
        public String skinUrl;
        public String skinVariant = "classic";
        public final List<Cape> capes = new ArrayList<>();
    }

    public static Profile fetchProfile(Account account) throws Exception {
        return fetchProfile(account, false);
    }

    public static Profile fetchProfile(Account account, boolean forceRefresh) throws Exception {
        String token = ensureToken(account, forceRefresh);
        Response response = get(PROFILE_URL, token);
        if (response.code == 401 && !forceRefresh) {
            token = ensureToken(account, true);
            response = get(PROFILE_URL, token);
        }
        if (response.code < 200 || response.code >= 300) {
            throw new IOException("Microsoft profile HTTP " + response.code);
        }
        return parseProfile(new JSONObject(response.body));
    }

    public static void upload(Account account, byte[] png, String variant) throws Exception {
        String token = ensureToken(account, false);
        Response response = postSkin(token, png, variant);
        if (response.code == 401) {
            token = ensureToken(account, true);
            response = postSkin(token, png, variant);
        }
        if (response.code < 200 || response.code >= 300) {
            throw new IOException("Microsoft skin HTTP " + response.code
                    + (response.body.isEmpty() ? "" : ": " + response.body));
        }
    }

    private static String ensureToken(Account account, boolean force) throws Exception {
        long now = System.currentTimeMillis();
        if (!force && account.accessToken != null && !"0".equals(account.accessToken)
                && account.expiresAt > now + EXPIRY_MARGIN_MS) {
            return account.accessToken;
        }
        return MicrosoftBackgroundLogin.refreshAccessToken(account);
    }

    private static Response get(String url, String token) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("User-Agent", SkinRemote.USER_AGENT);
            connection.setUseCaches(false);
            return read(connection);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Response postSkin(String token, byte[] png, String variant) throws IOException {
        String boundary = "MojoRemake-" + System.nanoTime();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(SKINS_URL).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("User-Agent", SkinRemote.USER_AGENT);
            connection.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            connection.setUseCaches(false);
            try (OutputStream output = connection.getOutputStream()) {
                write(output, "--" + boundary + "\r\n");
                write(output, "Content-Disposition: form-data; name=\"variant\"\r\n\r\n");
                write(output, "slim".equals(variant) ? "slim" : "classic");
                write(output, "\r\n--" + boundary + "\r\n");
                write(output, "Content-Disposition: form-data; name=\"file\"; "
                        + "filename=\"skin.png\"\r\n");
                write(output, "Content-Type: image/png\r\n\r\n");
                output.write(png);
                write(output, "\r\n--" + boundary + "--\r\n");
            }
            return read(connection);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Response read(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        InputStream input = code >= 200 && code < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String body = input == null ? "" : Tools.read(input);
        return new Response(code, body);
    }

    private static Profile parseProfile(JSONObject json) {
        Profile profile = new Profile();
        JSONArray skins = json.optJSONArray("skins");
        if (skins != null && skins.length() > 0) {
            JSONObject chosen = skins.optJSONObject(0);
            for (int i = 0; i < skins.length(); i++) {
                JSONObject candidate = skins.optJSONObject(i);
                if (candidate != null && "ACTIVE".equalsIgnoreCase(
                        candidate.optString("state", ""))) {
                    chosen = candidate;
                    break;
                }
            }
            if (chosen != null) {
                profile.skinUrl = chosen.optString("url", "");
                String variant = chosen.optString("variant", "CLASSIC");
                profile.skinVariant = "SLIM".equalsIgnoreCase(variant) ? "slim" : "classic";
            }
        }
        JSONArray capes = json.optJSONArray("capes");
        if (capes != null) {
            for (int i = 0; i < capes.length(); i++) {
                JSONObject jsonCape = capes.optJSONObject(i);
                if (jsonCape == null) continue;
                Cape cape = new Cape();
                cape.id = jsonCape.optString("id", "");
                cape.alias = jsonCape.optString("alias", "");
                cape.textureUrl = jsonCape.optString("url", "");
                cape.active = "ACTIVE".equalsIgnoreCase(jsonCape.optString("state", ""));
                profile.capes.add(cape);
            }
        }
        return profile;
    }

    private static void write(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }
}
