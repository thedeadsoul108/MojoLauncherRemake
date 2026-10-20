package git.artdeell.mojo.ui;

import androidx.appcompat.app.AppCompatDelegate;

import git.artdeell.mojo.R;

/** Maps the persisted appearance preference to AppCompat's day/night mode. */
public final class ThemeManager {
    public static final String SYSTEM = "system";
    public static final String DARK = "dark";
    public static final String LIGHT = "light";

    private ThemeManager() {}

    public static void apply(String mode) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }

    public static int toNightMode(String mode) {
        if (DARK.equals(mode)) return AppCompatDelegate.MODE_NIGHT_YES;
        if (LIGHT.equals(mode)) return AppCompatDelegate.MODE_NIGHT_NO;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    public static int summaryRes(String mode) {
        if (DARK.equals(mode)) return R.string.settings_theme_dark;
        if (LIGHT.equals(mode)) return R.string.settings_theme_light;
        return R.string.settings_theme_system;
    }
}
