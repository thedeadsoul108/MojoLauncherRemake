package git.artdeell.mojo.instances;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import git.artdeell.mojo.R;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class InstanceIconProvider {
    public static final String FALLBACK_ICON_NAME = "default";
    private static final Map<Object, Drawable> sIconCache = new HashMap<>();
    private static final Map<String, Drawable> sStaticIconCache = new HashMap<>();
    private static final Map<String, Integer> sStaticIcons = new HashMap<>();

    static {
        sStaticIcons.put("default", R.drawable.ic_ui_instance_default);
        sStaticIcons.put("fabric", R.drawable.ic_fabric);
        sStaticIcons.put("quilt", R.drawable.ic_quilt);
        sStaticIcons.put("forge", R.drawable.ic_forge);
        sStaticIcons.put("neoforge", R.drawable.ic_neoforge);
    }

    /**
     * Fetch an icon from the cache, or load it if it's not cached.
     *
     * <p>This method is synchronized because instance lists are pre-warmed from a worker thread
     * and then bound on the UI thread. Keep the work small: file icons are already decoded by
     * {@link #prewarm(Resources, List)} before the first bind.</p>
     *
     * @param resources the Resources object, used for creating drawables
     * @param instance the instance
     * @return an icon drawable
     */
    public static synchronized @NonNull Drawable fetchIcon(Resources resources, @NonNull DisplayInstance instance) {
        Object cacheKey = cacheKey(instance);

        Drawable cachedIcon = sIconCache.get(cacheKey);
        if(cachedIcon != null) return cachedIcon;

        Drawable instanceIcon = fetchInstanceFileIcon(resources, cacheKey, instance.getInstanceIconLocation());
        if(instanceIcon != null) return instanceIcon;

        return fetchStaticIcon(resources, cacheKey, instance.icon);
    }

    /**
     * Key icons by instance directory rather than object identity. Instance metadata is re-read
     * from disk on every library refresh, so an identity-keyed cache would retain one drawable
     * per reload.
     */
    private static Object cacheKey(DisplayInstance instance) {
        File root = instance.getInstanceRoot();
        return root != null ? root : Integer.valueOf(System.identityHashCode(instance));
    }

    /**
     * Decode icons for a list off the main thread. The same instance objects must be passed to
     * {@link #fetchIcon(Resources, DisplayInstance)} later so the identity cache can be reused.
     */
    public static void prewarm(Resources resources, List<? extends DisplayInstance> instances) {
        for(DisplayInstance instance : instances) {
            if(instance != null) fetchIcon(resources, instance);
        }
    }

    /**
     * Drop an icon from the icon cache. When dropped, it's Drawable will be re-read from the
     * instance icon file (or re-fetched from the static cache)
     * @param key the instance
     */
    public static synchronized void dropIcon(@NonNull Instance key) {
        sIconCache.remove(cacheKey(key));
    }

    /** Drop a cached icon for an instance directory that is being removed. */
    public static synchronized void dropIcon(@NonNull File instanceRoot) {
        sIconCache.remove(instanceRoot);
    }

    private static Drawable fetchInstanceFileIcon(Resources resources, Object cacheKey, File iconLocation) {
        if(iconLocation == null || !iconLocation.isFile() || !iconLocation.canRead()) return null;
        Bitmap iconBitmap = BitmapFactory.decodeFile(iconLocation.getAbsolutePath());
        if(iconBitmap == null) return null;
        Drawable iconDrawable = new BitmapDrawable(resources, iconBitmap);
        sIconCache.put(cacheKey, iconDrawable);
        return iconDrawable;
    }

    private static Drawable fetchStaticIcon(Resources resources, Object cacheKey, String icon) {
        String staticKey = icon == null ? FALLBACK_ICON_NAME : icon;
        Drawable staticIcon = sStaticIconCache.get(staticKey);
        if(staticIcon == null) {
            staticIcon = getStaticIcon(resources, staticKey);
            if(staticIcon == null) staticIcon = fetchFallbackIcon(resources);
            sStaticIconCache.put(staticKey, staticIcon);
        }
        sIconCache.put(cacheKey, staticIcon);
        return staticIcon;
    }

    private static @NonNull Drawable fetchFallbackIcon(Resources resources) {
        Drawable fallbackIcon = sStaticIconCache.get(FALLBACK_ICON_NAME);
        if(fallbackIcon == null) {
            fallbackIcon = Objects.requireNonNull(getStaticIcon(resources, FALLBACK_ICON_NAME));
            sStaticIconCache.put(FALLBACK_ICON_NAME, fallbackIcon);
        }
        return fallbackIcon;
    }

    private static Drawable getStaticIcon(Resources resources, @NonNull String icon) {
        int staticIconResource = getStaticIconResource(icon);
        if(staticIconResource == -1) return null;
        return ResourcesCompat.getDrawable(resources, staticIconResource, null);
    }

    private static int getStaticIconResource(String icon) {
        Integer iconResource = sStaticIcons.get(icon);
        if(iconResource == null) return -1;
        return iconResource;
    }

    /**
     * Check whether the icon under the specified name is a static icon available in the provider.
     * @param name static icon name to check
     * @return whether the icon is available or not
     */
    public static boolean hasStaticIcon(String name) {
        return name != null && sStaticIcons.containsKey(name);
    }
}
