package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.SubtitleParser;

/**
 * Compile shim for fongmi's private libass module.
 *
 * <p>{@link #isAvailable()} always returns {@code false}, so every caller takes its
 * existing non-libass code path and the standard Media3 subtitle pipeline is used.
 */
public final class LibassPlaybackSession {

    public static final class MediaComponents {

        public final ExtractorsFactory extractorsFactory;
        public final SubtitleParser.Factory subtitleParserFactory;

        public MediaComponents(ExtractorsFactory extractorsFactory, SubtitleParser.Factory subtitleParserFactory) {
            this.extractorsFactory = extractorsFactory;
            this.subtitleParserFactory = subtitleParserFactory;
        }
    }

    public LibassPlaybackSession(LibassConfiguration configuration, boolean enabled) {
    }

    public boolean isAvailable() {
        return false;
    }

    @Nullable
    public MediaComponents createMediaComponents(MediaItem mediaItem, ExtractorsFactory extractorsFactory) {
        return null;
    }

    @Nullable
    public Renderer createClockRenderer() {
        return null;
    }

    public void setPreloadMediaItem(@Nullable MediaItem mediaItem) {
    }

    public void setBottomPositionFraction(float bottomPositionFraction) {
    }

    public void setSecondaryBottomPositionFraction(float bottomPositionFraction) {
    }

    public void setFontScale(float scale, boolean apply) {
    }

    public void close() {
    }
}
