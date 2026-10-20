package git.artdeell.mojo.fragments;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.InstanceExporter;
import git.artdeell.mojo.instances.InstanceIconProvider;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.ui.ScreenTitleProvider;
import git.artdeell.mojo.ui.UiUtils;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Instance library. All disk access (enumeration, metadata, icon decoding, timestamps) happens on
 * the shared executor; only filtering, sorting and binding run on the UI thread.
 */
public class InstancesFragment extends Fragment implements ScreenTitleProvider, InstanceCardAdapter.Listener {
    public static final String TAG = "InstancesFragment";

    private static final int SORT_NAME = 1;
    private static final int SORT_RECENT = 2;
    private static final int SORT_VERSION = 3;
    private static final String STATE_PENDING_EXPORT = "pending_export";

    private final List<Instance> mAllInstances = new ArrayList<>();
    private final Map<File, Long> mLastModified = new HashMap<>();
    private final List<Instance> mVisibleInstances = new ArrayList<>();

    private RecyclerView mList;
    private InstanceCardAdapter mAdapter;
    private View mEmptyState;
    private View mErrorState;
    private TextView mEmptyTitle;
    private TextView mEmptyBody;
    private TextView mCount;
    private View mEmptyCreate;
    private View mEmptyImport;
    private EditText mSearch;
    private File mSelectedRoot;
    private int mSortMode = SORT_NAME;
    private boolean mLoadPending;
    private String mPendingExportPath;
    private String mPendingExportName;

