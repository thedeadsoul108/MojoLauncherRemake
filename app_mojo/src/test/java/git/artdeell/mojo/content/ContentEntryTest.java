package git.artdeell.mojo.content;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

public class ContentEntryTest {
    @Test
    public void onlyEnabledFilesWithModuleExtensionsAreCheckable() {
        assertTrue(entry("mod.jar", ContentEntry.Category.MODS, true).canCheckForUpdates());
        assertFalse(entry("mod.jar", ContentEntry.Category.MODS, false).canCheckForUpdates());
        assertFalse(entry("mod.zip", ContentEntry.Category.MODS, true).canCheckForUpdates());
        assertTrue(entry("pack.zip", ContentEntry.Category.RESOURCE_PACKS, true)
                .canCheckForUpdates());
        assertTrue(entry("shader.ZIP", ContentEntry.Category.SHADER_PACKS, true)
                .canCheckForUpdates());
    }

    private static ContentEntry entry(String name, ContentEntry.Category category, boolean enabled) {
        String stored = enabled ? name : name + ContentEntry.DISABLED_SUFFIX;
        return new ContentEntry(new File(stored), category, name, enabled, 0L);
    }
}
