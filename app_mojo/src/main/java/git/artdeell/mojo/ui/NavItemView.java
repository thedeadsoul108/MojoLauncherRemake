package git.artdeell.mojo.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import git.artdeell.mojo.R;

/**
 * A bottom-navigation / navigation-rail destination. The same view works in both layouts; the
 * parent decides whether items are arranged horizontally or vertically.
 */
public class NavItemView extends LinearLayout {
    private ImageView mIcon;
    private TextView mLabel;
    private android.graphics.Typeface mLabelTypeface;

    public NavItemView(Context context) {
        super(context);
        init();
    }

    public NavItemView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NavItemView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setClickable(true);
        setFocusable(true);
        setBackgroundResource(R.drawable.bg_nav_item);
        setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.mojo_touch_target));
        int padding = getResources().getDimensionPixelSize(R.dimen.mojo_space_1);
        setPadding(padding, padding, padding, padding);
        LayoutInflater.from(getContext()).inflate(R.layout.view_nav_item, this, true);
        mIcon = findViewById(R.id.nav_item_icon);
        mLabel = findViewById(R.id.nav_item_label);
    }

    public void bind(@DrawableRes int iconRes, @StringRes int labelRes) {
        mIcon.setImageResource(iconRes);
        mIcon.setImageTintList(ContextCompat.getColorStateList(getContext(), R.color.nav_item_tint));
        mIcon.setDuplicateParentStateEnabled(true);
        mLabel.setText(labelRes);
        mLabel.setTextColor(ContextCompat.getColorStateList(getContext(), R.color.nav_item_text));
        mLabel.setDuplicateParentStateEnabled(true);
        if (mLabelTypeface == null) mLabelTypeface = mLabel.getTypeface();
        setContentDescription(getContext().getString(labelRes));
    }

    public void setActive(boolean active) {
        setSelected(active);
        mIcon.setSelected(active);
        mLabel.setSelected(active);
        mLabel.setTypeface(active
                ? android.graphics.Typeface.create(mLabelTypeface, android.graphics.Typeface.BOLD)
                : mLabelTypeface);
    }
}
