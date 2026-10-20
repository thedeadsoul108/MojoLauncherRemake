package git.artdeell.mojo.content;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Wire models for the public Modrinth v2 API. Only fields the launcher consumes are mapped. */
public final class ModrinthModels {
    private ModrinthModels() {}

    public static class SearchResponse {
        public List<Project> hits;
        public int offset;
        public int limit;
        @SerializedName("total_hits")
        public int totalHits;
    }

    public static class Project {
        public String id;
        @SerializedName("project_id")
        public String searchProjectId;
        public String slug;
        public String title;
        public String description;
        public String author;
        public String body;
        @SerializedName("project_type")
        public String projectType;
        @SerializedName("icon_url")
        public String iconUrl;
        public int downloads;
        public int follows;
        public List<String> categories;
        @SerializedName("client_side")
        public String clientSide;
        @SerializedName("server_side")
        public String serverSide;
        public List<String> loaders;
        @SerializedName("game_versions")
        public List<String> gameVersions;
        @SerializedName("date_modified")
        public String dateModified;

        public String cacheTag() {
            String tag = slug != null && !slug.isEmpty() ? slug : id;
            return tag == null ? "project" : tag.replaceAll("[^A-Za-z0-9._-]", "_");
        }

        /** Search hits use project_id while the detail endpoint uses id. */
        public String idOrSlug() {
            if (id != null && !id.isEmpty()) return id;
            if (searchProjectId != null && !searchProjectId.isEmpty()) return searchProjectId;
            if (slug != null && !slug.isEmpty()) return slug;
            return "";
        }
    }

    public static class Version {
        public String id;
        @SerializedName("project_id")
        public String projectId;
        public String name;
        @SerializedName("version_number")
        public String versionNumber;
        @SerializedName("game_versions")
        public List<String> gameVersions;
        public List<String> loaders;
        @SerializedName("date_published")
        public String datePublished;
        public List<VersionFile> files;
        public List<Dependency> dependencies;
        public String versionType;
    }

    public static class VersionFile {
        public Hashes hashes;
        public String url;
        public String filename;
        public boolean primary;
        public long size;
    }

    public static class Hashes {
        public String sha1;
        public String sha512;
    }

    public static class Dependency {
        @SerializedName("version_id")
        public String versionId;
        @SerializedName("project_id")
        public String projectId;
        @SerializedName("file_name")
        public String fileName;
        @SerializedName("dependency_type")
        public String dependencyType;
    }

    public static VersionFile primaryFile(Version version) {
        if (version == null || version.files == null || version.files.isEmpty()) return null;
        for (VersionFile file : version.files) {
            if (file.primary) return file;
        }
        return version.files.get(0);
    }
}
