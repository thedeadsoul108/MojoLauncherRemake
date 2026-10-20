package git.artdeell.mojo.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.modding.modpacks.imagecache.ImageReceiver;
import git.artdeell.mojo.modding.modpacks.imagecache.ModIconCache;
import git.artdeell.mojo.skin.MicrosoftSkinClient;
import git.artdeell.mojo.skin.SkinBodyRenderer;
import git.artdeell.mojo.skin.SkinDraftViewModel;
import git.artdeell.mojo.skin.SkinRemote;
import git.artdeell.mojo.skin.SkinStore;
import git.artdeell.mojo.skin.SkinValidator;
import git.artdeell.mojo.ui.ScreenTitleProvider;

/**
 * In-app skin editor. Microsoft accounts can apply skins through the Minecraft services API.
 * Ely.by has no public upload API, so skins are saved locally and managed on ely.by. Capes are
 * read-only everywhere because neither service exposes a public "equip cape" call.
 */
public class SkinFragment extends Fragment implements ScreenTitleProvider {
    public static final String TAG = "SkinFragment";
    private static final String ARG_INDEX = "account_index";
    private static final String ARG_PROFILE_ID = "account_profile_id";
    private static final String ARG_USERNAME = "account_username";

    private ImageView mAvatar;
    private TextView mAccountName;
    private TextView mAccountType;
    private ImageView mPreview;
    private ProgressBar mPreviewLoading;
    private TextView mPreviewEmpty;
    private Button mClassic;
    private Button mSlim;
    private TextView mSource;
    private Button mChoose;
    private Button mFromUrl;
    private Button mCopyPlayer;
    private Button mReset;
    private Button mApply;
    private TextView mStatus;
    private TextView mCapeNote;
    private LinearLayout mCapeList;
    private Button mManageElyby;

    private static ModIconCache sIconCache;

    private SkinDraftViewModel mDraft;
    private ModIconCache mIconCache;
    private Account mAccount;
    private int mAccountIndex = -1;
    private int mGeneration;
    private int mPreviewGeneration;
    private int mPreviewNonce;
    private boolean mApplying;
    private MicrosoftSkinClient.Profile mProfile;
    private Bitmap mElyCapeBitmap;
    private ImageReceiver mPreviewReceiver;
    private ImageReceiver mAvatarReceiver;
    private ImageReceiver mCapeReceiver;
    private Context mAppContext;

