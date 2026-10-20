package git.artdeell.mojo.fragments;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.InstanceIconProvider;
import git.artdeell.mojo.ui.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Library list adapter. Selection is expressed through {@code state_selected} on the card so
 * the accent border and badge stay in sync without re-binding the whole list.
 */
public class InstanceCardAdapter extends RecyclerView.Adapter<InstanceCardAdapter.ViewHolder> {
    private static final int MENU_EDIT = 1;
    private static final int MENU_LAUNCH = 2;
    private static final int MENU_FOLDER = 3;
    private static final int MENU_DUPLICATE = 4;
    private static final int MENU_EXPORT = 5;
    private static final int MENU_DELETE = 6;

    public interface Listener {
        void onSelect(@NonNull Instance instance);
        void onEdit(@NonNull Instance instance);
        void onLaunch(@NonNull Instance instance);
        void onOpenFolder(@NonNull Instance instance);
        void onDuplicate(@NonNull Instance instance);
        void onExport(@NonNull Instance instance);
        void onDelete(@NonNull Instance instance);
    }

    private final Resources mResources;
    private final Listener mListener;
    private final List<Instance> mInstances = new ArrayList<>();
    private File mSelectedRoot;

    public InstanceCardAdapter(Resources resources, Listener listener) {
        mResources = resources;
        mListener = listener;
        setHasStableIds(false);
    }

    public void setInstances(@NonNull List<Instance> instances, File selectedRoot) {
        mInstances.clear();
        mInstances.addAll(instances);
        mSelectedRoot = selectedRoot;
        notifyDataSetChanged();
    }

    public void setSelectedRoot(File selectedRoot) {
        mSelectedRoot = selectedRoot;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.view_instance_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(mInstances.get(position));
    }

    @Override
    public int getItemCount() {
        return mInstances.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mIcon;
        private final TextView mName;
        private final TextView mMeta;
        private final TextView mBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mIcon = itemView.findViewById(R.id.instance_card_icon);
            mName = itemView.findViewById(R.id.instance_card_name);
            mMeta = itemView.findViewById(R.id.instance_card_meta);
            mBadge = itemView.findViewById(R.id.instance_card_badge);
            ImageButton more = itemView.findViewById(R.id.instance_card_more);
            more.setOnClickListener(this::showMenu);
            itemView.setOnClickListener(v -> {
                Instance instance = instanceAt();
                if (instance != null) mListener.onSelect(instance);
            });
        }

        void bind(Instance instance) {
            String name = UiUtils.displayName(itemView.getContext(), instance);
            String meta = UiUtils.describeInstance(itemView.getContext(), instance);
            mName.setText(name);
            mMeta.setText(meta);
            mIcon.setImageDrawable(InstanceIconProvider.fetchIcon(mResources, instance));
            mIcon.setContentDescription(itemView.getContext()
                    .getString(R.string.instance_icon_description, name));
            boolean selected = mSelectedRoot != null
                    && mSelectedRoot.equals(instance.getInstanceRoot());
            itemView.setSelected(selected);
            mBadge.setVisibility(selected ? View.VISIBLE : View.GONE);
            itemView.setContentDescription(itemView.getContext()
                    .getString(R.string.instance_card_description, name, meta)
                    + (selected ? ". " + itemView.getContext().getString(R.string.instances_selected) : ""));
        }

        private Instance instanceAt() {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION || position >= mInstances.size()) return null;
            return mInstances.get(position);
        }

        private void showMenu(View anchor) {
            Instance instance = instanceAt();
            if (instance == null) return;
            PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
            menu.getMenu().add(0, MENU_EDIT, 0, R.string.instances_edit);
            menu.getMenu().add(0, MENU_LAUNCH, 1, R.string.instances_launch);
            menu.getMenu().add(0, MENU_FOLDER, 2, R.string.instances_open_folder);
            menu.getMenu().add(0, MENU_DUPLICATE, 3, R.string.instances_duplicate);
            menu.getMenu().add(0, MENU_EXPORT, 4, R.string.instances_export);
            menu.getMenu().add(0, MENU_DELETE, 5, R.string.instances_delete);
            menu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == MENU_EDIT) mListener.onEdit(instance);
                else if (id == MENU_LAUNCH) mListener.onLaunch(instance);
                else if (id == MENU_FOLDER) mListener.onOpenFolder(instance);
                else if (id == MENU_DUPLICATE) mListener.onDuplicate(instance);
                else if (id == MENU_EXPORT) mListener.onExport(instance);
                else if (id == MENU_DELETE) mListener.onDelete(instance);
                else return false;
                return true;
            });
            menu.show();
        }
    }
}
