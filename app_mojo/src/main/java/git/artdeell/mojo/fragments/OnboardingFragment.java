package git.artdeell.mojo.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.ui.ScreenTitleProvider;

/** One-time setup shown only when the library and account list are both empty. */
public class OnboardingFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "OnboardingFragment";

    public OnboardingFragment() {
        super(R.layout.fragment_onboarding);
    }

    @Override
    public int getScreenTitle() {
        return R.string.onboarding_title;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.onboarding_create).setOnClickListener(v -> {
            markDone();
            if (getActivity() instanceof LauncherActivity) {
                LauncherActivity activity = (LauncherActivity) getActivity();
                activity.selectRootTab(LauncherActivity.TAB_HOME);
                Tools.swapFragment(activity, ProfileTypeSelectFragment.class,
                        ProfileTypeSelectFragment.TAG, null);
            }
        });
        view.findViewById(R.id.onboarding_sign_in).setOnClickListener(v -> {
            markDone();
            if (getActivity() instanceof LauncherActivity) {
                LauncherActivity activity = (LauncherActivity) getActivity();
                activity.selectRootTab(LauncherActivity.TAB_HOME);
                activity.openAccountManager();
            }
        });
        view.findViewById(R.id.onboarding_skip).setOnClickListener(v -> {
            markDone();
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).selectRootTab(LauncherActivity.TAB_HOME);
            }
        });
    }

    private static void markDone() {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_ONBOARDING_DONE, true)
                .apply();
    }
}