    private final ActivityResultLauncher<String[]> mOpenSkin = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) loadPickedSkin(uri);
            });

    public SkinFragment() {
        super(R.layout.fragment_skin);
    }

    public static SkinFragment newInstance(int index, Account account) {
        SkinFragment fragment = new SkinFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_INDEX, index);
        args.putString(ARG_PROFILE_ID, account.profileId);
        args.putString(ARG_USERNAME, account.username);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getScreenTitle() {
        return R.string.skin_title;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mAppContext = context.getApplicationContext();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mAvatar = view.findViewById(R.id.skin_account_avatar);
        mAccountName = view.findViewById(R.id.skin_account_name);
        mAccountType = view.findViewById(R.id.skin_account_type);
        mPreview = view.findViewById(R.id.skin_preview);
        mPreviewLoading = view.findViewById(R.id.skin_preview_loading);
        mPreviewEmpty = view.findViewById(R.id.skin_preview_empty);
        mClassic = view.findViewById(R.id.skin_variant_classic);
        mSlim = view.findViewById(R.id.skin_variant_slim);
        mSource = view.findViewById(R.id.skin_source);
        mChoose = view.findViewById(R.id.skin_choose);
        mFromUrl = view.findViewById(R.id.skin_from_url);
        mCopyPlayer = view.findViewById(R.id.skin_copy_player);
        mReset = view.findViewById(R.id.skin_reset);
        mApply = view.findViewById(R.id.skin_apply);
        mStatus = view.findViewById(R.id.skin_status);
        mCapeNote = view.findViewById(R.id.skin_cape_note);
        mCapeList = view.findViewById(R.id.skin_cape_list);
        mManageElyby = view.findViewById(R.id.skin_manage_elyby);

        Bundle args = getArguments();
        mAccountIndex = args == null ? -1 : args.getInt(ARG_INDEX, -1);

        mDraft = new ViewModelProvider(this).get(SkinDraftViewModel.class);
        mIconCache = getIconCache();

        mClassic.setOnClickListener(v -> selectVariant("classic"));
        mSlim.setOnClickListener(v -> selectVariant("slim"));
        mChoose.setOnClickListener(v -> mOpenSkin.launch(new String[]{"image/png"}));
        mFromUrl.setOnClickListener(v -> showInput(SkinInputDialogFragment.MODE_URL));
        mCopyPlayer.setOnClickListener(v -> showInput(SkinInputDialogFragment.MODE_PLAYER));
        mReset.setOnClickListener(v -> resetToAccountSkin());
        mApply.setOnClickListener(v -> applySkin());
        mManageElyby.setOnClickListener(v ->
                Tools.openURL(requireActivity(), SkinRemote.ELY_MANAGE_URL));

        getParentFragmentManager().setFragmentResultListener(
                SkinInputDialogFragment.REQUEST_KEY, this, (key, bundle) -> {
                    int mode = bundle.getInt(SkinInputDialogFragment.RESULT_MODE,
                            SkinInputDialogFragment.MODE_URL);
                    String value = bundle.getString(SkinInputDialogFragment.RESULT_VALUE, "");
                    if (value == null || value.trim().isEmpty()) return;
                    if (mode == SkinInputDialogFragment.MODE_URL) {
                        loadFromUrl(value.trim());
                    } else {
                        loadFromPlayer(value.trim());
                    }
                });

        selectVariant(mDraft.hasSkin() ? mDraft.variant : "classic");
        refreshApplyState();
        if (mDraft.hasSkin()) {
            updatePreview();
        } else {
            showPreviewEmpty();
        }
        loadAccountAndRemote();
    }

    @Override
    public void onDestroyView() {
        mGeneration++;
        mPreviewGeneration++;
        if (mIconCache != null) {
            if (mPreviewReceiver != null) mIconCache.cancelImage(mPreviewReceiver);
            if (mAvatarReceiver != null) mIconCache.cancelImage(mAvatarReceiver);
            if (mCapeReceiver != null) mIconCache.cancelImage(mCapeReceiver);
        }
        mPreviewReceiver = null;
        mAvatarReceiver = null;
        mCapeReceiver = null;
        mAvatar = null;
        mAccountName = null;
        mAccountType = null;
        mPreview = null;
        mPreviewLoading = null;
        mPreviewEmpty = null;
        mClassic = null;
        mSlim = null;
        mSource = null;
        mChoose = null;
        mFromUrl = null;
        mCopyPlayer = null;
        mReset = null;
        mApply = null;
        mStatus = null;
        mCapeNote = null;
        mCapeList = null;
        mManageElyby = null;
        super.onDestroyView();
    }

    private void loadAccountAndRemote() {
        if (mAppContext == null) return;
        final int generation = ++mGeneration;
        final int index = mAccountIndex;
        final String expectedProfileId = getArguments() == null
                ? null : getArguments().getString(ARG_PROFILE_ID);
        final String expectedUsername = getArguments() == null
                ? null : getArguments().getString(ARG_USERNAME);

        MojoApplication.sExecutorService.execute(() -> {
            Account account = null;
            SkinStore.StoredSkin stored = null;
            Bitmap face = null;
            MicrosoftSkinClient.Profile profile = null;
            byte[] elySkin = null;
            Bitmap elyCape = null;
            Throwable failure = null;
            int failureRes = 0;
            try {
                Accounts accounts = Accounts.load();
                account = findAccount(accounts.accounts, index, expectedProfileId, expectedUsername);
                if (account == null) {
                    failureRes = R.string.skin_error_no_account;
                } else {
                    stored = SkinStore.load(mAppContext, account);
                    face = account.getSkinFace();
                    if (account.authType == AuthType.MICROSOFT) {
                        try {
                            profile = MicrosoftSkinClient.fetchProfile(account);
                        } catch (Exception ignored) {
                            // Keep the saved/local preview when the profile is unavailable.
                        }
                    } else if (account.authType == AuthType.ELY_BY) {
                        try {
                            elySkin = SkinRemote.download(SkinRemote.elySkin(account.username));
                            SkinValidator.validate(elySkin);
                        } catch (Exception ignored) {
                            elySkin = null;
                        }
                        try {
                            byte[] cape = SkinRemote.download(SkinRemote.elyCape(account.username));
                            if (cape != null && cape.length > 0) {
                                elyCape = BitmapFactory.decodeByteArray(cape, 0, cape.length);
                            }
                        } catch (Exception ignored) {
                            // The account may have no cape.
                        }
                    }
                }
            } catch (Exception e) {
                failure = e;
            }

            final Account loadedAccount = account;
            final SkinStore.StoredSkin loadedStored = stored;
            final Bitmap loadedFace = face;
            final MicrosoftSkinClient.Profile loadedProfile = profile;
            final byte[] loadedElySkin = elySkin;
            final Bitmap loadedElyCape = elyCape;
            final Throwable loadedFailure = failure;
            final int loadedFailureRes = failureRes;
            Tools.runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                if (loadedElyCape != null) mElyCapeBitmap = loadedElyCape;
                if (loadedAccount == null) {
                    if (loadedFailureRes != 0) {
                        showStatus(getString(loadedFailureRes), true);
                    } else if (loadedFailure != null) {
                        showStatus(describeError(loadedFailure), true);
                    }
                    showPreviewEmpty();
                    return;
                }
                mAccount = loadedAccount;
                mAccountName.setText(loadedAccount.username);
                mAccountType.setText(typeLabel(loadedAccount.authType));
                bindAvatar(loadedFace);
                mProfile = loadedProfile;

                if (!mDraft.hasSkin()) {
                    if (loadedStored != null) {
                        mDraft.set(loadedStored.png, loadedStored.variant,
                                loadedStored.source, "");
                        selectVariant(loadedStored.variant);
                    } else if (loadedElySkin != null) {
                        mDraft.set(loadedElySkin, "classic", "account", "");
                        selectVariant("classic");
                    } else if (loadedProfile != null) {
                        selectVariant(loadedProfile.skinVariant);
                    }
                }
                if (!mDraft.hasSkin() && loadedProfile != null) {
                    selectVariant(loadedProfile.skinVariant);
                }
                updateApplyLabel();
                renderCapes();
                updatePreview();
                refreshApplyState();
            });
        });
    }

    private static synchronized ModIconCache getIconCache() {
        if (sIconCache == null) {
            try {
                sIconCache = new ModIconCache();
            } catch (RuntimeException ignored) {
                sIconCache = null;
            }
        }
        return sIconCache;
    }

    private Account findAccount(List<Account> accounts, int index, String profileId, String username) {
        for (Account account : accounts) {
            if (profileId != null && profileId.equals(account.profileId)
                    && username != null && username.equals(account.username)) {
                return account;
            }
        }
        if (index >= 0 && index < accounts.size()) return accounts.get(index);
        for (Account account : accounts) {
            if (username != null && username.equals(account.username)) return account;
        }
        return null;
    }

    private String typeLabel(AuthType type) {
        if (type == AuthType.MICROSOFT) return getString(R.string.auth_select_microsoft);
        if (type == AuthType.ELY_BY) return getString(R.string.auth_select_elyby);
        return getString(R.string.auth_select_local);
    }

    private void bindAvatar(@Nullable Bitmap face) {
        if (mAvatar == null) return;
        if (face != null) {
            mAvatar.setImageBitmap(face);
            mAvatar.clearColorFilter();
            return;
        }
        mAvatar.setImageResource(R.drawable.ic_ui_person);
        mAvatar.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        if (mAccount != null && mAccount.authType == AuthType.MICROSOFT
                && SkinRemote.isUuid(mAccount.profileId) && mIconCache != null) {
            final int generation = mGeneration;
            final ImageReceiver receiver = bitmap -> {
                if (generation != mGeneration || !isAdded() || getView() == null || mAvatar == null) {
                    return;
                }
                if (bitmap != null) {
                    mAvatar.setImageBitmap(bitmap);
                    mAvatar.clearColorFilter();
                }
            };
            mAvatarReceiver = receiver;
            mIconCache.getImage(receiver, "skin-avatar-" + SkinRemote.normalizedUuid(mAccount.profileId),
                    SkinRemote.crafatarAvatar(mAccount.profileId, 100));
        }
    }

    private void selectVariant(String variant) {
        boolean slim = "slim".equals(variant);
        if (mDraft != null) mDraft.variant = slim ? "slim" : "classic";
        if (mClassic != null) mClassic.setSelected(!slim);
        if (mSlim != null) mSlim.setSelected(slim);
        if (mDraft != null && mDraft.hasSkin()) updatePreview();
    }

    private void updatePreview() {
        if (mPreview == null || mDraft == null) return;
        if (mDraft.hasSkin()) {
            if (mSource != null) mSource.setText(sourceLabel());
            renderLocalPreview(mDraft.png, "slim".equals(mDraft.variant));
        } else if (mAccount != null && mAccount.authType != AuthType.ELY_BY
                && SkinRemote.isUuid(mAccount.profileId)) {
            if (mSource != null) mSource.setText(R.string.skin_source_account);
            loadCrafatarPreview();
        } else {
            showPreviewEmpty();
        }
        refreshApplyState();
    }

    private void renderLocalPreview(byte[] png, boolean slim) {
        if (mPreview == null || mPreviewLoading == null) return;
        final int generation = ++mPreviewGeneration;
        mPreviewLoading.setVisibility(View.VISIBLE);
        if (mPreviewEmpty != null) mPreviewEmpty.setVisibility(View.GONE);
        final byte[] snapshot = png;
        MojoApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = SkinBodyRenderer.render(snapshot, slim, 7);
            Tools.runOnUiThread(() -> {
                if (generation != mPreviewGeneration || !isAdded() || getView() == null
                        || mPreview == null || mPreviewLoading == null) {
                    return;
                }
                mPreviewLoading.setVisibility(View.GONE);
                if (bitmap == null) {
                    showPreviewEmpty();
                } else {
                    mPreview.setImageBitmap(bitmap);
                    if (mPreviewEmpty != null) mPreviewEmpty.setVisibility(View.GONE);
                }
            });
        });
    }

    private void loadCrafatarPreview() {
        if (mPreview == null || mIconCache == null || mAccount == null) {
            showPreviewEmpty();
            return;
        }
        final int generation = ++mPreviewGeneration;
        mPreviewLoading.setVisibility(View.VISIBLE);
        if (mPreviewEmpty != null) mPreviewEmpty.setVisibility(View.GONE);
        if (mPreviewReceiver != null) mIconCache.cancelImage(mPreviewReceiver);
        final ImageReceiver receiver = bitmap -> {
            if (generation != mPreviewGeneration || !isAdded() || getView() == null
                    || mPreview == null || mPreviewLoading == null) {
                return;
            }
            mPreviewLoading.setVisibility(View.GONE);
            if (bitmap == null) {
                showPreviewEmpty();
            } else {
                mPreview.setImageBitmap(bitmap);
                if (mPreviewEmpty != null) mPreviewEmpty.setVisibility(View.GONE);
            }
        };
        mPreviewReceiver = receiver;
        String tag = "skin-body-" + SkinRemote.normalizedUuid(mAccount.profileId)
                + "-" + mPreviewNonce;
        mIconCache.getImage(receiver, tag,
                SkinRemote.crafatarBody(mAccount.profileId, 280));
    }

    private void showPreviewEmpty() {
        if (mPreview == null) return;
        mPreview.setImageDrawable(null);
        if (mPreviewLoading != null) mPreviewLoading.setVisibility(View.GONE);
        if (mPreviewEmpty != null) {
            mPreviewEmpty.setText(R.string.skin_preview_empty);
            mPreviewEmpty.setVisibility(View.VISIBLE);
        }
        if (mSource != null && (mDraft == null || !mDraft.hasSkin())) {
            mSource.setText(R.string.skin_source_none);
        }
    }

    private String sourceLabel() {
        if (mDraft == null) return "";
        switch (mDraft.source) {
            case "file":
                return getString(R.string.skin_source_file, mDraft.label);
            case "url":
                return getString(R.string.skin_source_url);
            case "player":
                return getString(R.string.skin_source_player, mDraft.label);
            case "account":
                return getString(R.string.skin_source_account);
            case "microsoft":
                return getString(R.string.skin_source_microsoft);
            case "elyby":
                return getString(R.string.skin_source_elyby);
            case "local":
                return getString(R.string.skin_source_local);
            default:
                return getString(R.string.skin_source_saved);
        }
    }

    private void showInput(int mode) {
        if (mAccount == null) {
            Toast.makeText(requireContext(), R.string.skin_error_no_account, Toast.LENGTH_SHORT).show();
            return;
        }
        SkinInputDialogFragment.newInstance(mode)
                .show(getParentFragmentManager(), "skin_input");
    }

    private void loadPickedSkin(Uri uri) {
        if (mAppContext == null) return;
        final int generation = mGeneration;
        MojoApplication.sExecutorService.execute(() -> {
            byte[] bytes = null;
            String name = null;
            Throwable error = null;
            try (InputStream input = mAppContext.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Could not open file");
                bytes = readSkin(input);
                SkinValidator.validate(bytes);
                name = Tools.getFileName(mAppContext, uri);
            } catch (Exception e) {
                error = e;
            }
            final byte[] loaded = bytes;
            final String loadedName = name;
            final Throwable loadedError = error;
            Tools.runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                if (loadedError != null) {
                    showStatus(describeError(loadedError), true);
                    return;
                }
                setDraft(loaded, "file", loadedName == null ? "" : loadedName);
            });
        });
    }

    private void loadFromUrl(String url) {
        if (mAppContext == null) return;
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            showStatus(getString(R.string.skin_error_url), true);
            return;
        }
        final int generation = mGeneration;
        MojoApplication.sExecutorService.execute(() -> {
            byte[] bytes = null;
            Throwable error = null;
            try {
                bytes = SkinRemote.download(url);
                SkinValidator.validate(bytes);
            } catch (Exception e) {
                error = e;
            }
            final byte[] loaded = bytes;
            final Throwable loadedError = error;
            Tools.runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                if (loadedError != null) {
                    showStatus(describeError(loadedError), true);
                } else {
                    setDraft(loaded, "url", "");
                }
            });
        });
    }

    private void loadFromPlayer(String player) {
        if (mAppContext == null || mAccount == null) return;
        final int generation = mGeneration;
        final Account account = mAccount;
        MojoApplication.sExecutorService.execute(() -> {
            byte[] bytes = null;
            Throwable error = null;
            try {
                bytes = SkinRemote.skinFromPlayer(account, player);
                SkinValidator.validate(bytes);
            } catch (Exception e) {
                error = e;
            }
            final byte[] loaded = bytes;
            final Throwable loadedError = error;
            Tools.runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                if (loadedError != null) {
                    showStatus(describeError(loadedError), true);
                } else {
                    setDraft(loaded, "player", player);
                }
            });
        });
    }

    private void setDraft(byte[] png, String source, String label) {
        if (png == null || mDraft == null) return;
        mDraft.set(png, mDraft.variant, source, label);
        if (mStatus != null) mStatus.setVisibility(View.GONE);
        updatePreview();
        refreshApplyState();
    }

    private void resetToAccountSkin() {
        if (mAccount == null || mAppContext == null) return;
        final Account account = mAccount;
        final int generation = mGeneration;
        MojoApplication.sExecutorService.execute(() -> {
            SkinStore.clear(mAppContext, account);
            Tools.runOnUiThread(() -> {
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                mDraft.clear();
                mPreviewNonce++;
                if (mStatus != null) mStatus.setVisibility(View.GONE);
                updateApplyLabel();
                loadAccountAndRemote();
            });
        });
    }

    private void updateApplyLabel() {
        if (mApply == null) return;
        if (mAccount == null) {
            mApply.setText(R.string.skin_apply);
        } else if (mAccount.authType == AuthType.MICROSOFT) {
            mApply.setText(R.string.skin_apply_microsoft);
        } else if (mAccount.authType == AuthType.ELY_BY) {
            mApply.setText(R.string.skin_apply_elyby);
        } else {
            mApply.setText(R.string.skin_apply_local);
        }
    }

    private void refreshApplyState() {
        boolean ready = !mApplying && mAccount != null && mDraft != null && mDraft.hasSkin();
        if (mApply != null) mApply.setEnabled(ready);
        boolean editable = !mApplying;
        if (mChoose != null) mChoose.setEnabled(editable);
        if (mFromUrl != null) mFromUrl.setEnabled(editable);
        if (mCopyPlayer != null) mCopyPlayer.setEnabled(editable);
        if (mReset != null) mReset.setEnabled(editable && mAccount != null);
        if (mClassic != null) mClassic.setEnabled(editable);
        if (mSlim != null) mSlim.setEnabled(editable);
    }

    private void applySkin() {
        if (mApplying || mAccount == null || mDraft == null || !mDraft.hasSkin()) return;
        final Account account = mAccount;
        final byte[] png = mDraft.png;
        final String variant = mDraft.variant;
        final int generation = mGeneration;
        mApplying = true;
        refreshApplyState();
        showStatus(getString(R.string.skin_applying), false);

        MojoApplication.sExecutorService.execute(() -> {
            Throwable error = null;
            MicrosoftSkinClient.Profile refreshedProfile = null;
            Bitmap refreshedFace = null;
            try {
                if (account.authType == AuthType.MICROSOFT) {
                    MicrosoftSkinClient.upload(account, png, variant);
                    SkinStore.save(mAppContext, account, png, variant, "microsoft");
                    try {
                        account.updateSkinFace();
                        Account reloaded = account.reload();
                        if (reloaded != null) refreshedFace = reloaded.getSkinFace();
                    } catch (Exception ignored) {
                        // The upload succeeded; an avatar refresh is best-effort.
                    }
                    try {
                        refreshedProfile = MicrosoftSkinClient.fetchProfile(account);
                    } catch (Exception ignored) {
                        // Keep the old cape list if the profile cannot be refreshed.
                    }
                } else {
                    String source = account.authType == AuthType.ELY_BY ? "elyby" : "local";
                    SkinStore.save(mAppContext, account, png, variant, source);
                }
            } catch (Exception e) {
                error = e;
            }
            final Throwable loadedError = error;
            final MicrosoftSkinClient.Profile loadedProfile = refreshedProfile;
            final Bitmap loadedFace = refreshedFace;
            Tools.runOnUiThread(() -> {
                mApplying = false;
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                refreshApplyState();
                if (loadedError != null) {
                    showStatus(describeError(loadedError), true);
                    return;
                }
                if (loadedProfile != null) {
                    mProfile = loadedProfile;
                    renderCapes();
                }
                if (loadedFace != null) bindAvatar(loadedFace);
                if (account.authType == AuthType.MICROSOFT) {
                    mDraft.source = "microsoft";
                    showStatus(getString(R.string.skin_saved_microsoft), false);
                } else if (account.authType == AuthType.ELY_BY) {
                    mDraft.source = "elyby";
                    mManageElyby.setVisibility(View.VISIBLE);
                    showStatus(getString(R.string.skin_saved_elyby), false);
                } else {
                    mDraft.source = "local";
                    showStatus(getString(R.string.skin_saved_local), false);
                }
                mSource.setText(sourceLabel());
                mPreviewNonce++;
            });
        });
    }

    private void renderCapes() {
        if (mCapeList == null || mCapeNote == null || mAccount == null) return;
        mCapeList.removeAllViews();
        mManageElyby.setVisibility(
                mAccount.authType == AuthType.ELY_BY ? View.VISIBLE : View.GONE);
        if (mAccount.authType == AuthType.MICROSOFT) {
            mCapeNote.setText(R.string.skin_cape_note_microsoft);
            if (mProfile == null) {
                addCapeMessage(R.string.skin_cape_unavailable);
            } else if (mProfile.capes.isEmpty()) {
                addCapeMessage(R.string.skin_cape_none);
            } else {
                for (MicrosoftSkinClient.Cape cape : mProfile.capes) {
                    String name = cape.alias == null || cape.alias.isEmpty()
                            ? getString(R.string.skin_cape_unnamed) : cape.alias;
                    String state = cape.active
                            ? getString(R.string.skin_cape_equipped)
                            : getString(R.string.skin_cape_owned);
                    addCapeRow(name, state, null,
                            cape.active && SkinRemote.isUuid(mAccount.profileId)
                                    ? SkinRemote.crafatarCape(mAccount.profileId) : null);
                }
            }
        } else if (mAccount.authType == AuthType.ELY_BY) {
            mCapeNote.setText(R.string.skin_cape_note_elyby);
            if (mElyCapeBitmap != null) {
                addCapeRow(getString(R.string.skin_cape_elyby_name),
                        getString(R.string.skin_cape_managed_elyby), mElyCapeBitmap, null);
            } else {
                addCapeMessage(R.string.skin_cape_none);
            }
        } else {
            mCapeNote.setText(R.string.skin_cape_note_local);
        }
    }

    private void addCapeMessage(int stringRes) {
        if (mCapeList == null) return;
        TextView text = new TextView(requireContext());
        text.setText(stringRes);
        text.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        TextViewCompat.setTextAppearance(text, R.style.TextAppearance_Mojo_Body);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        mCapeList.addView(text);
    }

    private void addCapeRow(String name, String state, @Nullable Bitmap bitmap, @Nullable String imageUrl) {
        if (mCapeList == null) return;
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.view_skin_cape_row, mCapeList, false);
        TextView nameView = row.findViewById(R.id.skin_cape_name);
        TextView stateView = row.findViewById(R.id.skin_cape_state);
        ImageView image = row.findViewById(R.id.skin_cape_image);
        nameView.setText(name);
        stateView.setText(state);
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
        } else if (imageUrl != null && mIconCache != null && mAccount != null) {
            final int generation = mGeneration;
            if (mCapeReceiver != null) mIconCache.cancelImage(mCapeReceiver);
            final ImageReceiver receiver = loaded -> {
                if (generation != mGeneration || !isAdded() || getView() == null) return;
                if (loaded != null) image.setImageBitmap(loaded);
            };
            mCapeReceiver = receiver;
            mIconCache.getImage(receiver, "skin-cape-" + SkinRemote.normalizedUuid(mAccount.profileId)
                    + "-" + mPreviewNonce, imageUrl);
        }
        mCapeList.addView(row);
    }

    private void showStatus(String message, boolean error) {
        if (mStatus == null) return;
        mStatus.setText(trim(message));
        mStatus.setTextColor(ContextCompat.getColor(requireContext(), error ? R.color.danger : R.color.text_secondary));
        mStatus.setVisibility(View.VISIBLE);
    }

    private String describeError(Throwable throwable) {
        if (throwable instanceof SkinValidator.SkinValidationException) {
            SkinValidator.Reason reason = ((SkinValidator.SkinValidationException) throwable).reason;
            if (reason == SkinValidator.Reason.TOO_LARGE) {
                return getString(R.string.skin_error_too_large);
            }
            if (reason == SkinValidator.Reason.BAD_DIMENSIONS) {
                return getString(R.string.skin_error_dimensions);
            }
            return getString(R.string.skin_error_not_png);
        }
        String detail = throwable == null ? null : throwable.getMessage();
        if (detail == null || detail.trim().isEmpty()) detail = getString(R.string.skin_error_unknown);
        return getString(R.string.skin_error_detail, trim(detail));
    }

    private static String trim(String value) {
        if (value == null) return "";
        String singleLine = value.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() > 180 ? singleLine.substring(0, 180) + "\u2026" : singleLine;
    }

    private static byte[] readSkin(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
            if (output.size() > SkinValidator.MAX_BYTES) {
                throw new SkinValidator.SkinValidationException(
                        SkinValidator.Reason.TOO_LARGE, "Skin file is too large");
            }
        }
        return output.toByteArray();
    }
}
