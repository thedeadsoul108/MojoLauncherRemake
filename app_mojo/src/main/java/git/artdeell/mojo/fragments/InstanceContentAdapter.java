package git.artdeell.mojo.fragments;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;
import git.artdeell.mojo.content.ContentEntry;

import java.util.ArrayList;
import java.util.List;

/** Flattened headers and toggle rows for the content screen. */
public class InstanceContentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ENTRY = 1;

    public interface Listener {
        void onToggle(@NonNull ContentEntry entry);
    }

    private final List<Object> mRows = new ArrayList<>();
    private final Listener mListener;

    public InstanceContentAdapter(Listener listener) {
        mListener = listener;
    }

    public void submit(@NonNull List<ContentEntry> entries) {
        mRows.clear();
        for (ContentEntry.Category category : ContentEntry.Category.values()) {
            List<ContentEntry> group = new ArrayList<>();
            for (ContentEntry entry : entries) {
                if (entry.category == category) group.add(entry);
            }
            if (group.isEmpty()) continue;
            mRows.add(category);
            mRows.addAll(group);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mRows.get(position) instanceof ContentEntry.Category ? TYPE_HEADER : TYPE_ENTRY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.view_content_header, parent, false));
        }
        return new EntryHolder(inflater.inflate(R.layout.view_content_entry, parent, false), mListener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = mRows.get(position);
        if (row instanceof ContentEntry.Category) {
            ((HeaderHolder) holder).bind((ContentEntry.Category) row, count((ContentEntry.Category) row));
        } else {
            ((EntryHolder) holder).bind((ContentEntry) row);
        }
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    private int count(ContentEntry.Category category) {
        int count = 0;
        for (Object row : mRows) {
            if (row instanceof ContentEntry && ((ContentEntry) row).category == category) count++;
        }
        return count;
    }

    private static class HeaderHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView count;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.content_header_title);
            count = itemView.findViewById(R.id.content_header_count);
        }

        void bind(ContentEntry.Category category, int value) {
            title.setText(titleFor(category, itemView));
            count.setText(itemView.getContext().getString(R.string.content_count, value));
        }
    }

    private static class EntryHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView meta;
        private final SwitchCompat toggle;
        private final Listener listener;
        private boolean binding;

        EntryHolder(@NonNull View itemView, Listener listener) {
            super(itemView);
            name = itemView.findViewById(R.id.content_entry_name);
            meta = itemView.findViewById(R.id.content_entry_meta);
            toggle = itemView.findViewById(R.id.content_entry_switch);
            this.listener = listener;
        }

        void bind(ContentEntry entry) {
            binding = true;
            name.setText(entry.displayName);
            String size = Formatter.formatShortFileSize(itemView.getContext(), Math.max(0, entry.size));
            String state = itemView.getContext().getString(
                    entry.enabled ? R.string.content_enabled : R.string.content_disabled);
            meta.setText(size + " · " + state);
            toggle.setContentDescription(itemView.getContext().getString(
                    R.string.content_switch_description, entry.displayName));
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(entry.enabled);
            toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (binding || isChecked == entry.enabled) return;
                    listener.onToggle(entry);
                }
            });
            binding = false;
        }
    }

    private static CharSequence titleFor(ContentEntry.Category category, View itemView) {
        int res;
        switch (category) {
            case RESOURCE_PACKS:
                res = R.string.content_resource_packs;
                break;
            case SHADER_PACKS:
                res = R.string.content_shader_packs;
                break;
            case DATA_PACKS:
                res = R.string.content_data_packs;
                break;
            case MODS:
            default:
                res = R.string.content_mods;
                break;
        }
        return itemView.getContext().getString(res);
    }
}
