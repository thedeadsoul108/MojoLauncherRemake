package git.artdeell.mojo.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.game.renderer.RendererCache;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.InstanceIconProvider;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.multirt.MultiRTUtils;
import git.artdeell.mojo.multirt.RTSpinnerAdapter;
import git.artdeell.mojo.multirt.Runtime;
import git.artdeell.mojo.ui.ScreenTitleProvider;
import git.artdeell.mojo.ui.UiUtils;
import git.artdeell.mojo.utils.CropperUtils;
import git.artdeell.mojo.utils.profiles.VersionSelectorDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sectioned instance editor with a dirty-state guard. Instance metadata is read and written on
 * the executor; the UI only ever touches already-loaded values.
 */
public class InstanceEditorFragment extends Fragment implements ScreenTitleProvider,
        CropperUtils.CropperReceiver {
    public static final String TAG = "InstanceEditorFragment";
    public static final String ARG_INSTANCE_ROOT = "instance_root";

    private Instance mInstance;
    private String mSelectedVersion = "";
    private String mSelectedControlLayout;
    private int mInitialRuntimePosition = -1;
    private int mInitialRendererPosition = -1;
    private boolean mDirty;
    private boolean mBinding;
    private boolean mSaving;
    private int mRecommendedIconSize;
    private Bundle mPendingEdits;

    private ImageView mIcon;
    private EditText mName;
    private EditText mJvm;
    private EditText mNotes;
    private TextView mNameHelper;
    private TextView mVersionValue;
    private TextView mLoaderValue;
    private TextView mControlValue;
    private AppCompatSpinner mRuntime;
    private AppCompatSpinner mRenderer;
    private SwitchCompat mSharedData;
    private Button mSave;
    private View mContent;
    private View mLoading;
    private OnBackPressedCallback mBackCallback;

    private List<String> mRenderNames = new ArrayList<>();
    private List<Runtime> mRuntimes = new ArrayList<>();

    private final ActivityResultLauncher<?> mCropperLauncher =
            CropperUtils.registerCropper(this, this);

    public InstanceEditorFragment() {
        super(R.layout.fragment_instance_editor);
    }

    @Override
    public int getScreenTitle() {
        return R.string.editor_title;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupListeners(view);

        String controlValue = (String) ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR);
        if (controlValue != null) mSelectedControlLayout = controlValue;

        mBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showDiscardDialog();
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), mBackCallback);

        getParentFragmentManager().setFragmentResultListener(
                DeleteConfirmDialogFragment.RESULT_KEY, this,
                (requestKey, result) -> {
                    if (result.getBoolean("success") && getActivity() instanceof LauncherActivity) {
                        ((LauncherActivity) getActivity())
                                .selectRootTab(LauncherActivity.TAB_INSTANCES);
                    }
                });

        loadInstance();
    }

    private void bindViews(View view) {
        mContent = view.findViewById(R.id.editor_content);
        mLoading = view.findViewById(R.id.editor_loading);
        mIcon = view.findViewById(R.id.editor_icon);
        mName = view.findViewById(R.id.editor_name);
        mJvm = view.findViewById(R.id.editor_jvm);
        mNotes = view.findViewById(R.id.editor_notes);
        mNameHelper = view.findViewById(R.id.editor_name_helper);
        mVersionValue = view.findViewById(R.id.editor_version_value);
        mLoaderValue = view.findViewById(R.id.editor_loader_value);
        mControlValue = view.findViewById(R.id.editor_control_value);
        mRuntime = view.findViewById(R.id.editor_runtime);
        mRenderer = view.findViewById(R.id.editor_renderer);
        mSharedData = view.findViewById(R.id.editor_shared_data);
        mSave = view.findViewById(R.id.editor_save);
    }

    private void setupListeners(View view) {
        view.findViewById(R.id.editor_change_icon).setOnClickListener(v -> {
            mRecommendedIconSize = Math.max(mIcon.getWidth(), mIcon.getHeight());
            CropperUtils.startCropper(mCropperLauncher);
        });
        mIcon.setOnClickListener(v -> {
            mRecommendedIconSize = Math.max(mIcon.getWidth(), mIcon.getHeight());
            CropperUtils.startCropper(mCropperLauncher);
        });

        view.findViewById(R.id.editor_version_row).setOnClickListener(v ->
                VersionSelectorDialog.open(requireContext(), false, (id, snapshot) -> {
                    mSelectedVersion = id;
                    mVersionValue.setText(UiUtils.versionName(requireContext(), id));
                    mLoaderValue.setText(UiUtils.loaderName(requireContext(), id));
                    markDirty();
                }));

        View.OnClickListener controlListener = v -> {
            // The editor view is recreated when the picker is popped, so keep the in-progress
            // edits on the retained fragment instance instead of silently dropping them.
            mPendingEdits = captureEdits();
            Bundle bundle = new Bundle(2);
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, false);
            bundle.putString(FileSelectorFragment.BUNDLE_ROOT_PATH, Tools.CTRLMAP_PATH);
            Tools.swapFragment(requireActivity(), FileSelectorFragment.class,
                    FileSelectorFragment.TAG, bundle);
        };
        view.findViewById(R.id.editor_control_row).setOnClickListener(controlListener);

        mName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!mBinding) markDirty();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        mJvm.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!mBinding) markDirty();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        mNotes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!mBinding) markDirty();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        mSharedData.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!mBinding) markDirty();
        });

        View manageContent = view.findViewById(R.id.editor_manage_content);
        if (manageContent != null) {
            manageContent.setOnClickListener(v -> {
                if (mInstance == null) return;
                Tools.swapFragment(requireActivity(),
                        InstanceContentFragment.newInstance(mInstance.getInstanceRoot()),
                        InstanceContentFragment.TAG);
            });
        }

        View jvmPresets = view.findViewById(R.id.editor_jvm_presets);
        if (jvmPresets != null) {
            jvmPresets.setOnClickListener(v -> showJvmPresetsDialog());
        }

        View openDir = view.findViewById(R.id.editor_open_directory);
        if (openDir != null) {
            openDir.setOnClickListener(v -> {
                if (mInstance == null) return;
                File gameDir = mInstance.getGameDirectory();
                FileUtils.ensureDirectorySilently(gameDir);
                Tools.openPath(requireContext(), gameDir, false);
            });
        }

        View duplicate = view.findViewById(R.id.editor_duplicate);
        if (duplicate != null) {
            duplicate.setOnClickListener(v -> {
                if (mInstance == null) return;
                final String displayName = UiUtils.displayName(requireContext(), mInstance);
                final String copyName = getString(R.string.instances_copy_name, displayName);
                MojoApplication.sExecutorService.execute(() -> {
                    boolean success = true;
                    try {
                        Instances.duplicateInstance(mInstance, copyName);
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
                        if (finalSuccess) {
                            getParentFragmentManager().popBackStack();
                        }
                    });
                });
            });
        }

        mSave.setOnClickListener(v -> save());
        view.findViewById(R.id.editor_delete).setOnClickListener(v -> {
            if (mInstance == null) return;
            DeleteConfirmDialogFragment dialog = DeleteConfirmDialogFragment.newInstance(
                    mInstance.getInstanceRoot(), mName.getText().toString());
            dialog.show(getParentFragmentManager(), DeleteConfirmDialogFragment.TAG);
        });
    }

    private void showJvmPresetsDialog() {
        String[] presetNames = new String[] {
                getString(R.string.settings_jvm_preset_aikar),
                getString(R.string.settings_jvm_preset_shenandoah),
                getString(R.string.settings_jvm_preset_zgc),
                getString(R.string.settings_jvm_preset_low_ram),
                getString(R.string.settings_jvm_preset_clear)
        };
        String[] presetValues = new String[] {
                "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1",
                "-XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCMode=iu -XX:+AlwaysPreTouch",
                "-XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+AlwaysPreTouch",
                "-XX:+UseG1GC -XX:MaxGCPauseMillis=150 -XX:+ParallelRefProcEnabled -XX:SurvivorRatio=8",
                ""
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_jvm_presets)
                .setItems(presetNames, (dialog, which) -> {
                    mJvm.setText(presetValues[which]);
                    markDirty();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadInstance() {
        Bundle args = getArguments();
        String rootPath = args == null ? null : args.getString(ARG_INSTANCE_ROOT);
        Context appContext = requireContext().getApplicationContext();
        final android.content.res.Resources resources = getResources();
        MojoApplication.sExecutorService.execute(() -> {
            Instance instance = null;
            List<Runtime> runtimes = new ArrayList<>();
            RendererCache renderers = null;
            try {
                if (rootPath != null) {
                    instance = Instances.loadInstance(new File(rootPath));
                } else {
                    instance = Instances.loadSelectedInstance();
                }
                if (instance != null) {
                    runtimes.addAll(MultiRTUtils.getRuntimes());
                    renderers = RendererCache.getCompatibleRenderers(appContext);
                    InstanceIconProvider.prewarm(resources,
                            java.util.Collections.singletonList(instance));
                }
            } catch (Exception e) {
                Log.w("InstanceEditor", "Failed to load instance", e);
            }
            Instance finalInstance = instance;
            List<Runtime> finalRuntimes = runtimes;
            RendererCache finalRenderers = renderers;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                if (finalInstance == null) {
                    Toast.makeText(requireContext(), R.string.editor_instance_missing,
                            Toast.LENGTH_LONG).show();
                    getParentFragmentManager().popBackStack();
                    return;
                }
                mRuntimes = finalRuntimes;
                bindInstance(finalInstance, finalRenderers);
            });
        });
    }

    private void bindInstance(@NonNull Instance instance, @Nullable RendererCache renderers) {
        mBinding = true;
        mInstance = instance;

        mIcon.setImageDrawable(InstanceIconProvider.fetchIcon(getResources(), instance));
        mName.setText(instance.getDisplayName());
        mJvm.setText(nullToEmpty(instance.jvmArgs));
        mNotes.setText(nullToEmpty(instance.notes));
        mSelectedVersion = nullToEmpty(instance.versionId);
        mVersionValue.setText(UiUtils.versionName(requireContext(), mSelectedVersion));
        mLoaderValue.setText(UiUtils.loaderName(requireContext(), mSelectedVersion));
        mSharedData.setChecked(instance.sharedData);

        String control = mSelectedControlLayout != null
                ? mSelectedControlLayout : nullToEmpty(instance.controlLayout);
        mControlValue.setText(control.isEmpty()
                ? getString(R.string.editor_control_default) : control);

        RTSpinnerAdapter runtimeAdapter = new RTSpinnerAdapter(requireContext(), mRuntimes);
        mRuntime.setAdapter(runtimeAdapter);
        int runtimeIndex = -1;
        if (instance.selectedRuntime != null) {
            runtimeIndex = mRuntimes.indexOf(new Runtime(instance.selectedRuntime));
        }
        mRuntime.setSelection(runtimeIndex == -1 ? mRuntimes.size() - 1 : runtimeIndex);

        List<String> renderList = new ArrayList<>();
        if (renderers != null) {
            mRenderNames = new ArrayList<>(renderers.rendererIds);
            renderList.addAll(Arrays.asList(renderers.rendererDisplayNames));
        } else {
            mRenderNames = new ArrayList<>();
        }
        renderList.add(getString(R.string.global_default));
        mRenderer.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, renderList));
        int rendererIndex = mRenderNames.indexOf(instance.getLaunchRenderer());
        mRenderer.setSelection(rendererIndex == -1 ? renderList.size() - 1 : rendererIndex);

        AdapterView.OnItemSelectedListener dirtyListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mBinding) return;
                int initial = parent == mRuntime ? mInitialRuntimePosition : mInitialRendererPosition;
                if (position != initial) markDirty();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        mRuntime.setOnItemSelectedListener(dirtyListener);
        mRenderer.setOnItemSelectedListener(dirtyListener);

        mInitialRuntimePosition = mRuntime.getSelectedItemPosition();
        mInitialRendererPosition = mRenderer.getSelectedItemPosition();

        mDirty = false;
        mBinding = false;
        if (mBackCallback != null) mBackCallback.setEnabled(false);
        if (mPendingEdits != null) {
            Bundle pending = mPendingEdits;
            mPendingEdits = null;
            applyEdits(pending);
        }

        mContent.setVisibility(View.VISIBLE);
        mLoading.setVisibility(View.GONE);
    }

    private Bundle captureEdits() {
        Bundle edits = new Bundle();
        edits.putString("name", mName.getText().toString());
        edits.putString("jvm", mJvm.getText().toString());
        edits.putString("notes", mNotes.getText().toString());
        edits.putString("version", mSelectedVersion);
        edits.putBoolean("shared", mSharedData.isChecked());
        edits.putInt("runtime", mRuntime.getSelectedItemPosition());
        edits.putInt("renderer", mRenderer.getSelectedItemPosition());
        edits.putBoolean("dirty", mDirty);
        return edits;
    }

    private void applyEdits(@NonNull Bundle edits) {
        mBinding = true;
        String name = edits.getString("name");
        if (name != null) mName.setText(name);
        String jvm = edits.getString("jvm");
        if (jvm != null) mJvm.setText(jvm);
        String notes = edits.getString("notes");
        if (notes != null) mNotes.setText(notes);
        String version = edits.getString("version");
        if (version != null && !version.isEmpty()) {
            mSelectedVersion = version;
            mVersionValue.setText(UiUtils.versionName(requireContext(), version));
            mLoaderValue.setText(UiUtils.loaderName(requireContext(), version));
        }
        mSharedData.setChecked(edits.getBoolean("shared", mSharedData.isChecked()));
        int runtime = edits.getInt("runtime", mRuntime.getSelectedItemPosition());
        if (runtime >= 0 && runtime < mRuntimes.size()) mRuntime.setSelection(runtime);
        int renderer = edits.getInt("renderer", mRenderer.getSelectedItemPosition());
        if (renderer >= 0 && renderer < mRenderer.getCount()) mRenderer.setSelection(renderer);
        mInitialRuntimePosition = mRuntime.getSelectedItemPosition();
        mInitialRendererPosition = mRenderer.getSelectedItemPosition();
        mBinding = false;
        // Restore the dirty flag captured before the picker opened. Opening and cancelling the
        // picker must not turn an untouched editor into an unsaved one.
        mDirty = edits.getBoolean("dirty", true);
        if (mBackCallback != null) mBackCallback.setEnabled(mDirty);
    }

    private void markDirty() {
        if (mDirty) return;
        mDirty = true;
        if (mBackCallback != null) mBackCallback.setEnabled(true);
    }

    private void save() {
        if (mInstance == null || mSaving) return;
        final String newName = mName.getText().toString().trim();
        if (newName.isEmpty()) {
            showNameError(R.string.editor_name_required, true);
            return;
        }
        if (newName.contains("/") || newName.contains("\\") || newName.contains(":")
                || newName.contains("*") || newName.contains("?") || newName.contains("\"")
                || newName.contains("<") || newName.contains(">") || newName.contains("|")
                || newName.equals(".") || newName.equals("..")) {
            showNameError(R.string.editor_name_invalid, true);
            return;
        }
        if (mSelectedVersion.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_no_version, Toast.LENGTH_LONG).show();
            return;
        }

        mSaving = true;
        mSave.setEnabled(false);
        final String jvmArgs = mJvm.getText().toString().trim();
        final String notes = mNotes.getText().toString().trim();
        final String defaultControlLabel = getString(R.string.editor_control_default);
        final String control = mSelectedControlLayout != null
                ? mSelectedControlLayout : mControlValue.getText().toString().trim();
        final boolean sharedData = mSharedData.isChecked();
        final String versionId = mSelectedVersion;
        final int runtimePosition = mRuntime.getSelectedItemPosition();
        final Runtime runtime = runtimePosition >= 0 && runtimePosition < mRuntimes.size()
                ? mRuntimes.get(runtimePosition) : null;
        final int rendererPosition = mRenderer.getSelectedItemPosition();

        MojoApplication.sExecutorService.execute(() -> {
            boolean success = true;
            try {
                // Drop the icon under the current directory before a possible rename, then again
                // after the save so a changed loader/icon value is re-read on the next bind.
                InstanceIconProvider.dropIcon(mInstance);
                if (!newName.equals(mInstance.name)) {
                    Instances.renameInstanceDirectory(mInstance, newName);
                }
                mInstance.name = newName;
                mInstance.versionId = versionId;
                mInstance.jvmArgs = jvmArgs.isEmpty() ? null : jvmArgs;
                mInstance.notes = notes.isEmpty() ? null : notes;
                mInstance.controlLayout = control.isEmpty() || control.equals(defaultControlLabel)
                        ? null : control;
                mInstance.sharedData = sharedData;
                mInstance.selectedRuntime = runtime == null
                        || runtime.name.equals("<Default>") || runtime.versionString == null
                        ? null : runtime.name;
                if (rendererPosition == mRenderNames.size()) {
                    mInstance.renderer = null;
                } else if (rendererPosition >= 0 && rendererPosition < mRenderNames.size()) {
                    mInstance.renderer = mRenderNames.get(rendererPosition);
                }
                mInstance.write();
                InstanceIconProvider.dropIcon(mInstance);
            } catch (IOException | RuntimeException e) {
                success = false;
            }
            boolean finalSuccess = success;
            Tools.runOnUiThread(() -> {
                mSaving = false;
                if (!isAdded() || getView() == null) return;
                mSave.setEnabled(true);
                if (!finalSuccess) {
                    Toast.makeText(requireContext(), R.string.editor_save_failed,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                mDirty = false;
                if (mBackCallback != null) mBackCallback.setEnabled(false);
                if (mInstance != null) {
                    mSelectedVersion = mInstance.versionId;
                    InstanceIconProvider.dropIcon(mInstance);
                }
                Toast.makeText(requireContext(), R.string.editor_saved, Toast.LENGTH_SHORT).show();
                if (getActivity() instanceof LauncherActivity) {
                    ((LauncherActivity) getActivity())
                            .selectRootTab(LauncherActivity.TAB_INSTANCES);
                }
            });
        });
    }

    private void showNameError(int message, boolean error) {
        mNameHelper.setText(message);
        mNameHelper.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                error ? R.color.danger : R.color.text_secondary));
        mName.requestFocus();
    }

    private void showDiscardDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.editor_unsaved_title)
                .setMessage(R.string.editor_unsaved_body)
                .setPositiveButton(R.string.editor_discard, (dialog, which) -> {
                    mDirty = false;
                    if (mBackCallback != null) mBackCallback.setEnabled(false);
                    getParentFragmentManager().popBackStack();
                })
                .setNegativeButton(R.string.editor_keep_editing, null)
                .show();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public float getAspectRatio() {
        return 1f;
    }

    @Override
    public int getTargetMaxSide() {
        return mRecommendedIconSize;
    }

    @Override
    public void onCropped(Bitmap contentBitmap) {
        if (mInstance == null || mIcon == null) return;
        mIcon.setImageBitmap(contentBitmap);
        MojoApplication.sExecutorService.execute(() -> {
            try {
                mInstance.encodeNewIcon(contentBitmap);
                InstanceIconProvider.dropIcon(mInstance);
            } catch (IOException e) {
                Tools.showErrorRemote(e);
            }
        });
    }

    @Override
    public void onFailed(Exception exception) {
        Tools.showErrorRemote(exception);
    }
}
