package git.artdeell.mojo.content;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Reads instance content folders and toggles files by adding or removing a disabled suffix. */
public final class ContentScanner {
    private ContentScanner() {
    }

    public static List<ContentEntry> scan(File gameDirectory) throws IOException {
        if (gameDirectory == null || !gameDirectory.isDirectory()) {
            throw new IOException("Instance game directory is missing");
        }
        List<ContentEntry> entries = new ArrayList<>();
        for (ContentEntry.Category category : ContentEntry.Category.values()) {
            File directory = new File(gameDirectory, category.directory);
            if (!directory.isDirectory()) continue;
            File[] files = directory.listFiles();
            if (files == null) throw new IOException("Could not list " + directory);
            for (File file : files) {
                if (!file.isFile()) continue;
                String name = file.getName();
                if (name.startsWith(".") || name.endsWith(".part") || name.endsWith(".tmp")) {
                    continue;
                }
                boolean enabled = !name.endsWith(ContentEntry.DISABLED_SUFFIX);
                String displayName = enabled
                        ? name
                        : name.substring(0, name.length() - ContentEntry.DISABLED_SUFFIX.length());
                entries.add(new ContentEntry(file, category, displayName, enabled, file.length()));
            }
        }
        Collections.sort(entries, new Comparator<ContentEntry>() {
            @Override
            public int compare(ContentEntry first, ContentEntry second) {
                int category = first.category.compareTo(second.category);
                return category != 0 ? category
                        : first.displayName.compareToIgnoreCase(second.displayName);
            }
        });
        return entries;
    }

    /**
     * Renames the file to switch loading on or off. The target must not already exist, so a
     * toggle can never silently overwrite a different content file.
     * @return the renamed file
     */
    public static File toggle(ContentEntry entry) throws IOException {
        if (entry == null || entry.file == null) throw new IOException("Content file is missing");
        File source = entry.file;
        if (!source.isFile()) throw new IOException("Content file is no longer available");
        File target;
        if (entry.enabled) {
            target = new File(source.getAbsolutePath() + ContentEntry.DISABLED_SUFFIX);
        } else {
            String path = source.getAbsolutePath();
            if (!path.endsWith(ContentEntry.DISABLED_SUFFIX)) {
                throw new IOException("Disabled file name is invalid");
            }
            target = new File(path.substring(0,
                    path.length() - ContentEntry.DISABLED_SUFFIX.length()));
        }
        if (target.exists()) {
            throw new IOException("A file with the target name already exists");
        }
        if (!source.renameTo(target)) {
            throw new IOException("Could not rename the content file");
        }
        return target;
    }
}
