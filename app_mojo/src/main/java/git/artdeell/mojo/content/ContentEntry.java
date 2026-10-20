package git.artdeell.mojo.content;

import java.io.File;

/** One file inside an instance content folder, including its disabled state. */
public final class ContentEntry {
    public static final String DISABLED_SUFFIX = ".disabled";

    public enum Category {
        MODS("mods"),
        RESOURCE_PACKS("resourcepacks"),
        SHADER_PACKS("shaderpacks"),
        DATA_PACKS("datapacks");

        public final String directory;

        Category(String directory) {
            this.directory = directory;
        }
    }

    public final File file;
    public final Category category;
    public final String displayName;
    public final boolean enabled;
    public final long size;

    ContentEntry(File file, Category category, String displayName, boolean enabled, long size) {
        this.file = file;
        this.category = category;
        this.displayName = displayName;
        this.enabled = enabled;
        this.size = size;
    }
}
