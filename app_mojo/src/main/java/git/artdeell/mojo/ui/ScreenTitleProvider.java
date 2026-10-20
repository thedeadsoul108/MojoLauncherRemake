package git.artdeell.mojo.ui;

import androidx.annotation.StringRes;

/**
 * Implemented by fragments that know their own top-app-bar title.
 */
public interface ScreenTitleProvider {
    @StringRes
    int getScreenTitle();
}
