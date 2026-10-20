package git.artdeell.mojo.content;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure URL/facet builder for the public Modrinth v2 API. Kept free of Android classes so the
 * encoding rules can be unit tested.
 */
public final class ModrinthQuery {
    public static final String BASE = "https://api.modrinth.com/v2";

    public static final String TYPE_MOD = "mod";
    public static final String TYPE_MODPACK = "modpack";
    public static final String TYPE_RESOURCEPACK = "resourcepack";
    public static final String TYPE_SHADER = "shader";
    public static final String TYPE_DATAPACK = "datapack";

    public static final String INDEX_RELEVANCE = "relevance";
    public static final String INDEX_DOWNLOADS = "downloads";
    public static final String INDEX_FOLLOWS = "follows";
    public static final String INDEX_NEWEST = "newest";
    public static final String INDEX_UPDATED = "updated";

    private ModrinthQuery() {}

    public static String search(String query, String projectType, String gameVersion, String loader,
                                String category, String environment, String index,
                                int limit, int offset) {
        StringBuilder url = new StringBuilder(BASE).append("/search");
        List<String> params = new ArrayList<>();
        params.add("query=" + enc(query == null ? "" : query));
        params.add("limit=" + clamp(limit, 1, 100));
        params.add("offset=" + Math.max(0, offset));
        params.add("index=" + enc(normalizeIndex(index)));
        String facets = facets(projectType, gameVersion, loader, category, environment);
        if (facets != null) params.add("facets=" + enc(facets));
        for (String param : params) {
            url.append(url.indexOf("?") < 0 ? '?' : '&').append(param);
        }
        return url.toString();
    }

    public static String project(String idOrSlug) {
        return BASE + "/project/" + enc(idOrSlug);
    }

    public static String versionFile(String sha1) {
        return BASE + "/version_file/" + enc(sha1) + "?algorithm=sha1";
    }

    public static String versions(String idOrSlug, String gameVersion, String loader) {
        StringBuilder url = new StringBuilder(BASE).append("/project/")
                .append(enc(idOrSlug)).append("/version");
        List<String> params = new ArrayList<>();
        if (gameVersion != null && !gameVersion.trim().isEmpty()) {
            params.add("game_versions=" + enc("[\"" + jsonEscape(gameVersion.trim()) + "\"]"));
        }
        if (loader != null && !loader.trim().isEmpty()) {
            params.add("loaders=" + enc("[\"" + jsonEscape(loader.trim()) + "\"]"));
        }
        for (int i = 0; i < params.size(); i++) {
            url.append(i == 0 ? '?' : '&').append(params.get(i));
        }
        return url.toString();
    }

    /**
     * Builds the Modrinth facet array. Each group is an AND filter; values inside a group are OR.
     * We deliberately keep one value per group so a loader and a category do not become an OR.
     */
    public static String facets(String projectType, String gameVersion, String loader,
                                String category, String environment) {
        List<String> groups = new ArrayList<>();
        if (projectType != null && !projectType.trim().isEmpty()) {
            groups.add(group("project_type:" + projectType.trim().toLowerCase(Locale.ROOT)));
        }
        if (gameVersion != null && !gameVersion.trim().isEmpty()) {
            groups.add(group("versions:" + gameVersion.trim()));
        }
        if (loader != null && !loader.trim().isEmpty()) {
            groups.add(group("categories:" + loader.trim().toLowerCase(Locale.ROOT)));
        }
        if (category != null && !category.trim().isEmpty()) {
            groups.add(group("categories:" + category.trim().toLowerCase(Locale.ROOT)));
        }
        if (environment != null) {
            if ("client".equals(environment)) {
                groups.add(group("client_side:required"));
            } else if ("server".equals(environment)) {
                groups.add(group("server_side:required"));
            }
        }
        if (groups.isEmpty()) return null;
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) builder.append(',');
            builder.append(groups.get(i));
        }
        return builder.append(']').toString();
    }

    public static String loaderForVersionId(String versionId) {
        if (versionId == null) return null;
        String id = versionId.toLowerCase(Locale.ROOT);
        if (id.contains("neoforge")) return "neoforge";
        if (id.contains("fabric")) return "fabric";
        if (id.contains("quilt")) return "quilt";
        if (id.contains("forge")) return "forge";
        return null;
    }

    public static String gameVersionForVersionId(String versionId) {
        if (versionId == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<!\\d)(1\\.\\d+(?:\\.\\d+)?)(?!\\d)")
                .matcher(versionId);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String directoryFor(String projectType) {
        if (TYPE_MOD.equals(projectType)) return "mods";
        if (TYPE_RESOURCEPACK.equals(projectType)) return "resourcepacks";
        if (TYPE_SHADER.equals(projectType)) return "shaderpacks";
        if (TYPE_DATAPACK.equals(projectType)) return "datapacks";
        if (TYPE_MODPACK.equals(projectType)) return null;
        return "mods";
    }

    private static String group(String value) {
        return "[\"" + jsonEscape(value) + "\"]";
    }

    private static String normalizeIndex(String index) {
        if (index == null) return INDEX_RELEVANCE;
        switch (index) {
            case INDEX_DOWNLOADS:
            case INDEX_FOLLOWS:
            case INDEX_NEWEST:
            case INDEX_UPDATED:
            case INDEX_RELEVANCE:
                return index;
            default:
                return INDEX_RELEVANCE;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 is always available", e);
        }
    }
}
