package git.artdeell.mojo.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
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

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.LauncherActivity;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.modding.modpacks.ModItemAdapter;
import git.artdeell.mojo.modding.modpacks.api.CommonApi;
import git.artdeell.mojo.modding.modpacks.api.ModpackApi;
import git.artdeell.mojo.modding.modpacks.models.SearchFilters;
import git.artdeell.mojo.progresskeeper.ProgressKeeper;
import git.artdeell.mojo.progresskeeper.TaskCountListener;
import git.artdeell.mojo.ui.ScreenTitleProvider;
import git.artdeell.mojo.utils.profiles.VersionSelectorDialog;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Browse tab. Keeps the existing Modrinth/CurseForge pipeline but adds debounced search, explicit
 * loading/empty/error states and a clear action.
 */
public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback,
        ScreenTitleProvider {
    public static final String TAG = "SearchModFragment";

    private static final long SEARCH_DEBOUNCE_MS = 350L;
    private static final String STATE_QUERY = "browse_query";

    private final SearchFilters mSearchFilters = new SearchFilters();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private EditText mSearchEditText;
    private ImageButton mClearButton;
    private RecyclerView mRecyclerView;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private View mStateContainer;
    private ImageView mStateIcon;
    private TextView mStateTitle;
    private TextView mStateBody;
    private Button mStateAction;
    private Button mImportButton;
    private ModpackApi mModpackApi;
    private TaskCountListener mTaskCountListener;
    private boolean mRestoringQuery;

    private final Runnable mSearchRunnable = () -> {
        if (mSearchEditText != null) searchMods(mSearchEditText.getText().toString());
    };

    private final ActivityResultLauncher<String> mImportLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                Context context = getContext();
                if (context == null) return;
                ContentResolver contentResolver = context.getContentResolver();
                MojoApplication.sExecutorService.execute(() ->
                        performLocalInstall(uri, context, contentResolver));
            });

    public SearchModFragment() {
        super(R.layout.fragment_mod_search);
        mSearchFilters.isModpack = true;
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
        mModItemAdapter = new ModItemAdapter(getResources(), mModpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);

        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mClearButton = view.findViewById(R.id.search_mod_clear);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerView = view.findViewById(R.id.search_mod_list);
        mStateContainer = view.findViewById(R.id.browse_state);
        mStateIcon = view.findViewById(R.id.browse_state_icon);
        mStateTitle = view.findViewById(R.id.browse_state_title);
        mStateBody = view.findViewById(R.id.browse_state_body);
        mStateAction = view.findViewById(R.id.browse_state_action);
        mImportButton = view.findViewById(R.id.mineButton_import_local_modpack);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setAdapter(mModItemAdapter);

        view.findViewById(R.id.search_mod_filter).setOnClickListener(v -> displayFilterDialog());
        mClearButton.setOnClickListener(v -> {
            mSearchEditText.setText("");
            searchMods("");
        });
        mImportButton.setOnClickListener(v -> beginLocalImport());

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                mHandler.removeCallbacks(mSearchRunnable);
                searchMods(mSearchEditText.getText().toString());
                hideKeyboard();
                mSearchEditText.clearFocus();
                return true;
            }
            return false;
        });
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mClearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (mRestoringQuery) return;
                mHandler.removeCallbacks(mSearchRunnable);
                mHandler.postDelayed(mSearchRunnable, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        mTaskCountListener = taskCount -> {
            Tools.runOnUiThread(() -> {
                if (!isAdded() || getView() == null || mImportButton == null) return;
                mImportButton.setEnabled(taskCount == 0);
            });
            return false;
        };
        ProgressKeeper.addTaskCountListener(mTaskCountListener);

        showState(R.drawable.ic_ui_browse, R.string.browse_empty_title,
                R.string.browse_empty_body, 0, null);

        String restoredQuery = savedInstanceState == null ? ""
                : savedInstanceState.getString(STATE_QUERY, "");
        mRestoringQuery = true;
        mSearchEditText.setText(restoredQuery);
        mSearchEditText.setSelection(restoredQuery.length());
        mRestoringQuery = false;
        mClearButton.setVisibility(restoredQuery.isEmpty() ? View.GONE : View.VISIBLE);
        searchMods(restoredQuery);

        if (getActivity() instanceof LauncherActivity) {
            LauncherActivity activity = (LauncherActivity) getActivity();
            if (activity.consumeBrowseImportRequest()) {
                view.post(this::beginLocalImport);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSearchEditText != null) {
            outState.putString(STATE_QUERY, mSearchEditText.getText().toString());
        }
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mSearchRunnable);
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        ProgressKeeper.removeTaskCountListener(mTaskCountListener);
        mModItemAdapter.cancelSearch();
        mRecyclerView.setAdapter(null);
        super.onDestroyView();
    }

    /** Called by the shell when another screen asks to import a local modpack. */
    public void beginLocalImport() {
        if (!isAdded() || getView() == null || mImportButton == null) return;
        if (ProgressKeeper.getTaskCount() > 0) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
            return;
        }
        mImportLauncher.launch("*/*");
    }

    private void performLocalInstall(Uri uri, Context context, ContentResolver contentResolver) {
        String fileName = Tools.getFileName(context, uri);
        if (fileName == null || fileName.isEmpty()) {
            Tools.runOnUiThread(() -> Toast.makeText(context, R.string.browse_import_empty,
                    Toast.LENGTH_LONG).show());
            return;
        }
        File outFile = new File(Tools.DIR_CACHE, fileName + ".cf");
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, -1,
                R.string.multirt_progress_caching);
        try (InputStream inputStream = contentResolver.openInputStream(uri);
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

    private void searchMods(String name) {
        if (!isAdded() || getView() == null || mModItemAdapter == null) return;
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mStateContainer.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
        mSearchFilters.name = name == null ? "" : name;
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    @Override
    public void onSearchFinished() {
        if (!isAdded() || getView() == null) return;
        mSearchProgressBar.setVisibility(View.GONE);
        mStateContainer.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSearchError(int error) {
        if (!isAdded() || getView() == null) return;
        mSearchProgressBar.setVisibility(View.GONE);
        if (error == ERROR_NO_RESULTS) {
            showState(R.drawable.ic_ui_search, R.string.browse_no_results_title,
                    R.string.browse_no_results_body, 0, null);
        } else {
            String query = mSearchEditText == null ? "" : mSearchEditText.getText().toString();
            showState(R.drawable.ic_ui_warning, R.string.browse_error_title,
                    R.string.browse_error_body, R.string.browse_retry,
                    v -> searchMods(query));
        }
    }

    private void showState(int icon, int title, int body, int actionText,
                           View.OnClickListener action) {
        mRecyclerView.setVisibility(View.GONE);
        mStateContainer.setVisibility(View.VISIBLE);
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

    private void displayFilterDialog() {
        if (!isAdded() || getView() == null) return;
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            TextView selectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button selectVersionButton = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button applyButton = dialog.findViewById(R.id.search_mod_apply_filters);
            Button clearButton = dialog.findViewById(R.id.search_mod_clear_filters);
            if (selectedVersion == null || selectVersionButton == null || applyButton == null) return;

            selectedVersion.setText(mSearchFilters.mcVersion == null ? "" : mSearchFilters.mcVersion);
            selectVersionButton.setOnClickListener(v -> VersionSelectorDialog.open(v.getContext(), true,
                    (id, snapshot) -> selectedVersion.setText(id)));
            applyButton.setOnClickListener(v -> {
                String version = selectedVersion.getText().toString().trim();
                mSearchFilters.mcVersion = version.isEmpty() ? null : version;
                searchMods(mSearchEditText.getText().toString());
                dialogInterface.dismiss();
            });
            if (clearButton != null) {
                clearButton.setOnClickListener(v -> {
                    mSearchFilters.mcVersion = null;
                    selectedVersion.setText("");
                    searchMods(mSearchEditText.getText().toString());
                    dialogInterface.dismiss();
                });
            }
        });
        dialog.show();
    }

    private void hideKeyboard() {
        if (!isAdded() || mSearchEditText == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), 0);
    }
}
