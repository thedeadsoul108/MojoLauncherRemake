package git.artdeell.mojo.fragments;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.R;
import git.artdeell.mojo.content.ContentInstaller;
import git.artdeell.mojo.progresskeeper.ProgressKeeper;
import git.artdeell.mojo.ui.ScreenTitleProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists every active progress record. The global listener is the only data source, so this
 * screen works for downloads started from any other screen or from the background service.
 */
public class DownloadsFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "DownloadsFragment";

    private final Map<String, TaskRow> mRows = new LinkedHashMap<>();
    private LinearLayout mTasks;
    private View mEmpty;
    private TextView mCount;
    private View mNotificationCard;
    private View mNotificationsEnabled;
    private Button mNotificationAction;
    private boolean mViewReady;

    private final ProgressKeeper.GlobalProgressListener mProgressListener = tasks -> {
        View view = getView();
        if (view == null) return;
        view.post(() -> {
            if (!isAdded() || getView() == null || !mViewReady) return;
            renderTasks(tasks);
        });
    };

    public DownloadsFragment() {
        super(R.layout.fragment_downloads);
    }

    @Override
    public int getScreenTitle() {
        return R.string.nav_downloads;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTasks = view.findViewById(R.id.downloads_tasks_container);
        mEmpty = view.findViewById(R.id.downloads_empty);
        mCount = view.findViewById(R.id.downloads_count);
        mNotificationCard = view.findViewById(R.id.downloads_notification_card);
        mNotificationsEnabled = view.findViewById(R.id.downloads_notifications_enabled);
        mNotificationAction = view.findViewById(R.id.downloads_notification_action);
        view.findViewById(R.id.downloads_empty_browse).setOnClickListener(v -> {
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).selectRootTab(LauncherActivity.TAB_BROWSE);
            }
        });

        mNotificationAction.setOnClickListener(v -> {
            if (!(getActivity() instanceof LauncherActivity)) return;
            LauncherActivity activity = (LauncherActivity) getActivity();
            activity.requestNotificationPermission();
        });

        getParentFragmentManager().setFragmentResultListener(
                LauncherActivity.FRAGMENT_RESULT_NOTIFICATION_PERMISSION, this,
                (requestKey, result) -> refreshNotificationCard());

        mViewReady = true;
        ProgressKeeper.addGlobalProgressListener(mProgressListener);
        refreshNotificationCard();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshNotificationCard();
        if (mViewReady) renderTasks(ProgressKeeper.getTaskSnapshots());
    }

    @Override
    public void onDestroyView() {
        mViewReady = false;
        ProgressKeeper.removeGlobalProgressListener(mProgressListener);
        mRows.clear();
        super.onDestroyView();
    }

    private void refreshNotificationCard() {
        if (!isAdded() || mNotificationCard == null) return;
        boolean granted;
        if (getActivity() instanceof LauncherActivity) {
            granted = ((LauncherActivity) getActivity()).hasNotificationPermission();
        } else {
            granted = true;
        }
        if (Build.VERSION.SDK_INT < 33 || granted) {
            mNotificationCard.setVisibility(View.GONE);
            mNotificationsEnabled.setVisibility(View.VISIBLE);
            TextView note = mNotificationsEnabled.findViewById(
                    R.id.downloads_notifications_enabled_text);
            if (note != null) {
                note.setText(granted
                        ? R.string.downloads_notification_note
                        : R.string.downloads_notification_enabled);
            }
        } else {
            mNotificationCard.setVisibility(View.VISIBLE);
            mNotificationsEnabled.setVisibility(View.GONE);
        }
    }

    private void renderTasks(@NonNull List<ProgressKeeper.TaskSnapshot> tasks) {
        if (!isAdded() || getView() == null || !mViewReady || mTasks == null) return;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<String> seen = new ArrayList<>(tasks.size());
        for (ProgressKeeper.TaskSnapshot task : tasks) {
            seen.add(task.key);
            TaskRow row = mRows.get(task.key);
            if (row == null) {
                row = new TaskRow(inflater.inflate(R.layout.view_download_task, mTasks, false));
                mRows.put(task.key, row);
                mTasks.addView(row.root);
            }
            row.bind(task);
        }
        for (String key : new ArrayList<>(mRows.keySet())) {
            if (!seen.contains(key)) {
                TaskRow row = mRows.remove(key);
                if (row != null) mTasks.removeView(row.root);
            }
        }

        boolean empty = tasks.isEmpty();
        mEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mCount.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) {
            mCount.setText(getString(R.string.downloads_task_count, tasks.size()));
        }
    }

    private String taskTitle(String key) {
        if (ContentInstaller.isContentProgressKey(key)) {
            return getString(R.string.downloads_task_content);
        }
        switch (key) {
            case ProgressLayout.DOWNLOAD_GAME:
                return getString(R.string.downloads_task_download_game);
            case ProgressLayout.UNPACK_RUNTIME:
                return getString(R.string.downloads_task_runtime);
            case ProgressLayout.INSTALL_MODPACK:
                return getString(R.string.downloads_task_modpack);
            case ProgressLayout.AUTHENTICATE:
                return getString(R.string.downloads_task_auth);
            case ProgressLayout.DOWNLOAD_VERSION_LIST:
                return getString(R.string.downloads_task_versions);
            case ProgressLayout.INSTANCE_INSTALL:
                return getString(R.string.downloads_task_instance);
            case ProgressLayout.DATA_MIGRATION:
                return getString(R.string.downloads_task_migration);
            case ProgressLayout.EXTRACT_COMPONENTS:
            case ProgressLayout.EXTRACT_SINGLE_FILES:
                return getString(R.string.downloads_task_extract);
            default:
                return getString(R.string.downloads_task_generic);
        }
    }

    private class TaskRow {
        final View root;
        final TextView title;
        final TextView message;
        final TextView percent;
        final ProgressBar progress;

        TaskRow(View root) {
            this.root = root;
            title = root.findViewById(R.id.download_task_title);
            message = root.findViewById(R.id.download_task_message);
            percent = root.findViewById(R.id.download_task_percent);
            progress = root.findViewById(R.id.download_task_progress);
        }

        void bind(ProgressKeeper.TaskSnapshot task) {
            title.setText(taskTitle(task.key));
            String messageText = resolveMessage(task);
            message.setText(messageText);
            message.setVisibility(messageText.isEmpty() ? View.GONE : View.VISIBLE);
            if (task.progress < 0) {
                progress.setIndeterminate(true);
                percent.setVisibility(View.GONE);
            } else {
                int value = Math.max(0, Math.min(100, task.progress));
                progress.setIndeterminate(false);
                progress.setProgress(value);
                percent.setVisibility(View.VISIBLE);
                percent.setText(getString(R.string.downloads_progress_percent, value));
            }
        }

        private String resolveMessage(ProgressKeeper.TaskSnapshot task) {
            if (task.resid == -1) {
                if (task.args != null && task.args.length > 0 && task.args[0] instanceof String) {
                    return (String) task.args[0];
                }
                return "";
            }
            try {
                if (task.args == null || task.args.length == 0) {
                    return getString(task.resid);
                }
                return getString(task.resid, task.args);
            } catch (RuntimeException e) {
                return "";
            }
        }
    }
}
