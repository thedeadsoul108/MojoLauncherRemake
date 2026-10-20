package git.artdeell.mojo.skin;

import android.content.Context;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import git.artdeell.mojo.authenticator.accounts.Account;

/**
 * Device-local skin persistence. This is deliberately independent of the account file: a saved
 * skin can be previewed, exported and re-applied before an upload succeeds, and it survives
 * logging out or switching accounts.
 */
public final class SkinStore {
    private static final Gson GSON = new Gson();
    private static final String DIR_NAME = "skins";

    private SkinStore() {
    }

    public static final class StoredSkin {
        public byte[] png;
        public String variant = "classic";
        public String source = "";
        public long updatedAt;
    }

    private static final class Metadata {
        String variant;
        String source;
        long updatedAt;
    }

    public static StoredSkin load(Context context, Account account) {
        File png = new File(directory(context), key(account) + ".png");
        if (!png.isFile()) return null;
        try {
            byte[] bytes = readFile(png);
            if (bytes.length == 0) return null;
            StoredSkin stored = new StoredSkin();
            stored.png = bytes;
            stored.updatedAt = png.lastModified();
            File metadata = new File(directory(context), key(account) + ".json");
            if (metadata.isFile()) {
                Metadata meta = GSON.fromJson(new String(readFile(metadata), "UTF-8"), Metadata.class);
                if (meta != null) {
                    stored.variant = "slim".equals(meta.variant) ? "slim" : "classic";
                    stored.source = meta.source == null ? "" : meta.source;
                    if (meta.updatedAt > 0) stored.updatedAt = meta.updatedAt;
                }
            }
            return stored;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void save(Context context, Account account, byte[] png, String variant, String source)
            throws IOException {
        File directory = directory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create the skin directory");
        }
        String key = key(account);
        Metadata meta = new Metadata();
        meta.variant = "slim".equals(variant) ? "slim" : "classic";
        meta.source = source == null ? "" : source;
        meta.updatedAt = System.currentTimeMillis();
        writeAtomic(new File(directory, key + ".png"), png);
        writeAtomic(new File(directory, key + ".json"), GSON.toJson(meta).getBytes("UTF-8"));
    }

    public static void clear(Context context, Account account) {
        File directory = directory(context);
        String key = key(account);
        //noinspection ResultOfMethodCallIgnored
        new File(directory, key + ".png").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(directory, key + ".json").delete();
    }

    public static long version(Context context, Account account) {
        File png = new File(directory(context), key(account) + ".png");
        return png.isFile() ? png.lastModified() : 0L;
    }

    public static File directory(Context context) {
        return new File(context.getFilesDir(), DIR_NAME);
    }

    private static String key(Account account) {
        String value = account.profileId;
        if (value == null || value.isEmpty()
                || "00000000-0000-0000-0000-000000000000".equals(value)) {
            value = account.username;
        }
        if (value == null || value.isEmpty()) value = "local";
        StringBuilder safe = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            safe.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
        }
        return safe.toString();
    }

    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                int count = input.read(bytes, read, bytes.length - read);
                if (count < 0) break;
                read += count;
            }
            if (read == bytes.length) return bytes;
            byte[] trimmed = new byte[read];
            System.arraycopy(bytes, 0, trimmed, 0, read);
            return trimmed;
        }
    }

    private static void writeAtomic(File target, byte[] bytes) throws IOException {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(bytes);
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Could not replace " + target);
        }
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Could not write " + target);
        }
    }
}
