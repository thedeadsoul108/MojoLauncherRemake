package git.artdeell.mojo.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.CustomControlsActivity;
import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.prefs.screens.LauncherPreferenceControlFragment;
import git.artdeell.mojo.prefs.screens.LauncherPreferenceExperimentalFragment;
import git.artdeell.mojo.prefs.screens.LauncherPreferenceJavaFragment;
import git.artdeell.mojo.prefs.screens.LauncherPreferenceMiscellaneousFragment;
import git.artdeell.mojo.prefs.screens.LauncherPreferenceVideoFragment;
import git.artdeell.mojo.ui.ScreenTitleProvider;
import git.artdeell.mojo.ui.ThemeManager;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;

/**
 * Custom settings hub. The rows are built here instead of inflating the old preference root so
 * the shell keeps the same typography and card treatment as the rest of the remake. Each row
 * still opens the original preference fragment, preserving every existing preference key.
 */
public class SettingsFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "SettingsFragment";

    private TextView mAccountSummary;
    private TextView mNotificationSummary;
    private TextView mThemeSummary;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public int getScreenTitle() {
        return R.string.nav_settings;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        LinearLayout accounts = view.findViewById(R.id.settings_accounts_container);
        LinearLayout gameplay = view.findViewById(R.id.settings_gameplay_container);
        LinearLayout tools = view.findViewById(R.id.settings_tools_container);
        LinearLayout about = view.findViewById(R.id.settings_about_container);
        LinearLayout appearance = view.findViewById(R.id.settings_appearance_container);

        mThemeSummary = addRow(inflater, appearance, R.drawable.ic_ui_appearance,
                R.string.settings_theme_title, R.string.settings_theme_summary,
                v -> showThemeDialog()).summary;
        refreshThemeSummary();

        mAccountSummary = addRow(inflater, accounts, R.drawable.ic_ui_accounts,
                R.string.settings_accounts_title, R.string.settings_accounts_summary,
                v -> ((LauncherActivity) requireActivity()).openAccountManager()).summary;
        mNotificationSummary = addRow(inflater, accounts, R.drawable.ic_ui_notifications,
                R.string.settings_notifications_title, R.string.settings_notifications_allowed,
                v -> onNotificationRowClicked()).summary;

        addRow(inflater, gameplay, R.drawable.ic_px_image,
                R.string.preference_video_title, R.string.preference_video_description,
                v -> open(LauncherPreferenceVideoFragment.class));
        addRow(inflater, gameplay, R.drawable.ic_ui_gamepad,
                R.string.preference_control_title, R.string.preference_control_description,
                v -> open(LauncherPreferenceControlFragment.class));
        addRow(inflater, gameplay, R.drawable.ic_ui_gamepad,
                R.string.settings_gamepad_title, R.string.settings_gamepad_summary,
                v -> Tools.swapFragment(requireActivity(), GamepadMapperFragment.class,
                        GamepadMapperFragment.TAG, null));
        addRow(inflater, gameplay, R.drawable.ic_px_control_size,
                R.string.settings_custom_controls_title, R.string.settings_custom_controls_summary,
                v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        addRow(inflater, gameplay, R.drawable.ic_px_java,
                R.string.preference_java_title, R.string.preference_java_description,
                v -> open(LauncherPreferenceJavaFragment.class));
        addRow(inflater, gameplay, R.drawable.ic_px_alt_sliders,
                R.string.preference_misc_title, R.string.preference_misc_description,
                v -> open(LauncherPreferenceMiscellaneousFragment.class));
        addRow(inflater, gameplay, R.drawable.ic_px_experiment,
                R.string.preference_experimental_title, R.string.preference_experimental_description,
                v -> open(LauncherPreferenceExperimentalFragment.class));
        addRow(inflater, gameplay, R.drawable.ic_px_translate,
                R.string.settings_language_title, R.string.settings_language_summary,
                v -> showLanguageDialog());

        addRow(inflater, tools, R.drawable.ic_ui_folder,
                R.string.settings_open_folder_title, R.string.settings_open_folder_summary,
                v -> openGameFolder());
        addRow(inflater, tools, R.drawable.ic_ui_share,
                R.string.settings_share_logs_title, R.string.settings_share_logs_summary,
                v -> Tools.shareLog(requireContext()));

        addRow(inflater, about, R.drawable.ic_ui_info,
                R.string.settings_about_title, R.string.settings_about_summary,
                v -> ((LauncherActivity) requireActivity()).openAbout());
        addRow(inflater, about, R.drawable.ic_ui_external,
                R.string.settings_github_title, R.string.settings_github_summary,
                v -> Tools.openURL(requireActivity(), Tools.URL_HOME));

        TextView footer = view.findViewById(R.id.settings_version_footer);
        footer.setText(getString(R.string.settings_version_footer,
                BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshThemeSummary();
        refreshAccountSummary();
        refreshNotificationSummary();
    }

    private void open(Class<? extends Fragment> fragmentClass) {
        Tools.swapFragment(requireActivity(), fragmentClass,
                fragmentClass.getName(), null);
    }

    private void onNotificationRowClicked() {
        if (!(getActivity() instanceof LauncherActivity)) return;
        LauncherActivity activity = (LauncherActivity) getActivity();
        if (activity.hasNotificationPermission()) {
            activity.openNotificationSettings();
        } else {
            activity.requestNotificationPermission();
        }
    }

    private void refreshNotificationSummary() {
        if (mNotificationSummary == null || !isAdded()) return;
        boolean granted = getActivity() instanceof LauncherActivity
                && ((LauncherActivity) getActivity()).hasNotificationPermission();
        mNotificationSummary.setText(granted
                ? R.string.settings_notifications_allowed
                : R.string.settings_notifications_off);
    }

    private void refreshThemeSummary() {
        if (mThemeSummary == null || !isAdded()) return;
        mThemeSummary.setText(ThemeManager.summaryRes(LauncherPreferences.PREF_THEME));
    }

    private void showThemeDialog() {
        if (!isAdded()) return;
        final String[] modes = {ThemeManager.SYSTEM, ThemeManager.DARK, ThemeManager.LIGHT};
        final String[] labels = {
                getString(R.string.settings_theme_system),
                getString(R.string.settings_theme_dark),
                getString(R.string.settings_theme_light)
        };
        int selected = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(LauncherPreferences.PREF_THEME)) {
                selected = i;
                break;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_theme_dialog_title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    String mode = modes[which];
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putString(LauncherPreferences.PREF_KEY_THEME, mode).apply();
                    LauncherPreferences.PREF_THEME = mode;
                    ThemeManager.apply(mode);
                    refreshThemeSummary();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void refreshAccountSummary() {
        if (mAccountSummary == null || !isAdded()) return;
        MojoApplication.sExecutorService.execute(() -> {
            Account account = Accounts.getCurrent();
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mAccountSummary == null) return;
                if (account == null || account.username == null || account.username.isEmpty()) {
                    mAccountSummary.setText(R.string.accounts_empty_title);
                } else {
                    mAccountSummary.setText(getString(R.string.settings_account_current,
                            account.username));
                }
            });
        });
    }

    private void showLanguageDialog() {
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        boolean forcedEnglish = preferences.getBoolean("force_english", false);
        String[] options = {
                getString(R.string.settings_language_system),
                getString(R.string.settings_language_english)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_language_title)
                .setSingleChoiceItems(options, forcedEnglish ? 1 : 0, (dialog, which) -> {
                    boolean forceEnglish = which == 1;
                    preferences.edit().putBoolean("force_english", forceEnglish).apply();
                    LauncherPreferences.PREF_FORCE_ENGLISH = forceEnglish;
                    dialog.dismiss();
                    Toast.makeText(requireContext(), R.string.settings_language_toast,
                            Toast.LENGTH_LONG).show();
                    requireActivity().recreate();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void openGameFolder() {
        MojoApplication.sExecutorService.execute(() -> {
            Instance instance = Instances.loadSelectedInstance();
            if (instance == null) {
                Tools.runOnUiThread(() -> {
                    if (isAdded()) Toast.makeText(requireContext(), R.string.settings_no_instance,
                            Toast.LENGTH_LONG).show();
                });
                return;
            }
            File gameDirectory = instance.getGameDirectory();
            boolean ok = FileUtils.ensureDirectorySilently(gameDirectory);
            Tools.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (ok) {
                    Tools.openPath(requireContext(), gameDirectory, false);
                } else {
                    Toast.makeText(requireContext(), R.string.gamedir_open_failed,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private Row addRow(LayoutInflater inflater, LinearLayout container, @DrawableRes int icon,
                       @StringRes int title, @StringRes int summary, View.OnClickListener listener) {
        View row = inflater.inflate(R.layout.view_settings_row, container, false);
        ((ImageView) row.findViewById(R.id.settings_row_icon)).setImageResource(icon);
        TextView titleView = row.findViewById(R.id.settings_row_title);
        TextView summaryView = row.findViewById(R.id.settings_row_summary);
        titleView.setText(title);
        summaryView.setText(summary);
        row.setOnClickListener(listener);
        row.setContentDescription(getString(title) + ". " + getString(summary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int spacing = getResources().getDimensionPixelSize(R.dimen.mojo_space_1);
        params.bottomMargin = spacing;
        container.addView(row, params);
        return new Row(titleView, summaryView);
    }

    private static class Row {
        final TextView title;
        final TextView summary;

        Row(TextView title, TextView summary) {
            this.title = title;
            this.summary = summary;
        }
    }
}