    private final ActivityResultLauncher<String> mExportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            this::onExportDestination);

    public InstancesFragment() {
        super(R.layout.fragment_instances);
    }

    @Override
    public int getScreenTitle() {
        return R.string.nav_instances;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mList = view.findViewById(R.id.instances_list);
        mEmptyState = view.findViewById(R.id.instances_empty);
        mErrorState = view.findViewById(R.id.instances_error);
        mEmptyTitle = view.findViewById(R.id.instances_empty_title);
        mEmptyBody = view.findViewById(R.id.instances_empty_body);
        mEmptyCreate = view.findViewById(R.id.instances_empty_create);
        mEmptyImport = view.findViewById(R.id.instances_empty_import);
        mCount = view.findViewById(R.id.instances_count);
        mSearch = view.findViewById(R.id.instances_search);
        if (savedInstanceState != null) {
            mPendingExportPath = savedInstanceState.getString(STATE_PENDING_EXPORT);
            mPendingExportName = savedInstanceState.getString(STATE_PENDING_EXPORT + "_name");
        }
        ImageButton sortButton = view.findViewById(R.id.instances_sort);
        Button create = view.findViewById(R.id.instances_create);
        Button importButton = view.findViewById(R.id.instances_import);

        mAdapter = new InstanceCardAdapter(getResources(), this);
        mList.setLayoutManager(new LinearLayoutManager(requireContext()));
        mList.setAdapter(mAdapter);

        View.OnClickListener createListener = v -> openCreate();
        create.setOnClickListener(createListener);
        mEmptyCreate.setOnClickListener(createListener);
        View.OnClickListener importListener = v -> requireLauncherActivity().requestBrowseImport();
        importButton.setOnClickListener(importListener);
        mEmptyImport.setOnClickListener(importListener);
        sortButton.setOnClickListener(this::showSortMenu);
        view.findViewById(R.id.instances_retry).setOnClickListener(v -> loadInstances());

        mSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                mSearch.clearFocus();
                return true;
            }
            return false;
        });
        mSearch.addTextChangedListener(new SimpleTextWatcher(text -> {
            applyFilterAndSort();
        }));

        getParentFragmentManager().setFragmentResultListener(
                DeleteConfirmDialogFragment.RESULT_KEY, this,
                (requestKey, result) -> loadInstances());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadInstances();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_EXPORT, mPendingExportPath);
        outState.putString(STATE_PENDING_EXPORT + "_name", mPendingExportName);
    }

    @Override
    public void onDestroyView() {
        if (mList != null) mList.setAdapter(null);
        super.onDestroyView();
    }

    private LauncherActivity requireLauncherActivity() {
        return (LauncherActivity) requireActivity();
    }

    private void openCreate() {
        Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                ProfileTypeSelectFragment.TAG, null);
    }

    private void loadInstances() {
        if (mLoadPending) return;
        mLoadPending = true;
        mErrorState.setVisibility(View.GONE);
        if (mAllInstances.isEmpty()) {
            mEmptyState.setVisibility(View.GONE);
            mCount.setText(R.string.instances_loading);
        }
        final android.content.res.Resources resources = getResources();
        MojoApplication.sExecutorService.execute(() -> {
            List<Instance> instances = null;
            File selectedRoot = null;
            Map<File, Long> modified = new HashMap<>();
            try {
                Instances.ensureValidSelection();
                instances = Instances.loadAllInstances();
                selectedRoot = Instances.getSelectedInstanceRoot();
                for (Instance instance : instances) {
                    File root = instance.getInstanceRoot();
                    modified.put(root, root == null ? 0L : root.lastModified());
                }
                InstanceIconProvider.prewarm(resources, instances);
            } catch (IOException e) {
                // Reported through the error state below.
            }
            List<Instance> finalInstances = instances;
            File finalSelectedRoot = selectedRoot;
            Tools.runOnUiThread(() -> {
                mLoadPending = false;
                if (!isAdded() || getView() == null) return;
                if (finalInstances == null) {
                    showErrorState();
                    return;
                }
                mAllInstances.clear();
                mAllInstances.addAll(finalInstances);
                mLastModified.clear();
                mLastModified.putAll(modified);
                mSelectedRoot = finalSelectedRoot;
                mErrorState.setVisibility(View.GONE);
                applyFilterAndSort();
            });
        });
    }

    private void showErrorState() {
        mList.setVisibility(View.GONE);
        mEmptyState.setVisibility(View.GONE);
        mErrorState.setVisibility(View.VISIBLE);
        mCount.setText("");
    }

    private void applyFilterAndSort() {
        String query = mSearch.getText().toString().trim().toLowerCase(Locale.US);
        mVisibleInstances.clear();
        for (Instance instance : mAllInstances) {
            String name = UiUtils.displayName(requireContext(), instance).toLowerCase(Locale.US);
            String version = instance.versionId == null ? "" : instance.versionId.toLowerCase(Locale.US);
            if (query.isEmpty() || name.contains(query) || version.contains(query)) {
                mVisibleInstances.add(instance);
            }
        }
        Collections.sort(mVisibleInstances, getComparator());

        int total = mAllInstances.size();
        mCount.setText(total == 1
                ? getString(R.string.instances_count_one)
                : getString(R.string.instances_count, total));

        if (total == 0) {
            showEmptyState(R.string.instances_empty_title, R.string.instances_empty_body, true);
        } else if (mVisibleInstances.isEmpty()) {
            showEmptyState(R.string.instances_no_results_title, R.string.instances_no_results_body, false);
        } else {
            mList.setVisibility(View.VISIBLE);
            mEmptyState.setVisibility(View.GONE);
            mErrorState.setVisibility(View.GONE);
        }
        mAdapter.setInstances(mVisibleInstances, mSelectedRoot);
    }

    private void showEmptyState(int title, int body, boolean libraryEmpty) {
        mList.setVisibility(View.GONE);
        mEmptyState.setVisibility(View.VISIBLE);
        mEmptyTitle.setText(title);
        mEmptyBody.setText(body);
        mEmptyCreate.setVisibility(libraryEmpty ? View.VISIBLE : View.GONE);
        mEmptyImport.setVisibility(libraryEmpty ? View.VISIBLE : View.GONE);
    }

    private Comparator<Instance> getComparator() {
        switch (mSortMode) {
            case SORT_RECENT:
                return (a, b) -> {
                    Long aTime = mLastModified.get(a.getInstanceRoot());
                    Long bTime = mLastModified.get(b.getInstanceRoot());
                    long left = aTime == null ? 0L : aTime;
                    long right = bTime == null ? 0L : bTime;
                    return Long.compare(right, left);
                };
            case SORT_VERSION:
                return (a, b) -> safe(a.versionId).compareToIgnoreCase(safe(b.versionId));
            case SORT_NAME:
            default:
                return (a, b) -> UiUtils.displayName(requireContext(), a)
                        .compareToIgnoreCase(UiUtils.displayName(requireContext(), b));
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, SORT_NAME, 0, R.string.instances_sort_name)
                .setCheckable(true).setChecked(mSortMode == SORT_NAME);
        menu.getMenu().add(0, SORT_RECENT, 1, R.string.instances_sort_recent)
                .setCheckable(true).setChecked(mSortMode == SORT_RECENT);
        menu.getMenu().add(0, SORT_VERSION, 2, R.string.instances_sort_version)
                .setCheckable(true).setChecked(mSortMode == SORT_VERSION);
        menu.getMenu().setGroupCheckable(0, true, true);
        menu.setOnMenuItemClickListener(item -> {
            mSortMode = item.getItemId();
            applyFilterAndSort();
            return true;
        });
        menu.show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mSearch.getWindowToken(), 0);
    }

    @Override
    public void onSelect(@NonNull Instance instance) {
        Instances.setSelectedInstance(instance);
        mSelectedRoot = instance.getInstanceRoot();
        mAdapter.setSelectedRoot(mSelectedRoot);
        Toast.makeText(requireContext(), getString(R.string.instances_selected_toast,
                UiUtils.displayName(requireContext(), instance)), Toast.LENGTH_SHORT).show();
        requireLauncherActivity().refreshAccountChip();
    }

    @Override
    public void onEdit(@NonNull Instance instance) {
        Instances.setSelectedInstance(instance);
        mSelectedRoot = instance.getInstanceRoot();
        mAdapter.setSelectedRoot(mSelectedRoot);
        Bundle args = new Bundle();
        args.putString(InstanceEditorFragment.ARG_INSTANCE_ROOT,
                instance.getInstanceRoot().getAbsolutePath());
        Tools.swapFragment(requireActivity(), InstanceEditorFragment.class,
                InstanceEditorFragment.TAG, args);
    }

    @Override
    public void onLaunch(@NonNull Instance instance) {
        Instances.setSelectedInstance(instance);
        mSelectedRoot = instance.getInstanceRoot();
        mAdapter.setSelectedRoot(mSelectedRoot);
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    @Override
    public void onOpenFolder(@NonNull Instance instance) {
        final Context appContext = requireContext().getApplicationContext();
        MojoApplication.sExecutorService.execute(() -> {
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

    @Override
    public void onDuplicate(@NonNull Instance instance) {
        final String displayName = UiUtils.displayName(requireContext(), instance);
        final String copyName = getString(R.string.instances_copy_name, displayName);
        MojoApplication.sExecutorService.execute(() -> {
            boolean success = true;
            try {
                Instances.duplicateInstance(instance, copyName);
            } catch (IOException | RuntimeException e) {
                success = false;
            }
            final boolean finalSuccess = success;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                Toast.makeText(requireContext(), finalSuccess
                                ? getString(R.string.instances_duplicate_done, displayName)
                                : getString(R.string.instances_duplicate_failed),
                        finalSuccess ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                if (finalSuccess) loadInstances();
            });
        });
    }

    @Override
    public void onExport(@NonNull Instance instance) {
        String displayName = UiUtils.displayName(requireContext(), instance);
        String name = exportName(displayName);
        mPendingExportName = displayName;
        mPendingExportPath = instance.getInstanceRoot() == null
                ? null : instance.getInstanceRoot().getAbsolutePath();
        if (mPendingExportPath == null) {
            Toast.makeText(requireContext(), R.string.instances_export_missing,
                    Toast.LENGTH_LONG).show();
            return;
        }
        mExportLauncher.launch(name + ".zip");
    }

    private void onExportDestination(@Nullable Uri destination) {
        if (!isAdded()) return;
        final String path = mPendingExportPath;
        final String friendlyName = mPendingExportName;
        mPendingExportPath = null;
        mPendingExportName = null;
        if (destination == null) return;
        if (path == null) {
            Toast.makeText(requireContext(), R.string.instances_export_missing,
                    Toast.LENGTH_LONG).show();
            return;
        }
        final Context appContext = requireContext().getApplicationContext();
        final String displayName = friendlyName == null ? new File(path).getName() : friendlyName;
        MojoApplication.sExecutorService.execute(() -> {
            boolean success = true;
            try (OutputStream output = appContext.getContentResolver()
                    .openOutputStream(destination)) {
                if (output == null) throw new IOException("Could not open the export file");
                InstanceExporter.zipDirectory(new File(path), output);
            } catch (IOException | RuntimeException e) {
                success = false;
            }
            final boolean finalSuccess = success;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                Toast.makeText(requireContext(), finalSuccess
                                ? getString(R.string.instances_export_done, displayName)
                                : getString(R.string.instances_export_failed),
                        finalSuccess ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
            });
        });
    }

    private static String exportName(String name) {
        String safe = name == null ? "" : name.trim();
        safe = safe.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isEmpty()) safe = "instance";
        if (safe.length() > 60) safe = safe.substring(0, 60);
        return safe;
    }

    @Override
    public void onContent(@NonNull Instance instance) {
        if (instance.getInstanceRoot() == null) {
            Toast.makeText(requireContext(), R.string.instances_export_missing,
                    Toast.LENGTH_LONG).show();
            return;
        }
        Tools.swapFragment(requireActivity(),
                InstanceContentFragment.newInstance(instance.getInstanceRoot()),
                InstanceContentFragment.TAG);
    }

    @Override
    public void onDelete(@NonNull Instance instance) {
        DeleteConfirmDialogFragment dialog = DeleteConfirmDialogFragment.newInstance(
                instance.getInstanceRoot(), UiUtils.displayName(requireContext(), instance));
        dialog.show(getParentFragmentManager(), DeleteConfirmDialogFragment.TAG);
    }

    private interface TextWatcherCallback {
        void onTextChanged(String text);
    }

    private static class SimpleTextWatcher implements android.text.TextWatcher {
        private final TextWatcherCallback mCallback;

        SimpleTextWatcher(TextWatcherCallback callback) {
            mCallback = callback;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mCallback.onTextChanged(s.toString());
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {}
    }
}
