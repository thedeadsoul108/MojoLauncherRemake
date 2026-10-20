package git.artdeell.mojo.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.InstanceIconProvider;
import git.artdeell.mojo.instances.Instances;

import java.io.File;
import java.io.IOException;

/**
 * Confirmation dialog shared by the instance library and the editor. The directory is passed as
 * an argument so the dialog never guesses which instance the user meant, and deletion happens on
 * the executor because it can remove a large game directory.
 */
public class DeleteConfirmDialogFragment extends DialogFragment {
    public static final String TAG = "delete_dialog_confirm";
    public static final String RESULT_KEY = "instance_deleted";
    public static final String ARG_INSTANCE_ROOT = "instance_root";
    public static final String ARG_INSTANCE_NAME = "instance_name";

    public static DeleteConfirmDialogFragment newInstance(@NonNull File instanceRoot,
                                                          @Nullable String instanceName) {
        DeleteConfirmDialogFragment dialog = new DeleteConfirmDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_INSTANCE_ROOT, instanceRoot.getAbsolutePath());
        args.putString(ARG_INSTANCE_NAME, instanceName);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        final String instanceRoot = args == null ? null : args.getString(ARG_INSTANCE_ROOT);
        String instanceName = args == null ? null : args.getString(ARG_INSTANCE_NAME);
        if (instanceRoot == null) {
            dismissAllowingStateLoss();
            return new AlertDialog.Builder(requireContext()).create();
        }
        String title = instanceName == null || instanceName.trim().isEmpty()
                ? getString(R.string.instance_delete)
                : getString(R.string.editor_delete_confirm_title, instanceName);

        return new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(R.string.editor_delete_confirm_body)
                .setPositiveButton(R.string.global_delete, (dialog, which) ->
                        deleteInstance(new File(instanceRoot),
                                getParentFragmentManager(),
                                requireContext().getApplicationContext()))
                .setNegativeButton(R.string.action_cancel, null)
                .create();
    }

    private void deleteInstance(File instanceRoot, androidx.fragment.app.FragmentManager manager,
                                android.content.Context appContext) {
        MojoApplication.sExecutorService.execute(() -> {
            boolean success = true;
            try {
                Instances.deleteInstance(instanceRoot);
                Instances.ensureValidSelection();
                InstanceIconProvider.dropIcon(instanceRoot);
            } catch (IOException e) {
                success = false;
            }
            boolean finalSuccess = success;
            Tools.runOnUiThread(() -> {
                Toast.makeText(appContext, finalSuccess ? R.string.editor_deleted
                        : R.string.editor_delete_failed,
                        finalSuccess ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                Bundle result = new Bundle();
                result.putBoolean("success", finalSuccess);
                result.putString(ARG_INSTANCE_ROOT, instanceRoot.getAbsolutePath());
                manager.setFragmentResult(RESULT_KEY, result);
            });
        });
    }
}
