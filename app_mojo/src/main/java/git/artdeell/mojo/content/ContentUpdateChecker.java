package git.artdeell.mojo.content;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import git.artdeell.mojo.instances.Instance;

/**
 * Resolves installed mod files back to Modrinth versions by SHA-1 and compares them with the
 * newest version published for the instance's game version and loader.
 */
public final class ContentUpdateChecker {
    private ContentUpdateChecker() {
    }

    public static final class Update {
        public final ContentEntry entry;
        public final String projectId;
        public final String title;
        public final String installedVersion;
        public final String latestVersion;
        public final String latestVersionId;

        Update(ContentEntry entry, String projectId, String title, String installedVersion,
               String latestVersion, String latestVersionId) {
            this.entry = entry;
            this.projectId = projectId;
            this.title = title;
            this.installedVersion = installedVersion;
            this.latestVersion = latestVersion;
            this.latestVersionId = latestVersionId;
        }
    }

    public static final class Result {
        public final List<Update> updates;
        public final int checked;
        public final int failed;

        Result(List<Update> updates, int checked, int failed) {
            this.updates = Collections.unmodifiableList(updates);
            this.checked = checked;
            this.failed = failed;
        }
    }

    public static Result check(Instance instance, List<ContentEntry> entries, int maxMods)
            throws IOException {
        if (instance == null) throw new IOException("Instance is missing");
        List<Update> updates = new ArrayList<>();
        int checked = 0;
        int failed = 0;
        if (entries == null || maxMods <= 0) return new Result(updates, 0, 0);
        ModrinthClient client = new ModrinthClient();
        String gameVersion = InstanceEnvironment.gameVersionOf(instance);
        String loader = InstanceEnvironment.loaderOf(instance);
        for (ContentEntry entry : entries) {
            if (checked >= maxMods) break;
            if (entry.category != ContentEntry.Category.MODS || !entry.enabled
                    || !entry.displayName.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                continue;
            }
            checked++;
            try {
                String hash = ContentInstaller.sha1(entry.file);
                ModrinthModels.Version current = client.versionFromHash(hash);
                if (current == null || current.projectId == null) continue;
                List<ModrinthModels.Version> versions = client.versions(
                        current.projectId, gameVersion, loader, false);
                ModrinthModels.Version latest = newest(versions, current);
                if (latest == null) continue;
                String title = current.name != null && !current.name.trim().isEmpty()
                        ? current.name.trim() : current.versionNumber;
                updates.add(new Update(entry, current.projectId,
                        title == null ? current.projectId : title,
                        value(current.versionNumber), value(latest.versionNumber), latest.id));
            } catch (IOException | RuntimeException e) {
                failed++;
            }
        }
        return new Result(updates, checked, failed);
    }

    private static ModrinthModels.Version newest(List<ModrinthModels.Version> versions,
                                                 ModrinthModels.Version current) {
        if (versions == null) return null;
        for (ModrinthModels.Version candidate : versions) {
            if (candidate == null || candidate.id == null || candidate.id.equals(current.id)) {
                continue;
            }
            if (ModrinthModels.primaryFile(candidate) == null) continue;
            if (!isNewer(candidate, current)) continue;
            return candidate;
        }
        return null;
    }

    private static boolean isNewer(ModrinthModels.Version candidate, ModrinthModels.Version current) {
        if (candidate.datePublished != null && current.datePublished != null) {
            return candidate.datePublished.compareTo(current.datePublished) > 0;
        }
        return true;
    }

    private static String value(String value) {
        return value == null || value.trim().isEmpty() ? "?" : value;
    }
}
