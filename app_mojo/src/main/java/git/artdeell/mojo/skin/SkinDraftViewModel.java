package git.artdeell.mojo.skin;

import androidx.lifecycle.ViewModel;

/** Retains an unsaved skin across rotation and process-lifetime configuration changes. */
public class SkinDraftViewModel extends ViewModel {
    public byte[] png;
    public String variant = "classic";
    public String source = "";
    public String label = "";

    public boolean hasSkin() {
        return png != null && png.length > 0;
    }

    public void set(byte[] png, String variant, String source, String label) {
        this.png = png;
        this.variant = "slim".equals(variant) ? "slim" : "classic";
        this.source = source == null ? "" : source;
        this.label = label == null ? "" : label;
    }

    public void clear() {
        png = null;
        source = "";
        label = "";
    }
}
