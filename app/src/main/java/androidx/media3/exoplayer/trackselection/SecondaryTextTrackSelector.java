package androidx.media3.exoplayer.trackselection;

import android.content.Context;
import androidx.annotation.NonNull;

/**
 * Compile-time shim for the secondary-subtitle track selector.
 *
 * <p>The upstream FongMi/TV app expects a private Media3 build that provides
 * {@code SecondaryTextTrackSelector}. The public FongMi/media source tree does
 * not contain this class, so this shim delegates to the wrapped
 * {@link TrackSelector.Factory} (the app's own decode-aware factory) without
 * any secondary-text-specific behaviour.
 */
public final class SecondaryTextTrackSelector {

    private SecondaryTextTrackSelector() {}

    /** Factory that simply delegates to the wrapped {@link TrackSelector.Factory}. */
    public static final class Factory implements TrackSelector.Factory {

        private final TrackSelector.Factory delegate;

        public Factory(@NonNull TrackSelector.Factory delegate) {
            this.delegate = delegate;
        }

        @NonNull
        @Override
        public TrackSelector createTrackSelector(@NonNull Context context) {
            return delegate.createTrackSelector(context);
        }
    }
}
