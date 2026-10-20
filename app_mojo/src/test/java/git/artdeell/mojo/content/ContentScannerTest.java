package git.artdeell.mojo.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class ContentScannerTest {
    @Test
    public void scansDisabledFilesAndTogglesThemBack() throws Exception {
        File game = Files.createTempDirectory("mojo-content").toFile();
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        File jar = new File(mods, "example.jar");
        Files.write(jar.toPath(), "mod".getBytes(StandardCharsets.UTF_8));
        File disabled = new File(mods, "off.jar.disabled");
        Files.write(disabled.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(mods, ".hidden").toPath(), new byte[0]);
        Files.write(new File(mods, "partial.part").toPath(), new byte[0]);

        List<ContentEntry> entries = ContentScanner.scan(game);
        assertEquals(2, entries.size());
        ContentEntry enabled = find(entries, "example.jar");
        ContentEntry off = find(entries, "off.jar");
        assertTrue(enabled.enabled);
        assertFalse(off.enabled);

        File renamed = ContentScanner.toggle(enabled);
        assertEquals("example.jar.disabled", renamed.getName());
        assertFalse(renamed.exists() && enabled.file.exists());

        File restored = ContentScanner.toggle(find(ContentScanner.scan(game), "example.jar"));
        assertEquals("example.jar", restored.getName());
        assertTrue(restored.isFile());
    }

    private static ContentEntry find(List<ContentEntry> entries, String name) {
        for (ContentEntry entry : entries) {
            if (name.equals(entry.displayName)) return entry;
        }
        throw new AssertionError("Missing " + name);
    }
}
