package androidx.media3.ui.danmaku;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.ui.PlayerView;
import okhttp3.OkHttpClient;

/**
 * Compile-time shim for the danmaku (bullet comment) view controller.
 *
 * <p>The upstream FongMi/TV app expects a private Media3 build that provides
 * {@code DanmakuPlayerViewController}. The public FongMi/media source tree
 * does not contain this class, so this shim is a no-op controller that keeps
 * the app compiling. Danmaku overlay rendering is disabled in this build;
 * all other playback features are unaffected.
 */
public final class DanmakuPlayerViewController {

    public DanmakuPlayerViewController() {}

    public void bind(PlayerView playerView) {
        // No-op: danmaku overlay is not rendered in this build.
    }

    public void setOkHttpClient(@Nullable OkHttpClient client) {
        // No-op.
    }

    public void setEnabled(boolean enabled) {
        // No-op.
    }

    public void setConfig(DanmakuConfig config) {
        // No-op.
    }

    public void setDataSource(@Nullable Uri uri) {
        // No-op.
    }

    public void sendNow(String text) {
        // No-op.
    }

    public void close() {
        // No-op.
    }
}
