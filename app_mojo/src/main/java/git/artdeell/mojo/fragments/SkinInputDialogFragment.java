package git.artdeell.mojo.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import git.artdeell.mojo.R;

/** Rotation-safe text prompt for the skin URL and copy-from-player flows. */
public class SkinInputDialogFragment extends DialogFragment {
    public static final String REQUEST_KEY = "skin_input_request";
    public static final String RESULT_VALUE = "skin_input_value";
    public static final String RESULT_MODE = "skin_input_mode";
    private static final String ARG_MODE = "mode";
    private static final String STATE_TEXT = "input_text";
    public static final int MODE_URL = 1;
    public static final int MODE_PLAYER = 2;
    private EditText mInput;

    public static SkinInputDialogFragment newInstance(int mode) {
        SkinInputDialogFragment fragment = new SkinInputDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, mode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mInput != null && mInput.getText() != null) {
            outState.putString(STATE_TEXT, mInput.getText().toString());
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        int mode = getArguments() == null ? MODE_URL : getArguments().getInt(ARG_MODE, MODE_URL);
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_skin_input, null);
        TextView hint = view.findViewById(R.id.skin_input_hint);
        EditText input = view.findViewById(R.id.skin_input);
        mInput = input;
        boolean url = mode == MODE_URL;
        hint.setText(url ? R.string.skin_url_hint : R.string.skin_player_hint);
        input.setHint(url ? R.string.skin_url_example : R.string.skin_player_example);
        input.setInputType(url
                ? android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI
                : android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        if (savedInstanceState != null) {
            input.setText(savedInstanceState.getString(STATE_TEXT, ""));
        } else {
            input.requestFocus();
        }
        return new AlertDialog.Builder(requireContext())
                .setTitle(url ? R.string.skin_url_title : R.string.skin_player_title)
                .setView(view)
                .setPositiveButton(url ? R.string.skin_url_load : R.string.skin_player_copy,
                        (dialog, which) -> {
                            String value = input.getText() == null
                                    ? "" : input.getText().toString().trim();
                            Bundle result = new Bundle();
                            result.putString(RESULT_VALUE, value);
                            result.putInt(RESULT_MODE, mode);
                            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .create();
    }
}
