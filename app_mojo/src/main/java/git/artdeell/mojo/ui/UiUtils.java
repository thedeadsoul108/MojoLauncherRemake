package git.artdeell.mojo.ui;

import android.content.Context;

import git.artdeell.mojo.R;
import git.artdeell.mojo.instances.Instance;

import java.util.Locale;

/**
 * Small presentation helpers shared by the new launcher screens.
 */
public final class UiUtils {
    private UiUtils() {}

    public static String describeInstance(Context context, Instance instance) {
        if(instance == null) return "";
        String version = versionName(context, instance.versionId);
        String loader = loaderName(context, instance.versionId);
        return context.getString(R.string.instances_meta, version, loader);
    }

    public static String versionName(Context context, String versionId) {
        if(versionId == null || versionId.trim().isEmpty()) {
            return context.getString(R.string.common_unknown);
        }
        if(Instance.VERSION_LATEST_RELEASE.equalsIgnoreCase(versionId)) {
            return context.getString(R.string.profiles_latest_release);
        }
        if(Instance.VERSION_LATEST_SNAPSHOT.equalsIgnoreCase(versionId)) {
            return context.getString(R.string.profiles_latest_snapshot);
        }
        return versionId;
    }

    public static String loaderName(Context context, String versionId) {
        if(versionId == null) return context.getString(R.string.instances_loader_vanilla);
        String normalized = versionId.toLowerCase(Locale.US);
        if(normalized.contains("neoforge")) return "NeoForge";
        if(normalized.contains("quilt")) return "Quilt";
        if(normalized.contains("legacyfabric") || normalized.contains("legacy-fabric")) return "Legacy Fabric";
        if(normalized.contains("fabric")) return "Fabric";
        if(normalized.contains("optifine")) return "OptiFine";
        if(normalized.contains("forge")) return "Forge";
        if(normalized.contains("bta") || normalized.contains("betterthanadventure")) return "BTA";
        return context.getString(R.string.instances_loader_vanilla);
    }

    public static String displayName(Context context, Instance instance) {
        if(instance == null) return context.getString(R.string.instances_missing_name);
        String name = instance.getDisplayName();
        if(name.isEmpty()) return context.getString(R.string.instances_missing_name);
        return name;
    }
}
