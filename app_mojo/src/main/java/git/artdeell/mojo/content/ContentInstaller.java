package git.artdeell.mojo.content;

import com.kdt.mcgui.ProgressLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.utils.FileUtils;

/**
 * Downloads a single Modrinth file into the correct instance folder. Files are written to a
 * temporary sibling, SHA-1 verified when the API supplies a hash, then moved into place so a
 * failed download can never leave a half-written JAR or pack behind.
 */
public class ContentInstaller {
    private static final String PROGRESS_PREFIX = "content:";
    private static final String USER_AGENT = "MojoRemake/" + BuildConfig.VERSION_NAME
            + " (github.com/thedeadsoul108/MojoLauncherRemake)";

    public static String progressKey(ModrinthModels.Version version) {
        return PROGRESS_PREFIX + (version == null || version.id == null ? "unknown" : version.id);
    }

    public static boolean isContentProgressKey(String key) {
        return key != null && key.startsWith(PROGRESS_PREFIX);
    }

    /**
     * Installs the primary file of a project version into the matching folder of the instance.
     *
     * @return the installed file, or the already-present file when the hash matched
     */
    public File install(ModrinthModels.Project project, ModrinthModels.Version version,
                        Instance instance) throws IOException {
        String directoryName = ModrinthQuery.directoryFor(project.projectType);
        if (directoryName == null) {
            throw new IOException("This project type needs the modpack importer");
        }
        ModrinthModels.VersionFile file = ModrinthModels.primaryFile(version);
        if (file == null) throw new IOException("This version has no downloadable file");
        String fileName = safeFileName(file.filename);
        File directory = new File(instance.getGameDirectory(), directoryName);
        if (!FileUtils.ensureDirectorySilently(directory)) {
            throw new IOException("Could not create the " + directoryName + " folder");
        }
        File target = new File(directory, fileName);
        if (target.isFile() && sha1Matches(target, file.hashes == null ? null : file.hashes.sha1)) {
            return target;
        }
        download(file, target, progressKey(version));
        return target;
    }

    /** Downloads a modpack file into the cache so the existing mrpack importer can take over. */
    public File downloadModpack(ModrinthModels.Project project, ModrinthModels.Version version)
            throws IOException {
        ModrinthModels.VersionFile file = ModrinthModels.primaryFile(version);
        if (file == null) throw new IOException("This version has no downloadable file");
        String fileName = safeFileName(file.filename);
        File directory = new File(git.artdeell.mojo.Tools.DIR_CACHE, "modrinth_packs");
        if (!FileUtils.ensureDirectorySilently(directory)) {
            throw new IOException("Could not create the modpack cache folder");
        }
        File target = new File(directory, project.cacheTag() + "-" + fileName);
        if (target.isFile() && sha1Matches(target, file.hashes == null ? null : file.hashes.sha1)) {
            return target;
        }
        download(file, target, progressKey(version));
        return target;
    }

    public static String sha1(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-1 is always available", e);
        }
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(40);
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return hex.toString();
    }

    public static boolean sha1Matches(File file, String expected) {
        if (expected == null || expected.isEmpty()) return false;
        try {
            return expected.equalsIgnoreCase(sha1(file));
        } catch (IOException e) {
            return false;
        }
    }

    private void download(ModrinthModels.VersionFile file, File target, String progressKey)
            throws IOException {
        File temporary = new File(target.getAbsolutePath() + ".part");
        temporary.delete();
        ProgressLayout.setProgress(progressKey, 0);
        HttpURLConnection connection = null;
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-1 is always available", e);
        }
        try {
            connection = (HttpURLConnection) new URL(file.url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Download failed (HTTP " + code + ")");
            }
            long total = file.size > 0 ? file.size : connection.getContentLength();
            long downloaded = 0;
            int lastPercent = -1;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    downloaded += read;
                    int percent = total > 0
                            ? (int) Math.min(99, downloaded * 100 / total)
                            : -1;
                    if (percent != lastPercent && percent >= 0) {
                        lastPercent = percent;
                        ProgressLayout.setProgress(progressKey, percent);
                    }
                }
                output.flush();
            }
            String actualSha1 = hex(digest.digest());
            String expected = file.hashes == null ? null : file.hashes.sha1;
            if (expected != null && !expected.isEmpty()
                    && !expected.equalsIgnoreCase(actualSha1)) {
                throw new IOException("The downloaded file is corrupted (SHA-1 mismatch)");
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("Could not replace the old file");
            }
            if (!temporary.renameTo(target)) {
                throw new IOException("Could not move the downloaded file into place");
            }
        } finally {
            if (temporary.exists()) temporary.delete();
            if (connection != null) connection.disconnect();
            ProgressLayout.clearProgress(progressKey);
        }
    }

    private static String safeFileName(String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IOException("The project file has no name");
        }
        String result = new File(fileName).getName();
        if (result.isEmpty() || result.equals(".") || result.equals("..")) {
            throw new IOException("The project file name is not safe");
        }
        return result;
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return hex.toString();
    }
}
