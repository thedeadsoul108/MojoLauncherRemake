package git.artdeell.mojo.content;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;

/** Derives the Modrinth facets that fit the currently selected instance. */
public final class InstanceEnvironment {
    private InstanceEnvironment() {}

    public static String loaderOf(Instance instance) {
        return instance == null ? null : ModrinthQuery.loaderForVersionId(instance.versionId);
    }

    /**
     * The version id is not always a reliable source (new NeoForge builds, aliases such as
     * latest_release), so read the installed version JSON's inheritsFrom when it exists.
     */
    public static String gameVersionOf(Instance instance) {
        if (instance == null || instance.versionId == null) return null;
        String fromId = ModrinthQuery.gameVersionForVersionId(instance.versionId);
        File versionJson = new File(new File(instance.getGameDirectory(), "versions"),
                instance.versionId + "/" + instance.versionId + ".json");
        if (!versionJson.isFile()) return fromId;
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(versionJson), StandardCharsets.UTF_8)) {
            JsonObject json = Tools.GLOBAL_GSON.fromJson(reader, JsonObject.class);
            if (json != null && json.has("inheritsFrom")) {
                String inherited = json.get("inheritsFrom").getAsString();
                String version = ModrinthQuery.gameVersionForVersionId(inherited);
                if (version != null) return version;
            }
            if (json != null && json.has("id")) {
                String version = ModrinthQuery.gameVersionForVersionId(json.get("id").getAsString());
                if (version != null) return version;
            }
        } catch (IOException | RuntimeException ignored) {
            // Fall back to the id-derived version.
        }
        return fromId;
    }

    /** Modpacks create a new instance, so they are not filtered by the selected one. */
    public static boolean usesInstanceFilters(String projectType) {
        return ModrinthQuery.TYPE_MOD.equals(projectType)
                || ModrinthQuery.TYPE_DATAPACK.equals(projectType)
                || ModrinthQuery.TYPE_RESOURCEPACK.equals(projectType)
                || ModrinthQuery.TYPE_SHADER.equals(projectType);
    }

    public static boolean usesLoaderFilter(String projectType) {
        return ModrinthQuery.TYPE_MOD.equals(projectType);
    }
}
