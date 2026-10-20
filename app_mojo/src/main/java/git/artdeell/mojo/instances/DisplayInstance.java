package git.artdeell.mojo.instances;

import java.io.File;

public class DisplayInstance {
    protected transient File mInstanceRoot;
    public String name;
    public String versionId;
    public String icon;

    protected void sanitize() {
        sanitizeIcon();
    }

    protected DisplayInstance() {
    }

    protected File getInstanceIconLocation() {
        return new File(mInstanceRoot, "icon.webp");
    }

    /**
     * @return the directory that stores this instance's metadata and game data
     */
    public File getInstanceRoot() {
        return mInstanceRoot;
    }

    /**
     * @return the user-visible name, or an empty string when it has not been set
     */
    public String getDisplayName() {
        return name == null ? "" : name.trim();
    }

    private void sanitizeIcon() {
        if(!InstanceIconProvider.hasStaticIcon(icon)) {
            icon = InstanceIconProvider.FALLBACK_ICON_NAME;
        }
    }
}
