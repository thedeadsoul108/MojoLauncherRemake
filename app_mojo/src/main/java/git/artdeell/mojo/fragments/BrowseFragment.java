package git.artdeell.mojo.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.kdt.mcgui.ProgressLayout;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.content.ContentInstaller;
import git.artdeell.mojo.content.ContentProjectAdapter;
import git.artdeell.mojo.content.InstanceEnvironment;
import git.artdeell.mojo.content.ModrinthClient;
import git.artdeell.mojo.content.ModrinthModels;
import git.artdeell.mojo.content.ModrinthQuery;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.modding.modpacks.api.CommonApi;
import git.artdeell.mojo.modding.modpacks.imagecache.ModIconCache;
import git.artdeell.mojo.progresskeeper.ProgressKeeper;
import git.artdeell.mojo.ui.ScreenTitleProvider;
import git.artdeell.mojo.ui.UiUtils;
import git.artdeell.mojo.utils.profiles.VersionSelectorDialog;

/** Modrinth content hub: search, filter, inspect and install every supported project type. */
public class BrowseFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "BrowseFragment";

    private static final long SEARCH_DEBOUNCE_MS = 350L;
    private static final int PAGE_SIZE = 20;

    private static final String STATE_TYPE = "browse_type";
    private static final String STATE_QUERY = "browse_query";
    private static final String STATE_VERSION = "browse_version";
    private static final String STATE_LOADER = "browse_loader";
    private static final String STATE_CATEGORY = "browse_category";
    private static final String STATE_ENVIRONMENT = "browse_environment";
    private static final String STATE_SORT = "browse_sort";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mSearchRunnable = () -> startSearch(false, false);

    private EditText mSearchEditText;
    private ImageButton mClearButton;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mRefresh;
    private LinearLayout mSkeleton;
    private LinearLayout mStateContainer;
    private ImageView mStateIcon;
    private TextView mStateTitle;
    private TextView mStateBody;
    private Button mStateAction;
    private ProgressBar mProgress;
    private Spinner mSortSpinner;
    private TextView mInstanceText;
    private Button mInstanceAction;
    private ContentProjectAdapter mAdapter;
    private ModrinthClient mClient;
    private ModIconCache mIconCache;
    private CommonApi mModpackApi;

    private volatile Instance mInstance;
    private String mInstanceGameVersion;
    private String mInstanceLoader;
    private String mProjectType = ModrinthQuery.TYPE_MOD;
    private String mGameVersion;
    private String mLoader;
    private String mCategory;
    private String mEnvironment;
    private String mSortIndex = ModrinthQuery.INDEX_RELEVANCE;
    private String mQuery = "";
    private int mOffset;
    private boolean mLoading;
    private boolean mEnd;
    private boolean mRestoredState;
    private boolean mRestoringQuery;
    private boolean mSuppressSortCallback;
    private long mGeneration;

    private final ActivityResultLauncher<String> mImportLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                Context context = getContext();
                if (context == null) return;
                ContentResolver resolver = context.getContentResolver();
                Context appContext = context.getApplicationContext();
                MojoApplication.sExecutorService.execute(() ->
                        performLocalInstall(uri, appContext, resolver));
            });

    public BrowseFragment() {
        super(R.layout.fragment_mod_search);
    }

    @Override
    public int getScreenTitle() {
        return R.string.nav_browse;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mModpackApi = new CommonApi(context.getString(R.string.curseforge_api_key));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        readSavedState(savedInstanceState);
        bindViews(view);
        setupTypeChips();
        setupSearch(view);
        setupSort(view);
        setupSkeletons();

        if (!mRestoredState) mQuery = "";
        mRestoringQuery = true;
        mSearchEditText.setText(mQuery);
        mSearchEditText.setSelection(mQuery.length());
        mRestoringQuery = false;
        mClearButton.setVisibility(mQuery.isEmpty() ? View.GONE : View.VISIBLE);
        updateTypeChips();
        updateInstanceBanner();

        if (getActivity() instanceof LauncherActivity
                && ((LauncherActivity) getActivity()).consumeBrowseImportRequest()) {
            view.post(this::beginLocalImport);
        }

        loadClientAndInstance();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TYPE, mProjectType);
        outState.putString(STATE_QUERY, mSearchEditText == null
                ? mQuery : mSearchEditText.getText().toString());
        outState.putString(STATE_VERSION, mGameVersion);
        outState.putString(STATE_LOADER, mLoader);
        outState.putString(STATE_CATEGORY, mCategory);
        outState.putString(STATE_ENVIRONMENT, mEnvironment);
        outState.putString(STATE_SORT, mSortIndex);
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mSearchRunnable);
        mGeneration++;
        mLoading = false;
        if (mAdapter != null) {
            mAdapter.submit(new ArrayList<>(), false);
            if (mRecyclerView != null) mRecyclerView.setAdapter(null);
        }
        mAdapter = null;
        super.onDestroyView();
    }

    private void readSavedState(@Nullable Bundle state) {
        if (state == null) return;
        mRestoredState = true;
        mProjectType = valueOr(state.getString(STATE_TYPE), ModrinthQuery.TYPE_MOD);
        mQuery = valueOr(state.getString(STATE_QUERY), "");
        mGameVersion = state.getString(STATE_VERSION);
        mLoader = state.getString(STATE_LOADER);
        mCategory = state.getString(STATE_CATEGORY);
        mEnvironment = state.getString(STATE_ENVIRONMENT);
        mSortIndex = valueOr(state.getString(STATE_SORT), ModrinthQuery.INDEX_RELEVANCE);
    }

    private void bindViews(View view) {
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mClearButton = view.findViewById(R.id.search_mod_clear);
        mRecyclerView = view.findViewById(R.id.search_mod_list);
        mRefresh = view.findViewById(R.id.browse_refresh);
        mSkeleton = view.findViewById(R.id.browse_skeleton);
        mStateContainer = view.findViewById(R.id.browse_state);
        mStateIcon = view.findViewById(R.id.browse_state_icon);
        mStateTitle = view.findViewById(R.id.browse_state_title);
        mStateBody = view.findViewById(R.id.browse_state_body);
        mStateAction = view.findViewById(R.id.browse_state_action);
        mProgress = view.findViewById(R.id.browse_progress);
        mSortSpinner = view.findViewById(R.id.browse_sort_spinner);
        mInstanceText = view.findViewById(R.id.browse_instance_text);
        mInstanceAction = view.findViewById(R.id.browse_instance_action);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || mLoading || mEnd || mAdapter == null) return;
                LinearLayoutManager layout = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layout == null) return;
                int last = layout.findLastVisibleItemPosition();
                if (last >= mAdapter.getItemCount() - 4 && mAdapter.projectCount() > 0) {
                    startSearch(false, true);
                }
            }
        });

        mRefresh.setOnRefreshListener(() -> startSearch(true, false));
        mRefresh.setColorSchemeResources(R.color.accent);

        mInstanceAction.setOnClickListener(v -> {
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) getActivity()).selectRootTab(LauncherActivity.TAB_INSTANCES);
            }
        });
        view.findViewById(R.id.search_mod_filter).setOnClickListener(v -> showFilters());
        view.findViewById(R.id.browse_import_button).setOnClickListener(v -> beginLocalImport());
        mClearButton.setOnClickListener(v -> {
            mSearchEditText.setText("");
            mHandler.removeCallbacks(mSearchRunnable);
            startSearch(false, false);
        });
    }

    private void setupSearch(View view) {
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mClearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (mRestoringQuery) return;
                mQuery = s.toString();
                mHandler.removeCallbacks(mSearchRunnable);
                mHandler.postDelayed(mSearchRunnable, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                mHandler.removeCallbacks(mSearchRunnable);
                startSearch(false, false);
                hideKeyboard();
                mSearchEditText.clearFocus();
                return true;
            }
            return false;
        });
    }

    private void setupSort(View view) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.browse_sort_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSortSpinner.setAdapter(adapter);
        int selection = sortIndex(mSortIndex);
        mSuppressSortCallback = true;
        mSortSpinner.setSelection(selection, false);
        mSuppressSortCallback = false;
        mSortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mSuppressSortCallback) return;
                String index = sortValue(position);
                if (index.equals(mSortIndex)) return;
                mSortIndex = index;
                startSearch(false, false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupTypeChips() {
        bindTypeChip(R.id.browse_type_mods, ModrinthQuery.TYPE_MOD);
        bindTypeChip(R.id.browse_type_modpacks, ModrinthQuery.TYPE_MODPACK);
        bindTypeChip(R.id.browse_type_resourcepacks, ModrinthQuery.TYPE_RESOURCEPACK);
        bindTypeChip(R.id.browse_type_shaders, ModrinthQuery.TYPE_SHADER);
        bindTypeChip(R.id.browse_type_datapacks, ModrinthQuery.TYPE_DATAPACK);
    }

    private void bindTypeChip(int viewId, String type) {
        View chip = requireView().findViewById(viewId);
        chip.setOnClickListener(v -> {
            if (type.equals(mProjectType)) return;
            mProjectType = type;
            applyInstanceDefaults();
            updateTypeChips();
            updateInstanceBanner();
            startSearch(false, false);
        });
    }

    private void updateTypeChips() {
        View view = getView();
        if (view == null) return;
        view.findViewById(R.id.browse_type_mods).setSelected(ModrinthQuery.TYPE_MOD.equals(mProjectType));
        view.findViewById(R.id.browse_type_modpacks).setSelected(
                ModrinthQuery.TYPE_MODPACK.equals(mProjectType));
        view.findViewById(R.id.browse_type_resourcepacks).setSelected(
                ModrinthQuery.TYPE_RESOURCEPACK.equals(mProjectType));
        view.findViewById(R.id.browse_type_shaders).setSelected(
                ModrinthQuery.TYPE_SHADER.equals(mProjectType));
        view.findViewById(R.id.browse_type_datapacks).setSelected(
                ModrinthQuery.TYPE_DATAPACK.equals(mProjectType));
    }

    private void setupSkeletons() {
        if (mSkeleton.getChildCount() > 0) return;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < 5; i++) {
            mSkeleton.addView(inflater.inflate(R.layout.view_content_project_skeleton,
                    mSkeleton, false));
        }
    }

    private void loadClientAndInstance() {
        showSkeletons(true);
        mProgress.setVisibility(View.GONE);
        showState(0, 0, 0, 0, null);
        MojoApplication.sExecutorService.execute(() -> {
            ModrinthClient client = new ModrinthClient();
            ModIconCache iconCache = new ModIconCache();
            Instance instance = null;
            String gameVersion = null;
            String loader = null;
            try {
                instance = Instances.loadSelectedInstance();
                if (instance != null) {
                    gameVersion = InstanceEnvironment.gameVersionOf(instance);
                    loader = InstanceEnvironment.loaderOf(instance);
                }
            } catch (RuntimeException ignored) {
                // Browsing still works without instance-derived filters.
            }
            final Instance finalInstance = instance;
            final String finalGameVersion = gameVersion;
            final String finalLoader = loader;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                mClient = client;
                mIconCache = iconCache;
                mInstance = finalInstance;
                mInstanceGameVersion = finalGameVersion;
                mInstanceLoader = finalLoader;
                if (!mRestoredState) applyInstanceDefaults();
                updateTypeChips();
                updateInstanceBanner();
                setupAdapter();
                startSearch(false, false);
            });
        });
    }

    private void setupAdapter() {
        if (mIconCache == null || mAdapter != null) return;
        mAdapter = new ContentProjectAdapter(mIconCache, this::openDetails);
        mRecyclerView.setAdapter(mAdapter);
    }

    private void applyInstanceDefaults() {
        mGameVersion = InstanceEnvironment.usesInstanceFilters(mProjectType)
                ? mInstanceGameVersion : null;
        mLoader = InstanceEnvironment.usesLoaderFilter(mProjectType) ? mInstanceLoader : null;
        mCategory = null;
        mEnvironment = null;
    }

    private void updateInstanceBanner() {
        if (mInstanceText == null || !isAdded()) return;
        if (ModrinthQuery.TYPE_MODPACK.equals(mProjectType)) {
            mInstanceText.setText(R.string.browse_modpack_hint);
            mInstanceAction.setVisibility(View.GONE);
            return;
        }
        mInstanceAction.setVisibility(View.VISIBLE);
        if (mInstance == null) {
            mInstanceText.setText(R.string.browse_no_instance);
        } else {
            mInstanceText.setText(getString(R.string.browse_installing_into,
                    UiUtils.displayName(requireContext(), mInstance)));
        }
    }

    private void startSearch(boolean forceRefresh, boolean append) {
        if (!isAdded() || getView() == null || mClient == null || mAdapter == null) {
            if (mRefresh != null) mRefresh.setRefreshing(false);
            return;
        }
        if (append && (mLoading || mEnd)) return;
        if (!append) {
            mGeneration++;
            mOffset = 0;
            mEnd = false;
        }
        final long generation = mGeneration;
        final int offset = mOffset;
        final String query = mSearchEditText == null ? mQuery
                : mSearchEditText.getText().toString();
        final String type = mProjectType;
        final String version = mGameVersion;
        final String loader = mLoader;
        final String category = mCategory;
        final String environment = mEnvironment;
        final String index = mSortIndex;

        mLoading = true;
        mHandler.removeCallbacks(mSearchRunnable);
        if (append) {
            mAdapter.setLoadingMore(true);
        } else {
            mSkeleton.setVisibility(mAdapter.projectCount() == 0 ? View.VISIBLE : View.GONE);
            mRecyclerView.setVisibility(mAdapter.projectCount() == 0 ? View.GONE : View.VISIBLE);
            mStateContainer.setVisibility(View.GONE);
            mProgress.setVisibility(mAdapter.projectCount() == 0 ? View.GONE : View.VISIBLE);
        }

        MojoApplication.sExecutorService.execute(() -> {
            ModrinthModels.SearchResponse response = null;
            Throwable error = null;
            try {
                response = mClient.search(query, type, version, loader, category,
                        environment, index, PAGE_SIZE, offset, forceRefresh);
            } catch (Throwable t) {
                error = t;
            }
            final ModrinthModels.SearchResponse finalResponse = response;
            final Throwable finalError = error;
            Tools.runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded() || getView() == null
                        || mAdapter == null) {
                    return;
                }
                mLoading = false;
                mAdapter.setLoadingMore(false);
                mRefresh.setRefreshing(false);
                mProgress.setVisibility(View.GONE);
                showSkeletons(false);
                if (finalError != null) {
                    if (!append && mAdapter.projectCount() == 0) {
                        showSearchError(finalError);
                    } else {
                        Toast.makeText(requireContext(), errorMessage(finalError),
                                Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                List<ModrinthModels.Project> hits = finalResponse.hits;
                mAdapter.submit(hits, append);
                mOffset = offset + hits.size();
                mEnd = hits.isEmpty() || mOffset >= finalResponse.totalHits;
                if (mAdapter.projectCount() == 0) {
                    showEmptyState();
                } else {
                    mStateContainer.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void showSearchError(Throwable error) {
        mRecyclerView.setVisibility(View.GONE);
        if (error instanceof ModrinthClient.RateLimitedException) {
            showState(R.drawable.ic_ui_warning, R.string.browse_rate_limited_title,
                    R.string.browse_rate_limited_body, R.string.browse_retry,
                    v -> startSearch(false, false));
        } else {
            showState(R.drawable.ic_ui_warning, R.string.browse_error_title,
                    R.string.browse_error_body, R.string.browse_retry,
                    v -> startSearch(false, false));
        }
    }

    private void showEmptyState() {
        mRecyclerView.setVisibility(View.GONE);
        showState(R.drawable.ic_ui_browse, R.string.browse_no_results_title,
                R.string.browse_no_results_body, 0, null);
    }

    private void showSkeletons(boolean show) {
        if (mSkeleton == null) return;
        mSkeleton.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            mRecyclerView.setVisibility(View.GONE);
            mStateContainer.setVisibility(View.GONE);
        }
    }

    private void showState(int icon, int title, int body, int actionText,
                           View.OnClickListener action) {
        if (mStateContainer == null) return;
        mStateContainer.setVisibility(View.VISIBLE);
        if (title == 0) {
            mStateContainer.setVisibility(View.GONE);
            return;
        }
        mStateIcon.setImageResource(icon);
        mStateTitle.setText(title);
        mStateBody.setText(body);
        if (actionText == 0 || action == null) {
            mStateAction.setVisibility(View.GONE);
            mStateAction.setOnClickListener(null);
        } else {
            mStateAction.setVisibility(View.VISIBLE);
            mStateAction.setText(actionText);
            mStateAction.setOnClickListener(action);
        }
    }

    private void showFilters() {
        if (!isAdded() || getView() == null) return;
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_browse_filters, null);
        TextView versionValue = content.findViewById(R.id.browse_filter_version_value);
        Spinner loader = content.findViewById(R.id.browse_filter_loader);
        Spinner environment = content.findViewById(R.id.browse_filter_environment);
        EditText category = content.findViewById(R.id.browse_filter_category);

        versionValue.setText(mGameVersion == null ? getString(R.string.browse_version_any)
                : mGameVersion);
        category.setText(mCategory == null ? "" : mCategory);

        ArrayAdapter<CharSequence> loaderAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.browse_loader_options,
                android.R.layout.simple_spinner_item);
        loaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        loader.setAdapter(loaderAdapter);
        loader.setSelection(loaderIndex(mLoader), false);

        ArrayAdapter<CharSequence> envAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.browse_environment_options,
                android.R.layout.simple_spinner_item);
        envAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        environment.setAdapter(envAdapter);
        environment.setSelection(environmentIndex(mEnvironment), false);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.browse_filter_title)
                .setView(content)
                .setPositiveButton(R.string.browse_filter_apply, null)
                .setNeutralButton(R.string.browse_filter_clear, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            content.findViewById(R.id.browse_filter_version_button).setOnClickListener(v ->
                    VersionSelectorDialog.open(v.getContext(), true,
                            (id, snapshot) -> versionValue.setText(id)));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String version = versionValue.getText().toString().trim();
                mGameVersion = version.isEmpty() ? null : version;
                mLoader = loaderValue(loader.getSelectedItemPosition());
                mEnvironment = environmentValue(environment.getSelectedItemPosition());
                String categoryText = category.getText().toString().trim();
                mCategory = categoryText.isEmpty() ? null : categoryText;
                dialog.dismiss();
                startSearch(false, false);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                applyInstanceDefaults();
                dialog.dismiss();
                startSearch(false, false);
            });
        });
        dialog.show();
    }

    private void openDetails(@NonNull ModrinthModels.Project project) {
        if (!isAdded() || getView() == null || mClient == null) return;
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_content_project, null);
        ImageView icon = content.findViewById(R.id.content_detail_icon);
        TextView title = content.findViewById(R.id.content_detail_title);
        TextView author = content.findViewById(R.id.content_detail_author);
        TextView stats = content.findViewById(R.id.content_detail_stats);
        TextView description = content.findViewById(R.id.content_detail_description);
        TextView body = content.findViewById(R.id.content_detail_body);
        TextView targetText = content.findViewById(R.id.content_detail_target_text);
        Button targetAction = content.findViewById(R.id.content_detail_target_action);
        Spinner versionSpinner = content.findViewById(R.id.content_detail_version);
        ProgressBar progress = content.findViewById(R.id.content_detail_progress);
        TextView error = content.findViewById(R.id.content_detail_error);

        title.setText(project.title);
        author.setText(project.author == null ? getString(R.string.browse_unknown_author)
                : project.author);
        stats.setText(getString(R.string.browse_download_count, NumberFormat
                .getIntegerInstance(Locale.getDefault())
                .format(Math.max(0, project.downloads))));
        description.setText(project.description);
        loadDetailIcon(icon, project);

        final boolean modpack = ModrinthQuery.TYPE_MODPACK.equals(mProjectType);
        if (modpack) {
            targetText.setText(R.string.browse_detail_modpack_note);
            targetAction.setVisibility(View.GONE);
        } else if (mInstance == null) {
            targetText.setText(R.string.browse_no_instance);
            targetAction.setVisibility(View.VISIBLE);
        } else {
            targetText.setText(getString(R.string.browse_installing_into,
                    UiUtils.displayName(requireContext(), mInstance)));
            targetAction.setVisibility(View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(content)
                .setPositiveButton(modpack ? R.string.browse_install_modpack
                        : R.string.browse_install, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button install = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            install.setEnabled(false);
            targetAction.setOnClickListener(v -> {
                dialog.dismiss();
                if (getActivity() instanceof LauncherActivity) {
                    ((LauncherActivity) getActivity())
                            .selectRootTab(LauncherActivity.TAB_INSTANCES);
                }
            });

            final String versionFilter = InstanceEnvironment.usesInstanceFilters(mProjectType)
                    ? mGameVersion : null;
            final String loaderFilter = InstanceEnvironment.usesLoaderFilter(mProjectType)
                    ? mLoader : null;
            MojoApplication.sExecutorService.execute(() -> {
                ModrinthModels.Project detail = project;
                List<ModrinthModels.Version> versions = new ArrayList<>();
                Throwable loadError = null;
                try {
                    ModrinthModels.Project fetched = mClient.project(project.idOrSlug(), false);
                    if (fetched != null) detail = fetched;
                    versions = mClient.versions(project.idOrSlug(), versionFilter,
                            loaderFilter, false);
                } catch (Throwable t) {
                    loadError = t;
                }
                final ModrinthModels.Project finalDetail = detail;
                final List<ModrinthModels.Version> finalVersions = versions;
                final Throwable finalError = loadError;
                Tools.runOnUiThread(() -> {
                    if (!dialog.isShowing() || !isAdded()) return;
                    progress.setVisibility(View.GONE);
                    if (finalError != null) {
                        error.setText(finalError instanceof ModrinthClient.RateLimitedException
                                ? R.string.browse_rate_limited_body
                                : R.string.browse_detail_versions_error);
                        error.setVisibility(View.VISIBLE);
                        return;
                    }
                    String bodyText = plainText(finalDetail.body);
                    if (!bodyText.isEmpty()) {
                        body.setText(bodyText);
                        body.setVisibility(View.VISIBLE);
                    }
                    if (finalVersions.isEmpty()) {
                        error.setText(R.string.browse_detail_no_versions);
                        error.setVisibility(View.VISIBLE);
                        return;
                    }
                    List<String> labels = new ArrayList<>(finalVersions.size());
                    for (ModrinthModels.Version version : finalVersions) {
                        String label = version.versionNumber;
                        if (label == null || label.isEmpty()) label = version.name;
                        if (label == null || label.isEmpty()) label = version.id;
                        labels.add(label);
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_spinner_item, labels);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    versionSpinner.setAdapter(adapter);
                    versionSpinner.setVisibility(View.VISIBLE);
                    install.setEnabled(modpack || mInstance != null);
                    install.setOnClickListener(v -> {
                        int position = versionSpinner.getSelectedItemPosition();
                        if (position < 0 || position >= finalVersions.size()) return;
                        ModrinthModels.Version version = finalVersions.get(position);
                        dialog.dismiss();
                        if (modpack) installModpack(finalDetail, version);
                        else installContent(finalDetail, version);
                    });
                });
            });
        });
        dialog.show();
    }

    private void loadDetailIcon(ImageView icon, ModrinthModels.Project project) {
        if (mIconCache == null || project.iconUrl == null || project.iconUrl.isEmpty()) return;
        final String tag = project.cacheTag();
        icon.setTag(tag);
        mIconCache.getImage(bitmap -> {
            if (!tag.equals(icon.getTag())) {
                bitmap.recycle();
                return;
            }
            icon.setPadding(0, 0, 0, 0);
            icon.setImageTintList(null);
            icon.setImageBitmap(bitmap);
        }, tag, project.iconUrl);
    }

    private void installContent(ModrinthModels.Project project, ModrinthModels.Version version) {
        if (!isAdded() || getView() == null) return;
        if (mInstance == null) {
            Toast.makeText(requireContext(), R.string.browse_install_no_instance,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (ProgressKeeper.hasProgressKey(ContentInstaller.progressKey(version))) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }
        final Instance instance = mInstance;
        Toast.makeText(requireContext(),
                getString(R.string.browse_install_started, project.title),
                Toast.LENGTH_SHORT).show();
        MojoApplication.sExecutorService.execute(() -> {
            Throwable error = null;
            try {
                new ContentInstaller().install(project, version, instance);
            } catch (Throwable t) {
                error = t;
            }
            final Throwable finalError = error;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                if (finalError == null) {
                    Toast.makeText(requireContext(),
                            getString(R.string.browse_installed, project.title),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), R.string.browse_install_failed,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void installModpack(ModrinthModels.Project project, ModrinthModels.Version version) {
        if (!isAdded() || getView() == null) return;
        if (ProgressKeeper.hasProgressKey(ProgressLayout.INSTALL_MODPACK)) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(requireContext(),
                getString(R.string.browse_install_started, project.title),
                Toast.LENGTH_SHORT).show();
        MojoApplication.sExecutorService.execute(() -> {
            Throwable error = null;
            try {
                File pack = new ContentInstaller().downloadModpack(project, version);
                mModpackApi.installLocalModpack(project.title, pack, project.cacheTag());
            } catch (Throwable t) {
                error = t;
            }
            final Throwable finalError = error;
            if (finalError != null) {
                Tools.runOnUiThread(() -> {
                    if (!isAdded() || getView() == null) return;
                    Toast.makeText(requireContext(), R.string.browse_import_failed,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Called by the shell when another screen asks to import a local modpack. */
    public void beginLocalImport() {
        if (!isAdded() || getView() == null) return;
        if (ProgressKeeper.getTaskCount() > 0) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
            return;
        }
        mImportLauncher.launch("*/*");
    }

    private void performLocalInstall(Uri uri, Context context, ContentResolver resolver) {
        String fileName = Tools.getFileName(context, uri);
        if (fileName == null || fileName.isEmpty()) {
            Tools.runOnUiThread(() -> Toast.makeText(context, R.string.browse_import_empty,
                    Toast.LENGTH_LONG).show());
            return;
        }
        File outFile = new File(Tools.DIR_CACHE, fileName + ".cf");
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, -1,
                R.string.multirt_progress_caching);
        try (InputStream inputStream = resolver.openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(outFile)) {
            if (inputStream == null) {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                return;
            }
            IOUtils.copy(inputStream, outputStream);
            outputStream.flush();
        } catch (IOException e) {
            Tools.showErrorRemote(context.getString(R.string.browse_import_failed), e);
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            outFile.delete();
            return;
        }
        try {
            mModpackApi.installLocalModpack(fileName, outFile, null);
        } catch (IOException e) {
            Tools.showErrorRemote(context.getString(R.string.browse_import_failed), e);
        } finally {
            outFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    private String errorMessage(Throwable error) {
        return getString(error instanceof ModrinthClient.RateLimitedException
                ? R.string.browse_rate_limited_body : R.string.browse_error_body);
    }

    private void hideKeyboard() {
        if (!isAdded() || mSearchEditText == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), 0);
    }

    private static String sortValue(int position) {
        switch (position) {
            case 1: return ModrinthQuery.INDEX_DOWNLOADS;
            case 2: return ModrinthQuery.INDEX_FOLLOWS;
            case 3: return ModrinthQuery.INDEX_NEWEST;
            case 4: return ModrinthQuery.INDEX_UPDATED;
            default: return ModrinthQuery.INDEX_RELEVANCE;
        }
    }

    private static int sortIndex(String value) {
        if (ModrinthQuery.INDEX_DOWNLOADS.equals(value)) return 1;
        if (ModrinthQuery.INDEX_FOLLOWS.equals(value)) return 2;
        if (ModrinthQuery.INDEX_NEWEST.equals(value)) return 3;
        if (ModrinthQuery.INDEX_UPDATED.equals(value)) return 4;
        return 0;
    }

    private static int loaderIndex(String loader) {
        if ("fabric".equals(loader)) return 1;
        if ("quilt".equals(loader)) return 2;
        if ("forge".equals(loader)) return 3;
        if ("neoforge".equals(loader)) return 4;
        return 0;
    }

    private static String loaderValue(int position) {
        switch (position) {
            case 1: return "fabric";
            case 2: return "quilt";
            case 3: return "forge";
            case 4: return "neoforge";
            default: return null;
        }
    }

    private static int environmentIndex(String environment) {
        if ("client".equals(environment)) return 1;
        if ("server".equals(environment)) return 2;
        return 0;
    }

    private static String environmentValue(int position) {
        if (position == 1) return "client";
        if (position == 2) return "server";
        return null;
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String plainText(String markdown) {
        if (markdown == null) return "";
        String text = markdown
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")
                .replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("[*_`>]", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (text.length() > 1200) text = text.substring(0, 1200);
        return text;
    }
}
