package git.artdeell.mojo.fragments;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.InstanceIconProvider;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.progresskeeper.ProgressKeeper;
import git.artdeell.mojo.progresskeeper.TaskCountListener;
import git.artdeell.mojo.ui.ScreenTitleProvider;
import git.artdeell.mojo.ui.TaskCountRelay;
import git.artdeell.mojo.ui.UiUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Default destination of the launcher shell. Shows the selected instance, shortcuts and a
 * compact view of the instance library.
 */
public class HomeFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "HomeFragment";

    private View mSelectedCard;
    private View mEmptyCard;
    private ImageView mInstanceIcon;
    private TextView mInstanceName;
    private TextView mInstanceMeta;
    private ImageView mAccountIcon;
    private TextView mAccountName;
    private TextView mAccountType;
    private TextView mTasksCount;
    private View mDownloadsCard;
    private LinearLayout mRecentContainer;
    private TextView mRecentEmpty;

    private volatile Instance mSelectedInstance;
    private boolean mLoadPending;
    private int mGeneration;
    private final TaskCountRelay mTaskCountRelay = new TaskCountRelay();

    private final TaskCountListener mTaskCountListener = count -> {
        View view = getView();
        if (view == null) return false;
        view.post(() -> {
            // The post can outlive the view (rotation while a download is running).
            if (!isAdded() || getView() == null) return;
            mTaskCountRelay.dispatch(count);
        });
        return false;
    };

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public int getScreenTitle() {
        return R.string.nav_home;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupActions(view);
        mTaskCountRelay.attach(this::updateTaskCount);
        ProgressKeeper.addTaskCountListener(mTaskCountListener);
        updateTaskCount(ProgressKeeper.getTaskCount());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadContent();
    }

    @Override
    public void onDestroyView() {
        mGeneration++;
        mLoadPending = false;
        ProgressKeeper.removeTaskCountListener(mTaskCountListener);
        mTaskCountRelay.detach();
        super.onDestroyView();
    }

    private void bindViews(View view) {
        mSelectedCard = view.findViewById(R.id.home_instance_card);
        mEmptyCard = view.findViewById(R.id.home_empty_card);
        mInstanceIcon = view.findViewById(R.id.home_instance_icon);
        mInstanceName = view.findViewById(R.id.home_instance_name);
        mInstanceMeta = view.findViewById(R.id.home_instance_meta);
        mAccountIcon = view.findViewById(R.id.home_account_icon);
        mAccountName = view.findViewById(R.id.home_account_name);
        mAccountType = view.findViewById(R.id.home_account_type);
        mTasksCount = view.findViewById(R.id.home_tasks_count);
        mDownloadsCard = view.findViewById(R.id.home_downloads_card);
        mRecentContainer = view.findViewById(R.id.home_recent_container);
        mRecentEmpty = view.findViewById(R.id.home_recent_empty);
    }

    private void setupActions(View view) {
        view.findViewById(R.id.home_play).setOnClickListener(v ->
                ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        view.findViewById(R.id.home_configure).setOnClickListener(v -> openEditor());
        view.findViewById(R.id.home_empty_create).setOnClickListener(v -> openCreate());
        view.findViewById(R.id.home_recent_all).setOnClickListener(v -> selectTab(LauncherActivity.TAB_INSTANCES));
        view.findViewById(R.id.home_account_card).setOnClickListener(v ->
                requireLauncherActivity().openAccountManager());
        mDownloadsCard.setOnClickListener(v -> selectTab(LauncherActivity.TAB_DOWNLOADS));

        bindActionTile(view.findViewById(R.id.home_action_create), R.drawable.ic_ui_add,
                R.string.home_quick_create_title, R.string.home_quick_create_body, v -> openCreate());
        bindActionTile(view.findViewById(R.id.home_action_browse), R.drawable.ic_ui_browse,
                R.string.home_quick_browse_title, R.string.home_quick_browse_body,
                v -> selectTab(LauncherActivity.TAB_BROWSE));
        bindActionTile(view.findViewById(R.id.home_action_import), R.drawable.ic_ui_file_download,
                R.string.home_quick_import_title, R.string.home_quick_import_body,
                v -> requireLauncherActivity().requestBrowseImport());
        bindActionTile(view.findViewById(R.id.home_action_settings), R.drawable.ic_ui_settings,
                R.string.home_quick_settings_title, R.string.home_quick_settings_body,
                v -> selectTab(LauncherActivity.TAB_SETTINGS));
    }

    private void bindActionTile(View tile, int icon, @StringRes int title,
                                @StringRes int body, View.OnClickListener listener) {
        ((ImageView) tile.findViewById(R.id.action_tile_icon)).setImageResource(icon);
        ((TextView) tile.findViewById(R.id.action_tile_title)).setText(title);
        ((TextView) tile.findViewById(R.id.action_tile_body)).setText(body);
        tile.setOnClickListener(listener);
        tile.setContentDescription(getString(title) + ". " + getString(body));
    }

    private void openCreate() {
        Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                ProfileTypeSelectFragment.TAG, null);
    }

    private void openEditor() {
        Instance instance = mSelectedInstance;
        if (instance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        Bundle args = new Bundle();
        args.putString(InstanceEditorFragment.ARG_INSTANCE_ROOT,
                instance.getInstanceRoot().getAbsolutePath());
        Tools.swapFragment(requireActivity(), InstanceEditorFragment.class,
                InstanceEditorFragment.TAG, args);
    }

    private void selectTab(int tab) {
        requireLauncherActivity().selectRootTab(tab);
    }

    private LauncherActivity requireLauncherActivity() {
        return (LauncherActivity) requireActivity();
    }

    private void loadContent() {
        if (mLoadPending || getView() == null) return;
        mLoadPending = true;
        final android.content.res.Resources resources = getResources();
        final int generation = mGeneration;
        MojoApplication.sExecutorService.execute(() -> {
            List<Instance> instances;
            File selectedRoot;
            Account account = Accounts.getCurrent();
            Bitmap accountFace = account == null ? null : account.getSkinFace();
            try {
                Instances.ensureValidSelection();
                instances = Instances.loadAllInstances();
                selectedRoot = Instances.getSelectedInstanceRoot();
                InstanceIconProvider.prewarm(resources, instances);
            } catch (IOException e) {
                instances = null;
                selectedRoot = null;
            }
            List<Instance> finalInstances = instances;
            File finalSelectedRoot = selectedRoot;
            Tools.runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                mLoadPending = false;
                applyContent(finalInstances, finalSelectedRoot, account, accountFace);
            });
        });
    }

    private void applyContent(@Nullable List<Instance> instances, @Nullable File selectedRoot,
                              @Nullable Account account, @Nullable Bitmap accountFace) {
        if (instances == null) {
            mSelectedInstance = null;
            mSelectedCard.setVisibility(View.GONE);
            mEmptyCard.setVisibility(View.VISIBLE);
            mRecentEmpty.setVisibility(View.GONE);
            mRecentContainer.removeAllViews();
            bindAccount(account, accountFace);
            return;
        }

        Instance selected = null;
        for (Instance instance : instances) {
            if (selectedRoot != null && selectedRoot.equals(instance.getInstanceRoot())) {
                selected = instance;
                break;
            }
        }
        mSelectedInstance = selected;

        if (selected == null) {
            mSelectedCard.setVisibility(View.GONE);
            mEmptyCard.setVisibility(View.VISIBLE);
        } else {
            mSelectedCard.setVisibility(View.VISIBLE);
            mEmptyCard.setVisibility(View.GONE);
            mInstanceName.setText(UiUtils.displayName(requireContext(), selected));
            mInstanceMeta.setText(UiUtils.describeInstance(requireContext(), selected));
            mInstanceIcon.setImageDrawable(InstanceIconProvider.fetchIcon(getResources(), selected));
            mInstanceIcon.setContentDescription(getString(R.string.instance_icon_description,
                    UiUtils.displayName(requireContext(), selected)));
        }

        bindAccount(account, accountFace);
        bindRecentInstances(instances, selected);
    }

    private void bindAccount(@Nullable Account account, @Nullable Bitmap face) {
        if (account == null) {
            mAccountName.setText(R.string.account_chip_signed_out);
            mAccountType.setText(R.string.accounts_empty_body);
            setAccountPlaceholder(R.drawable.ic_ui_person_add);
        } else {
            mAccountName.setText(account.username);
            mAccountType.setText(authTypeLabel(account.authType));
            if (face != null) {
                mAccountIcon.setImageTintList(null);
                mAccountIcon.setImageBitmap(face);
            } else {
                setAccountPlaceholder(R.drawable.ic_ui_person);
            }
        }
        mAccountIcon.setContentDescription(account == null
                ? getString(R.string.account_chip_add_description)
                : getString(R.string.accounts_avatar_description, account.username));
    }

    private void setAccountPlaceholder(int drawable) {
        mAccountIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary)));
        mAccountIcon.setImageResource(drawable);
    }

    private String authTypeLabel(AuthType type) {
        if (type == AuthType.MICROSOFT) return getString(R.string.auth_select_microsoft);
        if (type == AuthType.ELY_BY) return getString(R.string.auth_select_elyby);
        return getString(R.string.auth_select_local);
    }

    private void bindRecentInstances(List<Instance> instances, @Nullable Instance selected) {
        mRecentContainer.removeAllViews();
        if (instances.isEmpty()) {
            mRecentEmpty.setVisibility(View.VISIBLE);
            return;
        }
        mRecentEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int limit = Math.min(3, instances.size());
        for (int i = 0; i < limit; i++) {
            Instance instance = instances.get(i);
            View row = inflater.inflate(R.layout.view_instance_row, mRecentContainer, false);
            ImageView icon = row.findViewById(R.id.instance_row_icon);
            TextView name = row.findViewById(R.id.instance_row_name);
            TextView meta = row.findViewById(R.id.instance_row_meta);
            TextView badge = row.findViewById(R.id.instance_row_badge);

            String displayName = UiUtils.displayName(requireContext(), instance);
            icon.setImageDrawable(InstanceIconProvider.fetchIcon(getResources(), instance));
            name.setText(displayName);
            meta.setText(UiUtils.describeInstance(requireContext(), instance));
            badge.setVisibility(instance == selected ? View.VISIBLE : View.GONE);
            row.setContentDescription(getString(R.string.instance_card_description, displayName,
                    UiUtils.describeInstance(requireContext(), instance)));
            row.setOnClickListener(v -> selectInstance(instance));
            mRecentContainer.addView(row);
            if (i < limit - 1) {
                View divider = new View(requireContext());
                divider.setBackgroundColor(androidx.core.content.ContextCompat.getColor(
                        requireContext(), R.color.border));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                params.setMarginStart(getResources().getDimensionPixelSize(R.dimen.mojo_instance_icon_small)
                        + getResources().getDimensionPixelSize(R.dimen.mojo_space_3));
                mRecentContainer.addView(divider, params);
            }
        }
    }

    private void selectInstance(@NonNull Instance instance) {
        Instances.setSelectedInstance(instance);
        Toast.makeText(requireContext(),
                getString(R.string.instances_selected_toast, UiUtils.displayName(requireContext(), instance)),
                Toast.LENGTH_SHORT).show();
        requireLauncherActivity().refreshAccountChip();
        loadContent();
    }

    private void updateTaskCount(int count) {
        if (!isAdded() || getView() == null) return;
        mDownloadsCard.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        mTasksCount.setText(getString(R.string.home_tasks_count, count));
    }
}
