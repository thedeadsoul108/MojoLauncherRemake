package git.artdeell.mojo.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.content.ContentEntry;
import git.artdeell.mojo.content.ContentScanner;
import git.artdeell.mojo.content.ContentUpdateChecker;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.ui.ScreenTitleProvider;
import git.artdeell.mojo.ui.UiUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lists the mods and packs of one instance. Toggling a file only renames it, so a disabled file
 * survives and can be enabled again from the same screen.
 */
public class InstanceContentFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "InstanceContentFragment";
    private static final String ARG_ROOT = "instance_root";
    private static final int UPDATE_CHECK_LIMIT = 20;

    private RecyclerView mList;
    private LinearLayout mEmpty;
    private ProgressBar mProgress;
    private TextView mHeading;
    private Button mCheckUpdates;
    private InstanceContentAdapter mAdapter;
    private Instance mInstance;
    private List<ContentEntry> mEntries = Collections.emptyList();
    private File mInstanceRoot;
    private boolean mLoadPending;
    private boolean mChecking;
    private boolean mTogglePending;
    private int mGeneration;

    public static InstanceContentFragment newInstance(@NonNull File instanceRoot) {
        InstanceContentFragment fragment = new InstanceContentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOT, instanceRoot.getAbsolutePath());
        fragment.setArguments(args);
        return fragment;
    }

    public InstanceContentFragment() {
        super(R.layout.fragment_instance_content);
    }

    @Override
    public int getScreenTitle() {
        return R.string.content_title;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mList = view.findViewById(R.id.content_list);
        mEmpty = view.findViewById(R.id.content_empty);
        mProgress = view.findViewById(R.id.content_progress);
        mHeading = view.findViewById(R.id.content_heading);
        mCheckUpdates = view.findViewById(R.id.content_check_updates);
        mAdapter = new InstanceContentAdapter(this::toggleEntry);
        mList.setLayoutManager(new LinearLayoutManager(requireContext()));
        mList.setAdapter(mAdapter);
        mCheckUpdates.setOnClickListener(v -> checkForUpdates());
        mCheckUpdates.setEnabled(false);

        Bundle args = getArguments();
        String path = args == null ? null : args.getString(ARG_ROOT);
        mInstanceRoot = path == null ? null : new File(path);
        if (mInstanceRoot == null) {
            showLoadFailure();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mInstanceRoot != null) loadContent();
    }

    @Override
    public void onDestroyView() {
        mGeneration++;
        mLoadPending = false;
        mChecking = false;
        mTogglePending = false;
        if (mList != null) mList.setAdapter(null);
        mList = null;
        mEmpty = null;
        mProgress = null;
        mHeading = null;
        mCheckUpdates = null;
        mAdapter = null;
        super.onDestroyView();
    }

    private void loadContent() {
        if (mLoadPending || mInstanceRoot == null) return;
        mLoadPending = true;
        if (mProgress != null) mProgress.setVisibility(View.VISIBLE);
        final int generation = mGeneration;
        MojoApplication.sExecutorService.execute(() -> {
            Instance instance = Instances.loadInstance(mInstanceRoot);
            List<ContentEntry> entries = null;
            Throwable error = null;
            try {
                if (instance == null) throw new IOException("Instance metadata is missing");
                entries = ContentScanner.scan(instance.getGameDirectory());
            } catch (IOException | RuntimeException e) {
                error = e;
            }
            final Instance loadedInstance = instance;
            final List<ContentEntry> loadedEntries = entries;
            final Throwable loadedError = error;
            Tools.runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                mLoadPending = false;
                if (mProgress != null) mProgress.setVisibility(View.GONE);
                if (loadedError != null) {
                    showLoadFailure();
                    return;
                }
                mInstance = loadedInstance;
                mEntries = loadedEntries == null ? Collections.emptyList() : loadedEntries;
                mAdapter.submit(mEntries);
                if (mHeading != null && mInstance != null) {
                    String name = UiUtils.displayName(requireContext(), mInstance);
                    if (mInstance.sharedData) name = name + " · " + getString(R.string.content_shared_suffix);
                    mHeading.setText(getString(R.string.content_instance, name));
                }
                boolean hasCheckable = false;
                for (ContentEntry entry : mEntries) {
                    if (ContentUpdateChecker.isCheckable(entry)) {
                        hasCheckable = true;
                        break;
                    }
                }
                if (mCheckUpdates != null) {
                    mCheckUpdates.setEnabled(hasCheckable && !mChecking);
                }
                if (mEmpty != null) mEmpty.setVisibility(mEntries.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void toggleEntry(@NonNull ContentEntry entry) {
        if (mLoadPending || mChecking || mTogglePending) return;
        mTogglePending = true;
        final int generation = mGeneration;
        MojoApplication.sExecutorService.execute(() -> {
            boolean success = true;
            try {
                ContentScanner.toggle(entry);
            } catch (IOException | RuntimeException e) {
                success = false;
            }
            final boolean finalSuccess = success;
            Tools.runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                mTogglePending = false;
                if (!finalSuccess) {
                    Toast.makeText(requireContext(), R.string.content_toggle_failed,
                            Toast.LENGTH_LONG).show();
                    if (mAdapter != null) mAdapter.notifyDataSetChanged();
                    return;
                }
                loadContent();
            });
        });
    }

    private void checkForUpdates() {
        if (mInstance == null || mEntries.isEmpty() || mChecking) return;
        mChecking = true;
        if (mCheckUpdates != null) {
            mCheckUpdates.setEnabled(false);
            mCheckUpdates.setText(R.string.content_checking);
        }
        if (mProgress != null) mProgress.setVisibility(View.VISIBLE);
        final int generation = mGeneration;
        final List<ContentEntry> entries = new ArrayList<>(mEntries);
        MojoApplication.sExecutorService.execute(() -> {
            ContentUpdateChecker.Result result = null;
            Throwable error = null;
            try {
                result = ContentUpdateChecker.check(mInstance, entries, UPDATE_CHECK_LIMIT);
            } catch (IOException | RuntimeException e) {
                error = e;
            }
            final ContentUpdateChecker.Result loadedResult = result;
            final Throwable loadedError = error;
            Tools.runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                mChecking = false;
                if (mProgress != null) mProgress.setVisibility(View.GONE);
                if (mCheckUpdates != null) {
                    mCheckUpdates.setText(R.string.content_check_updates);
                    mCheckUpdates.setEnabled(true);
                }
                if (loadedError != null) {
                    Toast.makeText(requireContext(), R.string.content_check_failed,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                showUpdateResult(loadedResult);
            });
        });
    }

    private void showUpdateResult(@Nullable ContentUpdateChecker.Result result) {
        if (result == null) return;
        if (result.updates.isEmpty()) {
            StringBuilder message = new StringBuilder(getString(R.string.content_checked_none));
            appendCheckNotes(message, result);
            Toast.makeText(requireContext(), message.toString(), Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder message = new StringBuilder();
        int shown = Math.min(6, result.updates.size());
        for (int i = 0; i < shown; i++) {
            ContentUpdateChecker.Update update = result.updates.get(i);
            if (i > 0) message.append('\n');
            message.append(getString(R.string.content_update_line, update.title,
                    update.installedVersion, update.latestVersion));
        }
        if (result.updates.size() > shown) {
            message.append('\n').append(getString(R.string.content_update_more,
                    result.updates.size() - shown));
        }
        appendCheckNotes(message, result);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.content_update_title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.content_update_browse, (dialog, which) -> {
                    if (isAdded() && getActivity() instanceof LauncherActivity) {
                        ((LauncherActivity) requireActivity()).selectRootTab(LauncherActivity.TAB_BROWSE);
                    }
                })
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private void appendCheckNotes(StringBuilder message, ContentUpdateChecker.Result result) {
        if (result.failed > 0) {
            message.append('\n').append(getString(R.string.content_checked_partial,
                    result.checked, result.failed));
        }
        if (result.remaining > 0) {
            message.append('\n').append(getString(R.string.content_checked_limit,
                    result.checked, result.remaining));
        }
    }

    private void showLoadFailure() {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), R.string.content_load_failed, Toast.LENGTH_LONG).show();
        if (mEmpty != null) mEmpty.setVisibility(View.VISIBLE);
        if (mCheckUpdates != null) mCheckUpdates.setEnabled(false);
    }
}
