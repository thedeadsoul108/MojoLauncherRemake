package git.artdeell.mojo.fragments;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import git.artdeell.mojo.ui.ScreenTitleProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visible account manager. Login itself is still handled by the hidden AccountSpinner and the
 * existing auth fragments; this screen only switches and removes already-stored accounts.
 */
public class AccountManagerFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "AccountManagerFragment";

    private LinearLayout mList;
    private View mEmpty;
    private View mLoading;
    private final List<Account> mAccounts = new ArrayList<>();
    private final Map<Account, Bitmap> mFaces = new IdentityHashMap<>();
    private int mSelectedIndex;
    private boolean mLoadPending;

    public AccountManagerFragment() {
        super(R.layout.fragment_account_manager);
    }

    @Override
    public int getScreenTitle() {
        return R.string.accounts_title;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mList = view.findViewById(R.id.accounts_list);
        mEmpty = view.findViewById(R.id.accounts_empty);
        mLoading = view.findViewById(R.id.accounts_loading);

        view.findViewById(R.id.accounts_add).setOnClickListener(v -> addAccount());
        view.findViewById(R.id.accounts_empty_add).setOnClickListener(v -> addAccount());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAccounts();
    }

    private void addAccount() {
        Tools.swapFragment(requireActivity(), SelectAuthFragment.class,
                SelectAuthFragment.TAG, null);
    }

    private void loadAccounts() {
        if (mLoadPending) return;
        mLoadPending = true;
        if (mAccounts.isEmpty()) {
            mLoading.setVisibility(View.VISIBLE);
        }
        MojoApplication.sExecutorService.execute(() -> {
            List<Account> accounts = null;
            int selected = 0;
            Map<Account, Bitmap> faces = new IdentityHashMap<>();
            try {
                Accounts loaded = Accounts.load();
                accounts = new ArrayList<>(loaded.accounts);
                selected = Accounts.hasSelectedAccount() ? loaded.selectionIndex : -1;
                for (Account account : accounts) {
                    Bitmap face = account.getSkinFace();
                    if (face != null) faces.put(account, face);
                }
            } catch (IOException e) {
                // Handled on the UI thread below.
            }
            List<Account> finalAccounts = accounts;
            int finalSelected = selected;
            Map<Account, Bitmap> finalFaces = faces;
            Tools.runOnUiThread(() -> {
                mLoadPending = false;
                if (!isAdded() || getView() == null) return;
                mLoading.setVisibility(View.GONE);
                if (finalAccounts == null) {
                    showEmpty();
                    Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_LONG).show();
                    return;
                }
                mAccounts.clear();
                mAccounts.addAll(finalAccounts);
                mFaces.clear();
                mFaces.putAll(finalFaces);
                mSelectedIndex = finalSelected;
                renderAccounts();
            });
        });
    }

    private void showEmpty() {
        mList.setVisibility(View.GONE);
        mEmpty.setVisibility(View.VISIBLE);
    }

    private void renderAccounts() {
        mList.removeAllViews();
        if (mAccounts.isEmpty()) {
            showEmpty();
            return;
        }
        mEmpty.setVisibility(View.GONE);
        mList.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < mAccounts.size(); i++) {
            Account account = mAccounts.get(i);
            View row = inflater.inflate(R.layout.view_account_row, mList, false);
            bindRow(row, account, i);
            mList.addView(row);
            if (i < mAccounts.size() - 1) {
                View divider = new View(requireContext());
                divider.setBackgroundColor(androidx.core.content.ContextCompat.getColor(
                        requireContext(), R.color.border));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                params.setMarginStart(getResources().getDimensionPixelSize(R.dimen.mojo_instance_icon_small)
                        + getResources().getDimensionPixelSize(R.dimen.mojo_space_3));
                mList.addView(divider, params);
            }
        }
    }

    private void bindRow(View row, Account account, int index) {
        ImageView avatar = row.findViewById(R.id.account_row_avatar);
        TextView name = row.findViewById(R.id.account_row_name);
        TextView type = row.findViewById(R.id.account_row_type);
        TextView active = row.findViewById(R.id.account_row_active);
        Button use = row.findViewById(R.id.account_row_use);
        ImageButton skin = row.findViewById(R.id.account_row_skin);
        ImageButton delete = row.findViewById(R.id.account_row_delete);

        String username = account.username == null || account.username.isEmpty()
                ? getString(R.string.account_chip_no_account) : account.username;
        name.setText(username);
        type.setText(authTypeLabel(account.authType));
        Bitmap face = mFaces.get(account);
        if (face != null) {
            avatar.setImageTintList(null);
            avatar.setImageBitmap(face);
        } else {
            avatar.setImageTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary)));
            avatar.setImageResource(R.drawable.ic_ui_person);
        }
        avatar.setContentDescription(getString(R.string.accounts_avatar_description, username));

        boolean selected = index == mSelectedIndex;
        active.setVisibility(selected ? View.VISIBLE : View.GONE);
        use.setVisibility(selected ? View.GONE : View.VISIBLE);
        use.setOnClickListener(v -> switchAccount(index));
        skin.setContentDescription(getString(R.string.accounts_skin_description, username));
        skin.setOnClickListener(v -> openSkinEditor(index, account));
        delete.setOnClickListener(v -> confirmDelete(account, username));
        row.setContentDescription(username + ". " + type.getText());
    }

    private void openSkinEditor(int index, Account account) {
        Tools.swapFragment(requireActivity(), SkinFragment.newInstance(index, account), SkinFragment.TAG);
    }

    private String authTypeLabel(AuthType type) {
        if (type == AuthType.MICROSOFT) return getString(R.string.auth_select_microsoft);
        if (type == AuthType.ELY_BY) return getString(R.string.auth_select_elyby);
        return getString(R.string.auth_select_local);
    }

    private void switchAccount(int index) {
        if (index < 0 || index >= mAccounts.size()) return;
        Account account = mAccounts.get(index);
        Accounts.setCurrent(account);
        mSelectedIndex = index;
        renderAccounts();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        if (getActivity() instanceof LauncherActivity) {
            ((LauncherActivity) getActivity()).refreshAccountChip();
        }
        Toast.makeText(requireContext(),
                getString(R.string.accounts_switch_toast, account.username), Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(Account account, String username) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.accounts_remove_title)
                .setMessage(getString(R.string.accounts_remove_body, username))
                .setPositiveButton(R.string.accounts_remove, (dialog, which) -> deleteAccount(account))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void deleteAccount(Account account) {
        boolean wasSelected = mSelectedIndex >= 0 && mSelectedIndex < mAccounts.size()
                && mAccounts.get(mSelectedIndex) == account;
        MojoApplication.sExecutorService.execute(() -> {
            Accounts.delete(account);
            if (wasSelected) Accounts.clearCurrent();
            List<Account> remaining = null;
            int selected = 0;
            Map<Account, Bitmap> faces = new IdentityHashMap<>();
            try {
                Accounts loaded = Accounts.load();
                remaining = new ArrayList<>(loaded.accounts);
                if (wasSelected && !remaining.isEmpty()) {
                    selected = 0;
                    Accounts.setCurrent(remaining.get(0));
                } else {
                    selected = Accounts.hasSelectedAccount() ? loaded.selectionIndex : -1;
                }
                for (Account acc : remaining) {
                    Bitmap face = acc.getSkinFace();
                    if (face != null) faces.put(acc, face);
                }
            } catch (IOException ignored) {
                // Reported through the generic error below.
            }
            List<Account> finalRemaining = remaining;
            int finalSelected = selected;
            Map<Account, Bitmap> finalFaces = faces;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                if (finalRemaining != null) {
                    mAccounts.clear();
                    mAccounts.addAll(finalRemaining);
                    mFaces.clear();
                    mFaces.putAll(finalFaces);
                    mSelectedIndex = finalSelected;
                    renderAccounts();
                }
                Toast.makeText(requireContext(),
                        getString(R.string.accounts_removed, account.username), Toast.LENGTH_SHORT).show();
                if (getActivity() instanceof LauncherActivity) {
                    ((LauncherActivity) getActivity()).refreshAccountChip();
                }
                ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
            });
        });
    }
}
