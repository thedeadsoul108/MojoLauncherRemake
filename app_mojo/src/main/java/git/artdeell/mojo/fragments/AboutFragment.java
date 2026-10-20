package git.artdeell.mojo.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.ui.ScreenTitleProvider;

/**
 * About screen for the remake. Version and package values come from BuildConfig, so CI builds
 * remain self-describing without hard-coded version strings.
 */
public class AboutFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "AboutFragment";

    public AboutFragment() {
        super(R.layout.fragment_about);
    }

    @Override
    public int getScreenTitle() {
        return R.string.about_title;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((TextView) view.findViewById(R.id.about_version))
                .setText(BuildConfig.VERSION_NAME);
        ((TextView) view.findViewById(R.id.about_build))
                .setText(BuildConfig.BUILD_TYPE + " \u00B7 " + BuildConfig.FLAVOR);
        ((TextView) view.findViewById(R.id.about_package))
                .setText(BuildConfig.APPLICATION_ID);

        view.findViewById(R.id.about_source).setOnClickListener(v ->
                Tools.openURL(requireActivity(), Tools.URL_HOME));
        view.findViewById(R.id.about_issues).setOnClickListener(v ->
                Tools.openURL(requireActivity(), Tools.URL_HOME + "/issues"));
        view.findViewById(R.id.about_license_link).setOnClickListener(v ->
                Tools.openURL(requireActivity(), Tools.URL_HOME + "/blob/dev/LICENSE"));
    }
}
