package git.artdeell.mojo.instances;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes an instance directory to a portable zip archive without loading it into memory. */
public final class InstanceExporter {
    private InstanceExporter() {
    }

    public static void zipDirectory(File root, OutputStream destination) throws IOException {
        if (root == null || !root.isDirectory()) {
            throw new IOException("Instance directory is missing");
        }
        File canonicalRoot = root.getCanonicalFile();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(destination))) {
            zipDirectory(canonicalRoot, canonicalRoot, zip);
        }
    }

    private static void zipDirectory(File directory, File root, ZipOutputStream zip)
            throws IOException {
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Could not list " + directory);
        List<File> sorted = new ArrayList<>(Arrays.asList(children));
        Collections.sort(sorted, new Comparator<File>() {
            @Override
            public int compare(File first, File second) {
                return first.getName().compareTo(second.getName());
            }
        });
        for (File child : sorted) {
            String relative = relativePath(root, child);
            if (child.isDirectory()) {
                zip.putNextEntry(new ZipEntry(relative + "/"));
                zip.closeEntry();
                zipDirectory(child, root, zip);
            } else if (child.isFile()) {
                zip.putNextEntry(new ZipEntry(relative));
                try (InputStream input = new FileInputStream(child)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (count == 0) continue;
                        zip.write(buffer, 0, count);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static String relativePath(File root, File child) throws IOException {
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        if (!childPath.startsWith(rootPath + File.separator)) {
            throw new IOException("Refusing to archive a path outside the instance");
        }
        return childPath.substring(rootPath.length() + 1).replace(File.separatorChar, '/');
    }
}
