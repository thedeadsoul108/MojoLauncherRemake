package git.artdeell.mojo.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;

/**
 * Visible account affordance used by the shell. It only reads account data; login and token
 * refresh stay in the hidden AccountSpinner so every ExtraCore listener keeps working.
 */
public class AccountChipView extends LinearLayout {
    private ImageView mAvatar;
    private TextView mName;
    private TextView mStatus;
    private int mRequestId;

    public AccountChipView(Context context) {
        super(context);
        init();
    }

    public AccountChipView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AccountChipView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClickable(true);
        setFocusable(true);
        setBackgroundResource(R.drawable.bg_account_chip);
        int paddingH = getResources().getDimensionPixelSize(R.dimen.mojo_space_2);
        int paddingV = getResources().getDimensionPixelSize(R.dimen.mojo_space_1);
        setPadding(paddingH, paddingV, paddingH, paddingV);
        LayoutInflater.from(getContext()).inflate(R.layout.view_account_chip, this, true);
        mAvatar = findViewById(R.id.account_chip_avatar);
        mName = findViewById(R.id.account_chip_name);
        mStatus = findViewById(R.id.account_chip_status);
        setAccount(null);
    }

    public void setAccount(@Nullable Account account) {
        int requestId = ++mRequestId;
        if(account == null) {
            mName.setText(R.string.account_chip_signed_out);
            mStatus.setText(R.string.account_chip_no_account);
            setPlaceholder(R.drawable.ic_ui_person_add, R.color.text_primary);
            mAvatar.setContentDescription(getContext().getString(R.string.account_chip_add_description));
            setContentDescription(getContext().getString(R.string.account_chip_add_description));
            return;
        }

        String username = account.username == null || account.username.isEmpty()
                ? getContext().getString(R.string.account_chip_no_account) : account.username;
        mName.setText(username);
        mStatus.setText(authTypeLabel(account.authType));
        setPlaceholder(R.drawable.ic_ui_person, R.color.text_primary);
        mAvatar.setContentDescription(getContext().getString(R.string.accounts_avatar_description, username));
        setContentDescription(getContext().getString(R.string.account_chip_description, username));

        final Account requestAccount = account;
        MojoApplication.sExecutorService.execute(() -> {
            Bitmap face = requestAccount.getSkinFace();
            Tools.runOnUiThread(() -> {
                if(requestId != mRequestId) return;
                if(face != null) {
                    mAvatar.setImageTintList(null);
                    mAvatar.setImageBitmap(face);
                } else {
                    setPlaceholder(R.drawable.ic_ui_person, R.color.text_primary);
                }
            });
        });
    }

    private void setPlaceholder(int drawable, int color) {
        mAvatar.setImageTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(getContext(), color)));
        mAvatar.setImageResource(drawable);
    }

    private String authTypeLabel(AuthType authType) {
        if(authType == AuthType.MICROSOFT) return getContext().getString(R.string.auth_select_microsoft);
        if(authType == AuthType.ELY_BY) return getContext().getString(R.string.auth_select_elyby);
        return getContext().getString(R.string.auth_select_local);
    }
}
