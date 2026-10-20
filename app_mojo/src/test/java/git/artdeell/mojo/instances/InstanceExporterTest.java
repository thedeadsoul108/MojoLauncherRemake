package git.artdeell.mojo.instances;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class InstanceExporterTest {
    @Test
    public void archivesNestedFilesWithStablePaths() throws Exception {
        File root = Files.createTempDirectory("mojo-export").toFile();
        File mods = new File(root, "mods");
        assertTrue(mods.mkdirs());
        Files.write(new File(root, "mojo_instance.json").toPath(),
                "{\"name\":\"Test\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(mods, "example.jar").toPath(),
                "mod".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        InstanceExporter.zipDirectory(root, output);

        boolean json = false;
        boolean jar = false;
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("mojo_instance.json".equals(entry.getName())) json = true;
                if ("mods/".equals(entry.getName())) assertTrue(entry.isDirectory());
                if ("mods/example.jar".equals(entry.getName())) {
                    jar = true;
                    byte[] bytes = new byte[3];
                    assertEquals(3, zip.read(bytes));
                    assertEquals("mod", new String(bytes, StandardCharsets.UTF_8));
                }
            }
        }
        assertTrue(json);
        assertTrue(jar);
        assertNotNull(output.toByteArray());
    }
}
