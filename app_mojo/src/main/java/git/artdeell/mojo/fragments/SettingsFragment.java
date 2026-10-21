package git.artdeell.mojo.fragments;

import static git.artdeell.mojo.Architecture.is32BitsDevice;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.CustomControlsActivity;
import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.game.renderer.RendererCache;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.multirt.MultiRTConfigDialog;
import git.artdeell.mojo.multirt.MultiRTUtils;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.ui.ScreenTitleProvider;
import git.artdeell.mojo.ui.ThemeManager;
import git.artdeell.mojo.utils.FileUtils;
import git.artdeell.mojo.utils.GpuUtils;

import java.io.File;

/**
 * Modern settings hub inspired by Cubiq Launcher, featuring categorized cards,
 * live Vulkan diagnostics, interactive RAM visualizer with presets, renderer selection,
 * JVM arguments with GC presets, and direct controls.
 */
public class SettingsFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "SettingsFragment";

    private TextView mThemeSummary;
    private TextView mNotificationSummary;
    private TextView mRendererSummary;
    private TextView mRuntimeSummary;
    private TextView mJvmSummary;

    private MultiRTConfigDialog mDialogScreen;
    private final ActivityResultLauncher<Object> mVmInstallLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("xz"), data -> {
                if (data != null && getContext() != null) {
                    Tools.installRuntimeFromUri(getContext(), data);
                }
            });

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

        setupRendererSection(view, inflater);
        setupJavaSection(view, inflater);
        setupControlsSection(view, inflater);
        setupBehaviorSection(view, inflater);
        setupSystemSection(view, inflater);

        TextView footer = view.findViewById(R.id.settings_version_footer);
        footer.setText(getString(R.string.settings_version_footer,
                BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshThemeSummary();
        refreshNotificationSummary();
        refreshRuntimeSummary();
    }

    // =========================================================================
    // 1. Graphics & Renderer Section
    // =========================================================================
    private void setupRendererSection(@NonNull View view, LayoutInflater inflater) {
        PackageManager pm = requireContext().getPackageManager();
        boolean hasVulkan = GpuUtils.checkVulkanSupport(pm);
        GpuUtils.GLInfo glInfo = GpuUtils.getGlInfo();

        // Vulkan Diagnostic Hero Banner
        ImageView vulkanIcon = view.findViewById(R.id.settings_vulkan_icon);
        TextView vulkanTitle = view.findViewById(R.id.settings_vulkan_title);
        TextView vulkanDesc = view.findViewById(R.id.settings_vulkan_desc);

        if (hasVulkan) {
            vulkanIcon.setImageResource(R.drawable.ic_ui_check_circle);
            vulkanIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.accent)));
            vulkanTitle.setText(R.string.settings_vulkan_badge_supported);
            vulkanTitle.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.accent));
            vulkanDesc.setText(getString(R.string.settings_vulkan_badge_supported_desc, glInfo.renderer));
        } else {
            vulkanIcon.setImageResource(R.drawable.ic_ui_info);
            vulkanIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.info)));
            vulkanTitle.setText(R.string.settings_vulkan_badge_unsupported);
            vulkanTitle.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary));
            vulkanDesc.setText(R.string.settings_vulkan_badge_unsupported_desc);
        }

        LinearLayout container = view.findViewById(R.id.settings_renderer_container);
        RendererCache cache = RendererCache.getCompatibleRenderers(requireContext());
        int rIndex = cache.rendererIds.indexOf(LauncherPreferences.PREF_RENDERER);
        String currentRendererName = rIndex >= 0 ? cache.rendererDisplayNames[rIndex] : LauncherPreferences.PREF_RENDERER;

        mRendererSummary = addRow(inflater, container, R.drawable.ic_px_image_renderer,
                R.string.settings_renderer_select_title, currentRendererName,
                v -> showRendererDialog(cache)).summary;

        // Resolution Scaling Slider
        int currentRes = (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100);
        if (currentRes < 25) currentRes = 100;
        int[] resPresets = new int[] { 50, 75, 100, 125 };
        addSliderRow(inflater, container, R.drawable.ic_px_resolution,
                R.string.settings_resolution_title,
                getString(R.string.mcl_setting_subtitle_resolution_scaler),
                25, 150, currentRes, "%", resPresets, progress -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putInt("resolutionRatio", progress).apply();
                    LauncherPreferences.PREF_SCALE_FACTOR = progress / 100f;
                });

        // Sustained Performance Switch (Nougat+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            addSwitchRow(inflater, container, R.drawable.ic_px_zap,
                    R.string.preference_sustained_performance_title,
                    R.string.preference_sustained_performance_description,
                    LauncherPreferences.PREF_SUSTAINED_PERFORMANCE,
                    (btn, isChecked) -> {
                        LauncherPreferences.DEFAULT_PREF.edit().putBoolean("sustainedPerformance", isChecked).apply();
                        LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = isChecked;
                    });
        }

        // Alternate Surface (SurfaceView)
        addSwitchRow(inflater, container, R.drawable.ic_px_viewport_expand,
                R.string.mcl_setting_title_use_surface_view,
                R.string.mcl_setting_subtitle_use_surface_view,
                LauncherPreferences.PREF_USE_ALTERNATE_SURFACE,
                (btn, isChecked) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putBoolean("alternate_surface", isChecked).apply();
                    LauncherPreferences.PREF_USE_ALTERNATE_SURFACE = isChecked;
                });

        // Force VSync
        addSwitchRow(inflater, container, R.drawable.ic_px_alt_sliders,
                R.string.preference_force_vsync_title,
                R.string.preference_force_vsync_description,
                LauncherPreferences.PREF_FORCE_VSYNC,
                (btn, isChecked) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putBoolean("force_vsync", isChecked).apply();
                    LauncherPreferences.PREF_FORCE_VSYNC = isChecked;
                });

        // Prefer System Driver for Zink on Adreno Vulkan
        if (hasVulkan && glInfo.isAdreno()) {
            addSwitchRow(inflater, container, R.drawable.ic_px_volcano,
                    R.string.preference_vulkan_driver_system_title,
                    R.string.preference_vulkan_driver_system_description,
                    LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER,
                    (btn, isChecked) -> {
                        LauncherPreferences.DEFAULT_PREF.edit().putBoolean("zinkPreferSystemDriver", isChecked).apply();
                        LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER = isChecked;
                    });
        }
    }

    private void showRendererDialog(RendererCache cache) {
        int selectedIndex = cache.rendererIds.indexOf(LauncherPreferences.PREF_RENDERER);
        if (selectedIndex < 0) selectedIndex = 0;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_renderer_select_title)
                .setSingleChoiceItems(cache.rendererDisplayNames, selectedIndex, (dialog, which) -> {
                    String selectedId = cache.rendererIds.get(which);
                    LauncherPreferences.DEFAULT_PREF.edit().putString("renderer", selectedId).apply();
                    LauncherPreferences.PREF_RENDERER = selectedId;
                    if (mRendererSummary != null) {
                        mRendererSummary.setText(cache.rendererDisplayNames[which]);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // =========================================================================
    // 2. Java & Memory Section
    // =========================================================================
    private void setupJavaSection(@NonNull View view, LayoutInflater inflater) {
        int totalRam = Tools.getTotalDeviceMemory(requireContext());
        int freeRam = Tools.getFreeDeviceMemory(requireContext());
        int minRam = 256;
        int maxRam;
        if (is32BitsDevice() || totalRam < 2048) {
            maxRam = Math.min(1024, totalRam);
        } else {
            maxRam = totalRam - (totalRam < 3064 ? 800 : 1024);
        }

        int currentRam = LauncherPreferences.PREF_RAM_ALLOCATION;
        if (currentRam > maxRam) currentRam = maxRam;
        if (currentRam < minRam) currentRam = minRam;

        TextView ramStatus = view.findViewById(R.id.settings_ram_status);
        TextView ramBadge = view.findViewById(R.id.settings_ram_badge);
        SeekBar ramSeekBar = view.findViewById(R.id.settings_ram_seekbar);

        ramStatus.setText(getString(R.string.settings_ram_status, currentRam, freeRam, totalRam));
        ramBadge.setText(currentRam + " MB");

        ramSeekBar.setMax(maxRam - minRam);
        ramSeekBar.setProgress(currentRam - minRam);

        ramSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int ram = minRam + progress;
                ramBadge.setText(ram + " MB");
                ramStatus.setText(getString(R.string.settings_ram_status, ram, freeRam, totalRam));
                if (fromUser) {
                    saveRam(ram);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int ram = minRam + seekBar.getProgress();
                saveRam(ram);
            }
        });

        int finalMaxRam = maxRam;
        bindRamPreset(view.findViewById(R.id.settings_ram_preset_15), 1536, minRam, maxRam, ramSeekBar, ramBadge, ramStatus, freeRam, totalRam);
        bindRamPreset(view.findViewById(R.id.settings_ram_preset_25), 2560, minRam, maxRam, ramSeekBar, ramBadge, ramStatus, freeRam, totalRam);
        bindRamPreset(view.findViewById(R.id.settings_ram_preset_40), 4096, minRam, maxRam, ramSeekBar, ramBadge, ramStatus, freeRam, totalRam);
        bindRamPreset(view.findViewById(R.id.settings_ram_preset_max), finalMaxRam, minRam, maxRam, ramSeekBar, ramBadge, ramStatus, freeRam, totalRam);

        LinearLayout container = view.findViewById(R.id.settings_java_container);

        // Java Runtime Manager (Multi-RT)
        String runtimeName = Tools.isValidString(LauncherPreferences.PREF_DEFAULT_RUNTIME)
                ? LauncherPreferences.PREF_DEFAULT_RUNTIME : getString(R.string.use_global_default);
        mRuntimeSummary = addRow(inflater, container, R.drawable.ic_px_runtime_mgr,
                R.string.multirt_title, runtimeName,
                v -> openMultiRTDialog()).summary;

        // JVM Arguments
        String currentJvm = Tools.isValidString(LauncherPreferences.PREF_CUSTOM_JAVA_ARGS)
                ? LauncherPreferences.PREF_CUSTOM_JAVA_ARGS : getString(R.string.mcl_setting_subtitle_javaargs);
        mJvmSummary = addRow(inflater, container, R.drawable.ic_px_console,
                R.string.settings_jvm_title, currentJvm,
                v -> showJvmArgumentsDialog()).summary;

        // Java Sandbox
        addSwitchRow(inflater, container, R.drawable.ic_px_java_run,
                R.string.mcl_setting_java_sandbox,
                R.string.mcl_setting_java_sandbox_subtitle,
                LauncherPreferences.PREF_JAVA_SANDBOX,
                (btn, isChecked) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putBoolean("java_sandbox", isChecked).apply();
                    LauncherPreferences.PREF_JAVA_SANDBOX = isChecked;
                });
    }

    private void saveRam(int ram) {
        LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", ram).apply();
        LauncherPreferences.PREF_RAM_ALLOCATION = ram;
    }

    private void bindRamPreset(Button btn, int targetRam, int minRam, int maxRam,
                              SeekBar seekBar, TextView badge, TextView status, int freeRam, int totalRam) {
        if (btn == null) return;
        if (targetRam > maxRam) {
            btn.setEnabled(false);
            btn.setAlpha(0.4f);
            return;
        }
        btn.setOnClickListener(v -> {
            seekBar.setProgress(targetRam - minRam);
            badge.setText(targetRam + " MB");
            status.setText(getString(R.string.settings_ram_status, targetRam, freeRam, totalRam));
            saveRam(targetRam);
        });
    }

    private void refreshRuntimeSummary() {
        if (mRuntimeSummary == null || !isAdded()) return;
        String runtime = LauncherPreferences.PREF_DEFAULT_RUNTIME;
        mRuntimeSummary.setText(Tools.isValidString(runtime) ? runtime : getString(R.string.use_global_default));
    }

    private void openMultiRTDialog() {
        if (mDialogScreen == null) {
            mDialogScreen = new MultiRTConfigDialog();
            mDialogScreen.prepare(getContext(), mVmInstallLauncher);
        }
        mDialogScreen.show();
    }

    private void showJvmArgumentsDialog() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = getResources().getDimensionPixelSize(R.dimen.mojo_space_4);
        layout.setPadding(pad, pad, pad, pad);

        EditText input = new EditText(requireContext());
        input.setText(LauncherPreferences.PREF_CUSTOM_JAVA_ARGS);
        input.setHint(R.string.editor_jvm_hint);
        layout.addView(input);

        Button presetsBtn = new Button(requireContext(), null, R.attr.buttonStyle);
        presetsBtn.setText(R.string.settings_jvm_presets);
        presetsBtn.setOnClickListener(v -> showJvmPresetsPicker(input));
        layout.addView(presetsBtn);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_jvm_title)
                .setView(layout)
                .setPositiveButton(R.string.global_save_and_exit, (dialog, which) -> {
                    String newArgs = input.getText().toString().trim();
                    LauncherPreferences.DEFAULT_PREF.edit().putString("javaArgs", newArgs).apply();
                    LauncherPreferences.PREF_CUSTOM_JAVA_ARGS = newArgs;
                    if (mJvmSummary != null) {
                        mJvmSummary.setText(newArgs.isEmpty()
                                ? getString(R.string.mcl_setting_subtitle_javaargs) : newArgs);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showJvmPresetsPicker(EditText target) {
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
                .setItems(presetNames, (dialog, which) -> target.setText(presetValues[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // =========================================================================
    // 3. Controls & Input Section
    // =========================================================================
    private void setupControlsSection(@NonNull View view, LayoutInflater inflater) {
        LinearLayout container = view.findViewById(R.id.settings_controls_container);

        // Virtual Mouse Speed Slider
        int currentMouseSpeed = (int) (LauncherPreferences.PREF_MOUSESPEED * 100);
        int[] speedPresets = new int[] { 50, 100, 150, 200 };
        addSliderRow(inflater, container, R.drawable.ic_px_speed,
                R.string.settings_mouse_speed_title,
                getString(R.string.mcl_setting_subtitle_mousespeed),
                25, 300, currentMouseSpeed, "%", speedPresets, progress -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putInt("mousespeed", progress).apply();
                    LauncherPreferences.PREF_MOUSESPEED = progress / 100f;
                });

        // Virtual Mouse Scale Slider
        int currentMouseScale = (int) (LauncherPreferences.PREF_MOUSESCALE * 100);
        int[] scalePresets = new int[] { 75, 100, 125, 150 };
        addSliderRow(inflater, container, R.drawable.ic_px_pointer_size,
                R.string.settings_mouse_scale_title,
                getString(R.string.mcl_setting_subtitle_mousescale),
                50, 200, currentMouseScale, "%", scalePresets, progress -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putInt("mousescale", progress).apply();
                    LauncherPreferences.PREF_MOUSESCALE = progress / 100f;
                });

        // Touch Gestures Switch
        addSwitchRow(inflater, container, R.drawable.ic_px_nogestures,
                R.string.mcl_disable_gestures,
                R.string.mcl_disable_gestures_subtitle,
                LauncherPreferences.PREF_DISABLE_GESTURES,
                (btn, isChecked) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putBoolean("disableGestures", isChecked).apply();
                    LauncherPreferences.PREF_DISABLE_GESTURES = isChecked;
                });

        // Double-Tap Swap Hand Switch
        addSwitchRow(inflater, container, R.drawable.ic_px_switch_arrows_x,
                R.string.mcl_disable_swap_hand,
                R.string.mcl_disable_swap_hand_subtitle,
                LauncherPreferences.PREF_DISABLE_SWAP_HAND,
                (btn, isChecked) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putBoolean("disableDoubleTap", isChecked).apply();
                    LauncherPreferences.PREF_DISABLE_SWAP_HAND = isChecked;
                });

        // Start With Virtual Mouse Switch
        addSwitchRow(inflater, container, R.drawable.ic_px_virtual_mouse,
                R.string.preference_mouse_start_title,
                R.string.preference_mouse_start_description,
                LauncherPreferences.PREF_VIRTUAL_MOUSE_START,
                (btn, isChecked) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putBoolean("mouse_start", isChecked).apply();
                    LauncherPreferences.PREF_VIRTUAL_MOUSE_START = isChecked;
                });

        // Custom On-Screen Controls Editor
        addRow(inflater, container, R.drawable.ic_px_control_size,
                R.string.settings_custom_controls_title,
                R.string.settings_custom_controls_summary,
                v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));

        // Gamepad / Controller Remapper
        addRow(inflater, container, R.drawable.ic_ui_gamepad,
                R.string.settings_gamepad_title,
                R.string.settings_gamepad_summary,
                v -> Tools.swapFragment(requireActivity(), GamepadMapperFragment.class,
                        GamepadMapperFragment.TAG, null));
    }

    // =========================================================================
    // 4. Launcher & Game Behavior Section
    // =========================================================================
    private void setupBehaviorSection(@NonNull View view, LayoutInflater inflater) {
        LinearLayout container = view.findViewById(R.id.settings_behavior_container);

        // Rapid Startup Check
        addSwitchRow(inflater, container, R.drawable.ic_px_zap,
                R.string.settings_rapid_start_title,
                R.string.settings_rapid_start_summary,
                LauncherPreferences.PREF_RAPID_START,
                (btn, isChecked) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putBoolean("fastStartupCheck", isChecked).apply();
                    LauncherPreferences.PREF_RAPID_START = isChecked;
                });

        // Verify Game Files
        addSwitchRow(inflater, container, R.drawable.ic_px_hash,
                R.string.settings_verify_files_title,
                R.string.settings_verify_files_summary,
                LauncherPreferences.PREF_VERIFY_FILES,
                (btn, isChecked) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putBoolean("checkGameFiles", isChecked).apply();
                    LauncherPreferences.PREF_VERIFY_FILES = isChecked;
                });

        // Memory Warning Dialog
        addSwitchRow(inflater, container, R.drawable.ic_px_memory_warning,
                R.string.preference_show_memory_warning_title,
                R.string.preference_show_memory_warning_summary,
                LauncherPreferences.PREF_SHOW_MEMORY_WARNING_DIALOG,
                (btn, isChecked) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putBoolean("showMemoryWarning", isChecked).apply();
                    LauncherPreferences.PREF_SHOW_MEMORY_WARNING_DIALOG = isChecked;
                });

        // Theme / Appearance
        mThemeSummary = addRow(inflater, container, R.drawable.ic_ui_appearance,
                R.string.settings_theme_title,
                R.string.settings_theme_summary,
                v -> showThemeDialog()).summary;
        refreshThemeSummary();

        // Language
        addRow(inflater, container, R.drawable.ic_px_translate,
                R.string.settings_language_title,
                R.string.settings_language_summary,
                v -> showLanguageDialog());

        // Notifications
        mNotificationSummary = addRow(inflater, container, R.drawable.ic_ui_notifications,
                R.string.settings_notifications_title,
                R.string.settings_notifications_allowed,
                v -> onNotificationRowClicked()).summary;
        refreshNotificationSummary();
    }

    // =========================================================================
    // 5. System & Diagnostics Section
    // =========================================================================
    private void setupSystemSection(@NonNull View view, LayoutInflater inflater) {
        LinearLayout container = view.findViewById(R.id.settings_system_container);

        // Hardware Diagnostics
        int totalRam = Tools.getTotalDeviceMemory(requireContext());
        String infoSummary = getString(R.string.settings_device_info_summary,
                Build.MANUFACTURER + " " + Build.MODEL,
                Build.VERSION.RELEASE,
                Build.CPU_ABI,
                totalRam);
        addRow(inflater, container, R.drawable.ic_ui_info,
                R.string.settings_device_info_title, infoSummary,
                v -> showHardwareDialog());

        // Open Game Folder
        addRow(inflater, container, R.drawable.ic_ui_folder,
                R.string.settings_open_folder_title,
                R.string.settings_open_folder_summary,
                v -> openGameFolder());

        // Share Logs
        addRow(inflater, container, R.drawable.ic_ui_share,
                R.string.settings_share_logs_title,
                R.string.settings_share_logs_summary,
                v -> Tools.shareLog(requireContext()));

        // About MojoRemake
        addRow(inflater, container, R.drawable.ic_ui_info,
                R.string.settings_about_title,
                R.string.settings_about_summary,
                v -> ((LauncherActivity) requireActivity()).openAbout());

        // GitHub Homepage
        addRow(inflater, container, R.drawable.ic_ui_external,
                R.string.settings_github_title,
                R.string.settings_github_summary,
                v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
    }

    private void showHardwareDialog() {
        Context context = requireContext();
        PackageManager pm = context.getPackageManager();
        GpuUtils.GLInfo glInfo = GpuUtils.getGlInfo();
        int totalRam = Tools.getTotalDeviceMemory(context);
        int freeRam = Tools.getFreeDeviceMemory(context);
        boolean hasVulkan = GpuUtils.checkVulkanSupport(pm);
        long storageBytes = new File(Tools.DIR_GAME_NEW).getUsableSpace();

        String message = "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")\n"
                + "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Architecture: " + Build.CPU_ABI + (is32BitsDevice() ? " (32-bit)" : " (64-bit)") + "\n\n"
                + "Total RAM: " + totalRam + " MB\n"
                + "Available RAM: ~" + freeRam + " MB\n"
                + "Allocated RAM: " + LauncherPreferences.PREF_RAM_ALLOCATION + " MB\n\n"
                + "GPU Vendor: " + glInfo.vendor + "\n"
                + "GPU Renderer: " + glInfo.renderer + "\n"
                + "OpenGL ES: " + glInfo.glesMajorVersion + ".x\n"
                + "Vulkan: " + (hasVulkan ? "Supported" : "Not supported") + "\n\n"
                + "Storage Free: " + (storageBytes > 0 ? Formatter.formatFileSize(context, storageBytes) : "Unknown");

        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_device_info_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
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

    // =========================================================================
    // UI Helpers: Action Rows, Switch Rows, Slider Rows
    // =========================================================================
    private Row addRow(LayoutInflater inflater, LinearLayout container, @DrawableRes int icon,
                       @StringRes int title, @StringRes int summary, View.OnClickListener listener) {
        return addRow(inflater, container, icon, title, getString(summary), listener);
    }

    private Row addRow(LayoutInflater inflater, LinearLayout container, @DrawableRes int icon,
                       @StringRes int title, String summary, View.OnClickListener listener) {
        View row = inflater.inflate(R.layout.view_settings_row, container, false);
        ((ImageView) row.findViewById(R.id.settings_row_icon)).setImageResource(icon);
        TextView titleView = row.findViewById(R.id.settings_row_title);
        TextView summaryView = row.findViewById(R.id.settings_row_summary);
        titleView.setText(title);
        summaryView.setText(summary);
        row.setOnClickListener(listener);
        row.setContentDescription(getString(title) + ". " + summary);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.mojo_space_1);
        container.addView(row, params);
        return new Row(titleView, summaryView);
    }

    private View addSwitchRow(LayoutInflater inflater, LinearLayout container, @DrawableRes int icon,
                              @StringRes int title, @StringRes int summary, boolean initialValue,
                              CompoundButton.OnCheckedChangeListener listener) {
        View row = inflater.inflate(R.layout.view_settings_switch_row, container, false);
        ((ImageView) row.findViewById(R.id.settings_switch_icon)).setImageResource(icon);
        TextView titleView = row.findViewById(R.id.settings_switch_title);
        TextView summaryView = row.findViewById(R.id.settings_switch_summary);
        SwitchCompat switchCompat = row.findViewById(R.id.settings_switch_widget);

        titleView.setText(title);
        summaryView.setText(summary);
        switchCompat.setChecked(initialValue);
        switchCompat.setOnCheckedChangeListener(listener);

        row.setOnClickListener(v -> switchCompat.toggle());
        row.setContentDescription(getString(title) + ". " + getString(summary));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.mojo_space_1);
        container.addView(row, params);
        return row;
    }

    private interface OnSliderChangedListener {
        void onProgress(int value);
    }

    private View addSliderRow(LayoutInflater inflater, LinearLayout container, @DrawableRes int icon,
                              @StringRes int title, String summaryText, int min, int max, int initialValue,
                              String unitSuffix, int[] presets, OnSliderChangedListener listener) {
        View row = inflater.inflate(R.layout.view_settings_slider_row, container, false);
        ((ImageView) row.findViewById(R.id.settings_slider_icon)).setImageResource(icon);
        TextView titleView = row.findViewById(R.id.settings_slider_title);
        TextView summaryView = row.findViewById(R.id.settings_slider_summary);
        TextView badgeView = row.findViewById(R.id.settings_slider_value_badge);
        SeekBar seekBar = row.findViewById(R.id.settings_slider_seekbar);
        LinearLayout presetsLayout = row.findViewById(R.id.settings_slider_presets);

        titleView.setText(title);
        summaryView.setText(summaryText);
        badgeView.setText(initialValue + unitSuffix);

        seekBar.setMax(max - min);
        seekBar.setProgress(Math.max(0, initialValue - min));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = min + progress;
                badgeView.setText(val + unitSuffix);
                if (fromUser) {
                    listener.onProgress(val);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                int val = min + sb.getProgress();
                listener.onProgress(val);
            }
        });

        if (presets != null && presets.length > 0) {
            presetsLayout.setVisibility(View.VISIBLE);
            for (int preset : presets) {
                Button btn = new Button(requireContext(), null, R.attr.buttonStyle);
                btn.setText(preset + unitSuffix);
                btn.setTextSize(12);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        getResources().getDimensionPixelSize(R.dimen.mojo_touch_target), 1f);
                lp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.mojo_space_1));
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> {
                    seekBar.setProgress(preset - min);
                    badgeView.setText(preset + unitSuffix);
                    listener.onProgress(preset);
                });
                presetsLayout.addView(btn);
            }
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.mojo_space_1);
        container.addView(row, params);
        return row;
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
