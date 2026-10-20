package git.artdeell.mojo;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.system.Os;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.AccountSpinner;
import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.extra.ExtraListener;
import git.artdeell.mojo.fragments.AccountManagerFragment;
import git.artdeell.mojo.fragments.AboutFragment;
import git.artdeell.mojo.fragments.BTAInstallFragment;
import git.artdeell.mojo.fragments.DownloadsFragment;
import git.artdeell.mojo.fragments.ElyByLoginFragment;
import git.artdeell.mojo.fragments.FabricInstallFragment;
import git.artdeell.mojo.fragments.FileSelectorFragment;
import git.artdeell.mojo.fragments.ForgeInstallFragment;
import git.artdeell.mojo.fragments.GamepadMapperFragment;
import git.artdeell.mojo.fragments.HomeFragment;
import git.artdeell.mojo.fragments.InstanceEditorFragment;
import git.artdeell.mojo.fragments.InstancesFragment;
import git.artdeell.mojo.fragments.LegacyFabricInstallFragment;
import git.artdeell.mojo.fragments.LocalLoginFragment;
import git.artdeell.mojo.fragments.MicrosoftLoginFragment;
import git.artdeell.mojo.fragments.NeoforgeInstallFragment;
import git.artdeell.mojo.fragments.OptiFineInstallFragment;
import git.artdeell.mojo.fragments.ProfileTypeSelectFragment;
import git.artdeell.mojo.fragments.QuiltInstallFragment;
import git.artdeell.mojo.fragments.BrowseFragment;
import git.artdeell.mojo.fragments.SelectAuthFragment;
import git.artdeell.mojo.fragments.SettingsFragment;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.InstanceInstaller;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.modding.modpacks.imagecache.IconCacheJanitor;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.prefs.screens.LauncherPreferenceControlFragment;
import git.artdeell.mojo.prefs.screens.LauncherPreferenceExperimentalFragment;
import git.artdeell.mojo.prefs.screens.LauncherPreferenceJavaFragment;
import git.artdeell.mojo.prefs.screens.LauncherPreferenceMiscellaneousFragment;
import git.artdeell.mojo.prefs.screens.LauncherPreferenceVideoFragment;
import git.artdeell.mojo.progresskeeper.ProgressKeeper;
import git.artdeell.mojo.services.ProgressServiceKeeper;
import git.artdeell.mojo.tasks.AsyncVersionList;
import git.artdeell.mojo.tasks.mojson.MoJsonDownloader;
import git.artdeell.mojo.tasks.mojson.MoJsonExtras;
import git.artdeell.mojo.ui.AccountChipView;
import git.artdeell.mojo.ui.NavItemView;
import git.artdeell.mojo.ui.ScreenTitleProvider;
import git.artdeell.mojo.utils.NotificationUtils;
import git.artdeell.mojo.utils.lifecycle.ContextAwareDoneListener;
import git.artdeell.mojo.utils.lifecycle.ContextExecutor;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";
    public static final String FRAGMENT_RESULT_NOTIFICATION_PERMISSION = "notification_permission";

    public static final int TAB_HOME = 0;
    public static final int TAB_INSTANCES = 1;
    public static final int TAB_BROWSE = 2;
    public static final int TAB_DOWNLOADS = 3;
    public static final int TAB_SETTINGS = 4;

    private static final String STATE_CURRENT_TAB = "mojo_current_tab";
    private static final String[] ROOT_TAGS = {
            "root_home", "root_instances", "root_browse", "root_downloads", "root_settings"
    };

    private FragmentContainerView mFragmentView;
    private ImageButton mBackButton;
    private ImageView mMarkView;
    private TextView mTitleView;
    private AccountChipView mAccountChip;
    private LinearLayout mNavRail;
    private LinearLayout mBottomNav;
    private NavItemView[] mNavRailItems;
    private NavItemView[] mBottomNavItems;
    private AccountSpinner mAccountSpinner;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private NotificationManager mNotificationManager;
    private ActivityResultLauncher<String> mRequestPermissionLauncher;

    private int mCurrentTab = TAB_HOME;
    private boolean mIsWide;
    private boolean mBrowseImportRequested;
    private boolean mLaunchInProgress;

    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    updateChrome(f);
                }
            };

    /* Listener for the back button in settings */
    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if(value.equals("true")) onBackPressed();
        return false;
    };

    /* Listener for the auth method selection screen */
    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        FragmentManager manager = getSupportFragmentManager();
        if(!value || manager.isStateSaved()) return false;
        Fragment fragment = manager.findFragmentById(mFragmentView.getId());
        if(!isAccountEntryPoint(fragment)) return false;
        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    /* Keep the visible chip in sync when a login completes in the hidden spinner. */
    private final ExtraListener<Boolean> mAccountChangedListener = (key, value) -> {
        refreshAccountChip();
        return false;
    };

    private boolean isAccountEntryPoint(Fragment fragment) {
        return fragment instanceof HomeFragment
                || fragment instanceof InstancesFragment
                || fragment instanceof DownloadsFragment
                || fragment instanceof SettingsFragment
                || fragment instanceof BrowseFragment
                || fragment instanceof AccountManagerFragment;
    }

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        if(mLaunchInProgress) return false;
        if(mProgressLayout.hasProcesses()) {
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }
        mLaunchInProgress = true;
        MojoApplication.sExecutorService.execute(() -> {
            Instance selectedInstance = Instances.loadSelectedInstance();
            Account currentAccount = Accounts.getCurrent();
            Tools.runOnUiThread(() -> finishGameLaunch(selectedInstance, currentAccount));
        });
        return false;
    };

    private void finishGameLaunch(Instance selectedInstance, Account currentAccount) {
        try {
            if(selectedInstance == null) {
                Toast.makeText(this, R.string.no_instance, Toast.LENGTH_LONG).show();
                return;
            }

            if(selectedInstance.installer != null) {
                selectedInstance.installer.start();
                return;
            }

            if(!Tools.isValidString(selectedInstance.versionId)){
                Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
                return;
            }

            if(currentAccount == null){
                Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
                ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
                return;
            }
            String normalizedVersionId = MoJsonExtras.normalizeVersionId(selectedInstance.versionId);
            JVersionList.Version mcVersion = MoJsonExtras.getListedVersion(normalizedVersionId);
            new MoJsonDownloader().start(
                    getAssets(),
                    mcVersion,
                    normalizedVersionId,
                    new ContextAwareDoneListener(this, normalizedVersionId)
            );
        } finally {
            mLaunchInProgress = false;
        }
    }

    private final git.artdeell.mojo.progresskeeper.TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        if(taskCount > 0) {
            Tools.runOnUiThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
        return false;
    };

    @Override
    protected boolean shouldIgnoreNotch() {
        return getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean setFullscreen() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mojo_ui);
        MoJsonDownloader.prepareSubstitutionMap(getAssets());

        try {
            Os.setenv("TMPDIR", Tools.DIR_CACHE.getAbsolutePath(), true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        IconCacheJanitor.runJanitor();

        getWindow().setBackgroundDrawable(null);
        bindViews();
        setupNavigation();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);

        mRequestPermissionLauncher = this.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    notifyNotificationPermissionChanged();
                    if(isAllowed) {
                        Toast.makeText(this, R.string.notif_cta_granted, Toast.LENGTH_SHORT).show();
                    }else{
                        Toast.makeText(this, R.string.notif_cta_denied, Toast.LENGTH_LONG).show();
                        if(Build.VERSION.SDK_INT >= 33
                                && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                            showNotificationDeniedDialog();
                        }
                    }
                }
        );

        mAccountSpinner.setAutoCreateAccountOnEmpty(false);
        refreshAccountChip();

        if(LauncherPreferences.PREF_MIGRATION_NOTICE)
            MojoApplication.sExecutorService.submit(this::checkPreviousInstalls);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        ProgressKeeper.addTaskCountListener(mProgressLayout);
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.addExtraListener(ExtraConstants.ACCOUNT_CHANGED, mAccountChangedListener);
        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions));

        mProgressLayout.observe(ProgressLayout.DOWNLOAD_GAME);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
        mProgressLayout.observe(ProgressLayout.INSTANCE_INSTALL);
        mProgressLayout.observe(ProgressLayout.DATA_MIGRATION);

        if(savedInstanceState != null) {
            mCurrentTab = savedInstanceState.getInt(STATE_CURRENT_TAB, TAB_HOME);
            updateNavSelection();
        }
        if(savedInstanceState == null || getSupportFragmentManager().findFragmentById(mFragmentView.getId()) == null) {
            selectRootTab(mCurrentTab);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_CURRENT_TAB, mCurrentTab);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        InstanceInstaller.postInstallCheck(this);
        refreshAccountChip();
        updateChromeForCurrentFragment();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        ProgressKeeper.removeTaskCountListener(mDoubleLaunchPreventionListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.ACCOUNT_CHANGED, mAccountChangedListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    /** Custom implementation to feel more natural when a backstack isn't present */
    @Override
    public void onBackPressed() {
        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if(fragment != null){
            if(fragment.canGoBack()){
                fragment.goBack();
                return;
            }
        }
        if(!isCurrentFragmentRoot() && getSupportFragmentManager().getBackStackEntryCount() > 0) {
            super.onBackPressed();
            return;
        }
        if(mCurrentTab != TAB_HOME) {
            selectRootTab(TAB_HOME);
            return;
        }
        super.onBackPressed();
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag){
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    public void askForPermission(int minApi, final String permission) {
        if(Build.VERSION.SDK_INT < minApi) return;
        mRequestPermissionLauncher.launch(permission);
    }

    public boolean checkForPermission(int minApi, final String permission) {
        return Build.VERSION.SDK_INT < minApi ||
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_DENIED;
    }

    public boolean checkForPermissionRationale(int minApi, final String permission) {
        return checkForPermission(minApi, permission) || ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
    }

    public boolean hasNotificationPermission() {
        return checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
    }

    /**
     * Contextual notification request. Never called during startup; screens call it only when
     * the user is looking at download-related content.
     */
    public void requestNotificationPermission() {
        if(hasNotificationPermission()) {
            Toast.makeText(this, R.string.notif_cta_granted, Toast.LENGTH_SHORT).show();
            return;
        }
        if(Build.VERSION.SDK_INT < 33) return;
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.notif_cta_title)
                    .setMessage(R.string.notif_cta_body)
                    .setPositiveButton(R.string.notif_cta_allow, (d, w) ->
                            askForPermission(33, Manifest.permission.POST_NOTIFICATIONS))
                    .setNegativeButton(R.string.notif_cta_not_now, null)
                    .show();
        }else{
            askForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void showNotificationDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notif_permission_denied_title)
                .setMessage(R.string.notif_permission_denied_body)
                .setPositiveButton(R.string.notif_cta_settings, (d, w) -> openNotificationSettings())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    public void openNotificationSettings() {
        try {
            Intent intent;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            }else{
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + getPackageName()));
            }
            startActivity(intent);
        }catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName())));
        }
    }

    private void notifyNotificationPermissionChanged() {
        Bundle result = new Bundle();
        result.putBoolean("granted", hasNotificationPermission());
        getSupportFragmentManager().setFragmentResult(FRAGMENT_RESULT_NOTIFICATION_PERMISSION, result);
    }

    // Call async
    private void checkPreviousInstalls(){
        final String[] packages = {"git.artdeell.mojo", "git.artdeell.mojo.debug", "git.artdeell.mojo.pub"};
        for(String s : packages){
            Intent i = getPackageManager().getLaunchIntentForPackage(s);
            if(i == null) continue;
            Tools.runOnUiThread(() ->
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.migration_progress_warning_title)
                        .setMessage(R.string.migration_notice)
                        .setPositiveButton(android.R.string.ok, (d, button) -> LauncherPreferences.DEFAULT_PREF.edit().putBoolean("migrationNotice", false).apply())
                        .setOnDismissListener(d -> LauncherPreferences.PREF_MIGRATION_NOTICE = false)
                        .show());
            break;
        }
    }

    public void refreshAccountChip() {
        if(mAccountChip == null) return;
        MojoApplication.sExecutorService.execute(() -> {
            Account account = Accounts.getCurrent();
            Tools.runOnUiThread(() -> {
                if(isFinishing() || isDestroyed()) return;
                mAccountChip.setAccount(account);
            });
        });
    }

    public void selectRootTab(int tab) {
        if(tab < TAB_HOME || tab > TAB_SETTINGS) return;
        FragmentManager manager = getSupportFragmentManager();
        if(manager.isStateSaved()) return;

        String targetTag = ROOT_TAGS[tab];
        Fragment current = manager.findFragmentById(mFragmentView.getId());
        if(current != null && targetTag.equals(current.getTag()) && isCurrentFragmentRoot()) {
            mCurrentTab = tab;
            updateNavSelection();
            updateChrome(current);
            return;
        }

        manager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        Fragment root = createRootFragment(tab);
        manager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(mFragmentView.getId(), root, targetTag)
                .commit();
        mCurrentTab = tab;
        updateNavSelection();
        updateChrome(root);
    }

    private Fragment createRootFragment(int tab) {
        switch (tab) {
            case TAB_INSTANCES: return new InstancesFragment();
            case TAB_BROWSE: return new BrowseFragment();
            case TAB_DOWNLOADS: return new DownloadsFragment();
            case TAB_SETTINGS: return new SettingsFragment();
            case TAB_HOME:
            default: return new HomeFragment();
        }
    }

    /**
     * Ask Browse to open its local modpack file picker. Works whether Browse is already visible or
     * has to be created by a root-tab switch.
     */
    public void requestBrowseImport() {
        Fragment browse = getSupportFragmentManager().findFragmentByTag(ROOT_TAGS[TAB_BROWSE]);
        if(mCurrentTab == TAB_BROWSE && browse instanceof BrowseFragment && browse.isVisible()) {
            ((BrowseFragment) browse).beginLocalImport();
            return;
        }
        mBrowseImportRequested = true;
        selectRootTab(TAB_BROWSE);
    }

    public boolean consumeBrowseImportRequest() {
        boolean requested = mBrowseImportRequested;
        mBrowseImportRequested = false;
        return requested;
    }

    public void openAccountManager() {
        Tools.swapFragment(this, AccountManagerFragment.class, AccountManagerFragment.TAG, null);
    }

    public void openAbout() {
        Tools.swapFragment(this, AboutFragment.class, AboutFragment.TAG, null);
    }

    private void setupNavigation() {
        int[] icons = {
                R.drawable.ic_ui_home, R.drawable.ic_ui_instances, R.drawable.ic_ui_browse,
                R.drawable.ic_ui_downloads, R.drawable.ic_ui_settings
        };
        int[] labels = {
                R.string.nav_home, R.string.nav_instances, R.string.nav_browse,
                R.string.nav_downloads, R.string.nav_settings
        };
        for(int i = 0; i < icons.length; i++) {
            final int tab = i;
            mBottomNavItems[i].bind(icons[i], labels[i]);
            mBottomNavItems[i].setOnClickListener(v -> selectRootTab(tab));
            mNavRailItems[i].bind(icons[i], labels[i]);
            mNavRailItems[i].setOnClickListener(v -> selectRootTab(tab));
        }
        mAccountChip.setOnClickListener(v -> openAccountManager());
        mBackButton.setOnClickListener(v -> onBackPressed());
        mProgressLayout.setOnClickListener(v -> {
            if(isCurrentFragmentRoot()) {
                selectRootTab(TAB_DOWNLOADS);
            }else{
                // Do not discard an unsaved editor screen from the progress pill. Let the
                // existing ProgressLayout expand its inline task list instead.
                mProgressLayout.toggleExpanded();
            }
        });
        updateNavSelection();
    }

    private void updateNavSelection() {
        for(int i = 0; i < 5; i++) {
            mBottomNavItems[i].setActive(i == mCurrentTab);
            mNavRailItems[i].setActive(i == mCurrentTab);
        }
    }

    private boolean isCurrentFragmentRoot() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        return isRootFragment(fragment);
    }

    private static boolean isRootFragment(Fragment fragment) {
        if(fragment == null || fragment.getTag() == null) return false;
        for(String tag : ROOT_TAGS) {
            if(tag.equals(fragment.getTag())) return true;
        }
        return false;
    }

    private void updateChromeForCurrentFragment() {
        updateChrome(getSupportFragmentManager().findFragmentById(mFragmentView.getId()));
    }

    private void updateChrome(Fragment fragment) {
        if(fragment == null || mTitleView == null) return;
        boolean root = isRootFragment(fragment);
        mTitleView.setText(getTitleFor(fragment));
        mBackButton.setVisibility(root ? View.GONE : View.VISIBLE);
        mMarkView.setVisibility(root ? View.VISIBLE : View.GONE);
        mNavRail.setVisibility(root && mIsWide ? View.VISIBLE : View.GONE);
        mBottomNav.setVisibility(root && !mIsWide ? View.VISIBLE : View.GONE);
        if(root) {
            int tab = tabForTag(fragment.getTag());
            if(tab >= 0) {
                mCurrentTab = tab;
                updateNavSelection();
            }
        }
    }

    private static int tabForTag(String tag) {
        for(int i = 0; i < ROOT_TAGS.length; i++) {
            if(ROOT_TAGS[i].equals(tag)) return i;
        }
        return -1;
    }

    @StringRes
    private int getTitleFor(Fragment fragment) {
        if(fragment instanceof ScreenTitleProvider) {
            return ((ScreenTitleProvider) fragment).getScreenTitle();
        }
        switch(tabForTag(fragment.getTag())) {
            case TAB_HOME: return R.string.nav_home;
            case TAB_INSTANCES: return R.string.nav_instances;
            case TAB_BROWSE: return R.string.nav_browse;
            case TAB_DOWNLOADS: return R.string.nav_downloads;
            case TAB_SETTINGS: return R.string.nav_settings;
        }
        if(fragment instanceof ProfileTypeSelectFragment) return R.string.create_title;
        if(fragment instanceof InstanceEditorFragment) return R.string.editor_title;
        if(fragment instanceof SelectAuthFragment) return R.string.auth_title;
        if(fragment instanceof LocalLoginFragment) return R.string.local_login_title;
        if(fragment instanceof MicrosoftLoginFragment) return R.string.auth_select_microsoft;
        if(fragment instanceof ElyByLoginFragment) return R.string.auth_select_elyby;
        if(fragment instanceof AccountManagerFragment) return R.string.accounts_title;
        if(fragment instanceof AboutFragment) return R.string.about_title;
        if(fragment instanceof FileSelectorFragment) return R.string.folder_fragment_select;
        if(fragment instanceof GamepadMapperFragment) return R.string.settings_gamepad_title;
        if(fragment instanceof FabricInstallFragment) return R.string.modloader_dl_install_fabric_instance;
        if(fragment instanceof QuiltInstallFragment) return R.string.modloader_dl_install_quilt_instance;
        if(fragment instanceof LegacyFabricInstallFragment) return R.string.modloader_dl_install_legacy_fabric_instance;
        if(fragment instanceof ForgeInstallFragment) return R.string.modloader_dl_install_forge_instance;
        if(fragment instanceof NeoforgeInstallFragment) return R.string.modloader_dl_install_neoforge_instance;
        if(fragment instanceof OptiFineInstallFragment) return R.string.mod_dl_install_optifine;
        if(fragment instanceof BTAInstallFragment) return R.string.create_bta_instance;
        if(fragment instanceof LauncherPreferenceVideoFragment) return R.string.preference_video_title;
        if(fragment instanceof LauncherPreferenceControlFragment) return R.string.preference_control_title;
        if(fragment instanceof LauncherPreferenceJavaFragment) return R.string.preference_java_title;
        if(fragment instanceof LauncherPreferenceMiscellaneousFragment) return R.string.preference_misc_title;
        if(fragment instanceof LauncherPreferenceExperimentalFragment) return R.string.preference_experimental_title;
        return R.string.app_short_name;
    }

    /** Stuff all the view boilerplate here */
    private void bindViews(){
        mFragmentView = findViewById(R.id.container_fragment);
        mBackButton = findViewById(R.id.top_bar_back);
        mMarkView = findViewById(R.id.top_bar_mark);
        mTitleView = findViewById(R.id.top_bar_title);
        mAccountChip = findViewById(R.id.account_chip);
        mNavRail = findViewById(R.id.nav_rail);
        mBottomNav = findViewById(R.id.bottom_nav);
        mAccountSpinner = findViewById(R.id.account_spinner);
        mProgressLayout = findViewById(R.id.progress_layout);

        mNavRailItems = new NavItemView[] {
                findViewById(R.id.nav_rail_home), findViewById(R.id.nav_rail_instances),
                findViewById(R.id.nav_rail_browse), findViewById(R.id.nav_rail_downloads),
                findViewById(R.id.nav_rail_settings)
        };
        mBottomNavItems = new NavItemView[] {
                findViewById(R.id.nav_home), findViewById(R.id.nav_instances),
                findViewById(R.id.nav_browse), findViewById(R.id.nav_downloads),
                findViewById(R.id.nav_settings)
        };
        mIsWide = getResources().getConfiguration().screenWidthDp >= 600;
    }
}
