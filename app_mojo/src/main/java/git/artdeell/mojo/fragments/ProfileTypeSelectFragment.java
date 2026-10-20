package git.artdeell.mojo.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
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
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.ui.ScreenTitleProvider;

import java.io.IOException;

/**
 * Create screen. Loader and modpack entries reuse the existing installer flows; vanilla creates
 * an empty instance immediately and opens the editor.
 */
public class ProfileTypeSelectFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "ProfileTypeSelectFragment";

    private View mProgress;
    private boolean mCreating;

    public ProfileTypeSelectFragment() {
        super(R.layout.fragment_profile_type);
    }

    @Override
    public int getScreenTitle() {
        return R.string.create_title;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mProgress = view.findViewById(R.id.create_progress);

        bindOption(view.findViewById(R.id.create_vanilla), R.drawable.ic_ui_instance_default,
                R.string.create_vanilla_title, R.string.create_vanilla_body, v -> createVanilla());
        bindOption(view.findViewById(R.id.create_optifine), R.drawable.ic_ui_settings,
                R.string.create_optifine_title, R.string.create_optifine_body,
                v -> open(OptiFineInstallFragment.class, OptiFineInstallFragment.TAG));
        bindOption(view.findViewById(R.id.create_fabric), R.drawable.ic_ui_box,
                R.string.modloader_dl_install_fabric_instance, R.string.create_fabric_body,
                v -> open(FabricInstallFragment.class, FabricInstallFragment.TAG));
        bindOption(view.findViewById(R.id.create_quilt), R.drawable.ic_ui_box,
                R.string.modloader_dl_install_quilt_instance, R.string.create_quilt_body,
                v -> open(QuiltInstallFragment.class, QuiltInstallFragment.TAG));
        bindOption(view.findViewById(R.id.create_legacy_fabric), R.drawable.ic_ui_box,
                R.string.modloader_dl_install_legacy_fabric_instance, R.string.create_legacy_fabric_body,
                v -> open(LegacyFabricInstallFragment.class, LegacyFabricInstallFragment.TAG));
        bindOption(view.findViewById(R.id.create_forge), R.drawable.ic_ui_box,
                R.string.modloader_dl_install_forge_instance, R.string.create_forge_body,
                v -> open(ForgeInstallFragment.class, ForgeInstallFragment.TAG));
        bindOption(view.findViewById(R.id.create_neoforge), R.drawable.ic_ui_box,
                R.string.modloader_dl_install_neoforge_instance, R.string.create_neoforge_body,
                v -> open(NeoforgeInstallFragment.class, NeoforgeInstallFragment.TAG));
        bindOption(view.findViewById(R.id.create_bta), R.drawable.ic_ui_box,
                R.string.create_bta_instance, R.string.create_bta_body,
                v -> open(BTAInstallFragment.class, BTAInstallFragment.TAG));
        bindOption(view.findViewById(R.id.create_modpack), R.drawable.ic_ui_browse,
                R.string.create_modpack_title, R.string.create_modpack_body,
                v -> ((LauncherActivity) requireActivity()).selectRootTab(LauncherActivity.TAB_BROWSE));
        bindOption(view.findViewById(R.id.create_import), R.drawable.ic_ui_file_download,
                R.string.create_import_title, R.string.create_import_body,
                v -> ((LauncherActivity) requireActivity()).requestBrowseImport());
    }

    private void bindOption(View option, int icon, @StringRes int title, @StringRes int body,
                            View.OnClickListener listener) {
        ((ImageView) option.findViewById(R.id.create_option_icon)).setImageResource(icon);
        ((TextView) option.findViewById(R.id.create_option_title)).setText(title);
        ((TextView) option.findViewById(R.id.create_option_body)).setText(body);
        option.setOnClickListener(listener);
        option.setContentDescription(getString(title) + ". " + getString(body));
    }

    private void open(Class<? extends Fragment> fragmentClass, String tag) {
        if (mCreating) return;
        Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
    }

    private void createVanilla() {
        if (mCreating) return;
        mCreating = true;
        mProgress.setVisibility(View.VISIBLE);
        final String defaultName = getString(R.string.create_default_name);
        MojoApplication.sExecutorService.execute(() -> {
            Instance instance = null;
            IOException error = null;
            try {
                instance = Instances.createInstance(created -> {
                    created.sharedData = true;
                    created.versionId = Instance.VERSION_LATEST_RELEASE;
                    created.name = defaultName;
                }, null);
                Instances.setSelectedInstance(instance);
            } catch (IOException e) {
                error = e;
            }
            Instance finalInstance = instance;
            IOException finalError = error;
            Tools.runOnUiThread(() -> {
                mCreating = false;
                if (!isAdded() || getView() == null) return;
                mProgress.setVisibility(View.GONE);
                if (finalError != null) {
                    Tools.showError(requireContext(), finalError);
                    return;
                }
                if (finalInstance == null) {
                    Toast.makeText(requireContext(), R.string.editor_save_failed,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                ((LauncherActivity) requireActivity()).refreshAccountChip();
                Bundle args = new Bundle();
                args.putString(InstanceEditorFragment.ARG_INSTANCE_ROOT,
                        finalInstance.getInstanceRoot().getAbsolutePath());
                Tools.swapFragment(requireActivity(), InstanceEditorFragment.class,
                        InstanceEditorFragment.TAG, args);
            });
        });
    }
}
