package git.artdeell.mojo.content;

import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.utils.FileUtils;

/**
 * Small blocking Modrinth v2 client. All methods must run off the main thread. Responses are
 * cached on disk so a cold Browse screen can render instantly while a refresh runs.
 */
public class ModrinthClient {
    private static final String USER_AGENT = "MojoRemake/" + BuildConfig.VERSION_NAME
            + " (github.com/thedeadsoul108/MojoLauncherRemake)";
    private static final long SEARCH_TTL_MS = 5 * 60 * 1000L;
    private static final long PROJECT_TTL_MS = 60 * 60 * 1000L;
    private static final long VERSION_TTL_MS = 10 * 60 * 1000L;
    private static final int MAX_ATTEMPTS = 3;

    private final File cacheDir;

    public ModrinthClient() {
        cacheDir = new File(Tools.DIR_CACHE, "modrinth");
        FileUtils.ensureDirectorySilently(cacheDir);
    }

    public ModrinthModels.SearchResponse search(String query, String projectType,
                                                String gameVersion, String loader,
                                                String category, String environment,
                                                String index, int limit, int offset,
                                                boolean forceRefresh) throws IOException {
        String url = ModrinthQuery.search(query, projectType, gameVersion, loader, category,
                environment, index, limit, offset);
        String json = get(url, forceRefresh, SEARCH_TTL_MS);
        ModrinthModels.SearchResponse response = Tools.GLOBAL_GSON.fromJson(
                json, ModrinthModels.SearchResponse.class);
        if (response == null) throw new IOException("Empty Modrinth search response");
        if (response.hits == null) response.hits = Collections.emptyList();
        return response;
    }

    public ModrinthModels.Project project(String idOrSlug, boolean forceRefresh)
            throws IOException {
        String json = get(ModrinthQuery.project(idOrSlug), forceRefresh, PROJECT_TTL_MS);
        ModrinthModels.Project project = Tools.GLOBAL_GSON.fromJson(
                json, ModrinthModels.Project.class);
        if (project == null) throw new IOException("Empty Modrinth project response");
        return project;
    }

    /** Resolves a local file hash to the version that published it, or null for unknown files. */
    public ModrinthModels.Version versionFromHash(String sha1) throws IOException {
        if (sha1 == null || sha1.isEmpty()) return null;
        try {
            String json = get(ModrinthQuery.versionFile(sha1), false, VERSION_TTL_MS);
            return Tools.GLOBAL_GSON.fromJson(json, ModrinthModels.Version.class);
        } catch (ApiException e) {
            if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) return null;
            throw e;
        }
    }

    public List<ModrinthModels.Version> versions(String idOrSlug, String gameVersion,
                                                 String loader, boolean forceRefresh)
            throws IOException {
        String json = get(ModrinthQuery.versions(idOrSlug, gameVersion, loader),
                forceRefresh, VERSION_TTL_MS);
        List<ModrinthModels.Version> versions = Tools.GLOBAL_GSON.fromJson(json,
                new TypeToken<List<ModrinthModels.Version>>() {}.getType());
        return versions == null ? Collections.emptyList() : versions;
    }

    private String get(String url, boolean forceRefresh, long ttl) throws IOException {
        File cache = cacheFile(url);
        if (!forceRefresh) {
            String cached = readFresh(cache, ttl);
            if (cached != null) return cached;
        }

        IOException lastFailure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                long delay = lastFailure instanceof RateLimitedException
                        ? ((RateLimitedException) lastFailure).retryAfterMs
                        : Math.min(2000L, 400L * attempt);
                sleep(delay);
            }
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(25000);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    String body = readStream(connection.getInputStream());
                    writeCache(cache, body);
                    return body;
                }
                if (code == 429) {
                    long retryAfter = parseRetryAfter(connection.getHeaderField("Retry-After"));
                    lastFailure = new RateLimitedException(retryAfter);
                    continue;
                }
                if (code >= 500) {
                    lastFailure = new ApiException(code, "Modrinth is unavailable (HTTP " + code + ")");
                    continue;
                }
                throw new ApiException(code, "Modrinth request failed (HTTP " + code + ")");
            } catch (ApiException e) {
                throw e;
            } catch (IOException e) {
                lastFailure = e;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        if (lastFailure != null) throw lastFailure;
        throw new IOException("Modrinth request failed");
    }

    private File cacheFile(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(hash.length * 2);
            for (byte b : hash) name.append(String.format("%02x", b & 0xff));
            return new File(cacheDir, name + ".json");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-1 is always available", e);
        }
    }

    private String readFresh(File file, long ttl) {
        if (!file.isFile()) return null;
        if (System.currentTimeMillis() - file.lastModified() > ttl) return null;
        try (InputStream input = new FileInputStream(file)) {
            return readStream(input);
        } catch (IOException e) {
            return null;
        }
    }

    private void writeCache(File file, String body) {
        File temp = new File(file.getAbsolutePath() + ".tmp");
        try (OutputStream output = new FileOutputStream(temp)) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            temp.delete();
            return;
        }
        if (!temp.renameTo(file)) {
            file.delete();
            temp.renameTo(file);
        }
    }

    private static String readStream(InputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, count);
            }
        }
        return builder.toString();
    }

    private static long parseRetryAfter(String value) {
        if (value == null) return 2000L;
        try {
            long seconds = Long.parseLong(value.trim());
            return Math.min(8000L, Math.max(500L, seconds * 1000L));
        } catch (NumberFormatException ignored) {
            return 2000L;
        }
    }

    private static void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to retry", e);
        }
    }

    public static class RateLimitedException extends IOException {
        public final long retryAfterMs;

        RateLimitedException(long retryAfterMs) {
            super("Modrinth rate limit reached");
            this.retryAfterMs = retryAfterMs;
        }
    }

    public static class ApiException extends IOException {
        public final int statusCode;

        ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
