package git.artdeell.mojo.content;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import git.artdeell.mojo.R;
import git.artdeell.mojo.modding.modpacks.imagecache.ImageReceiver;
import git.artdeell.mojo.modding.modpacks.imagecache.ModIconCache;

/** Recycler adapter for Modrinth project cards with a pagination footer. */
public class ContentProjectAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onProjectSelected(@NonNull ModrinthModels.Project project);
    }

    private static final int TYPE_PROJECT = 0;
    private static final int TYPE_LOADING = 1;

    private final List<ModrinthModels.Project> mProjects = new ArrayList<>();
    private final ModIconCache mIconCache;
    private final Listener mListener;
    private boolean mLoadingMore;

    public ContentProjectAdapter(ModIconCache iconCache, Listener listener) {
        mIconCache = iconCache;
        mListener = listener;
        setHasStableIds(false);
    }

    public void submit(List<ModrinthModels.Project> projects, boolean append) {
        if (!append) mProjects.clear();
        mProjects.addAll(projects);
        notifyDataSetChanged();
    }

    public void setLoadingMore(boolean loading) {
        if (mLoadingMore == loading) return;
        mLoadingMore = loading;
        if (loading) notifyItemInserted(mProjects.size());
        else notifyItemRemoved(mProjects.size());
    }

    public int projectCount() {
        return mProjects.size();
    }

    public ModrinthModels.Project getProject(int position) {
        return mProjects.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        return position < mProjects.size() ? TYPE_PROJECT : TYPE_LOADING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_LOADING) {
            return new LoadingHolder(inflater.inflate(R.layout.view_content_loading, parent, false));
        }
        return new ProjectHolder(inflater.inflate(R.layout.view_content_project, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ProjectHolder) {
            ((ProjectHolder) holder).bind(mProjects.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return mProjects.size() + (mLoadingMore ? 1 : 0);
    }

    private class ProjectHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView author;
        final TextView description;
        final TextView stats;
        String iconTag;
        ImageReceiver receiver;

        ProjectHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.content_project_icon);
            title = itemView.findViewById(R.id.content_project_title);
            author = itemView.findViewById(R.id.content_project_author);
            description = itemView.findViewById(R.id.content_project_description);
            stats = itemView.findViewById(R.id.content_project_stats);
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && position < mProjects.size()) {
                    mListener.onProjectSelected(mProjects.get(position));
                }
            });
        }

        void bind(ModrinthModels.Project project) {
            title.setText(project.title);
            String authorName = TextUtils.isEmpty(project.author)
                    ? itemView.getContext().getString(R.string.browse_unknown_author)
                    : project.author;
            author.setText(authorName);
            String projectDescription = project.description == null ? "" : project.description;
            description.setText(projectDescription);
            String downloads = NumberFormat.getIntegerInstance(Locale.getDefault())
                    .format(Math.max(0, project.downloads));
            stats.setText(itemView.getContext().getString(R.string.browse_download_count, downloads));
            itemView.setContentDescription(itemView.getContext().getString(
                    R.string.browse_project_description, project.title, authorName,
                    projectDescription, downloads));
            loadIcon(project);
        }

        private void loadIcon(ModrinthModels.Project project) {
            if (receiver != null) {
                mIconCache.cancelImage(receiver);
                receiver = null;
            }
            if (project.iconUrl == null || project.iconUrl.isEmpty()) {
                iconTag = null;
                icon.setPadding(dp(8), dp(8), dp(8), dp(8));
                icon.setImageTintList(ContextCompat.getColorStateList(
                        itemView.getContext(), R.color.text_muted));
                icon.setImageResource(R.drawable.ic_ui_box);
                return;
            }
            final String tag = project.cacheTag();
            iconTag = tag;
            icon.setPadding(dp(8), dp(8), dp(8), dp(8));
            icon.setImageTintList(ContextCompat.getColorStateList(
                    itemView.getContext(), R.color.text_muted));
            icon.setImageResource(R.drawable.ic_ui_box);
            receiver = bitmap -> {
                if (!tag.equals(iconTag)) {
                    bitmap.recycle();
                    return;
                }
                icon.setPadding(0, 0, 0, 0);
                icon.setImageTintList(null);
                icon.setImageBitmap(bitmap);
            };
            mIconCache.getImage(receiver, tag, project.iconUrl);
        }

        private int dp(int value) {
            return Math.round(value * itemView.getResources().getDisplayMetrics().density);
        }
    }

    private static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
