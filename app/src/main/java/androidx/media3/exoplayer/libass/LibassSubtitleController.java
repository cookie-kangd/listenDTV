package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.trackselection.TrackSelector;

import java.util.List;

/**
 * Compile shim for fongmi's private libass module. All subtitle selection state is
 * reported as unset, which leaves secondary subtitle handling inert.
 */
public final class LibassSubtitleController {

    public LibassSubtitleController(Player player, LibassPlaybackSession session, TrackSelector.Factory trackSelectorFactory, TextOutput textOutput) {
    }

    @Nullable
    public TrackSelectionOverride getPrimaryTextTrackSelectionOverride() {
        return null;
    }

    @Nullable
    public TrackSelectionOverride getSecondaryTextTrackSelectionOverride() {
        return null;
    }

    public List<TrackSelectionOverride> getSecondaryTextTrackSelectionOverrides() {
        return List.of();
    }

    public boolean isSecondaryTextTrackSuppressed() {
        return false;
    }

    public void setSecondaryTextTrackSelectionOverride(@Nullable TrackSelectionOverride override) {
    }

    public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {
    }

    public void close() {
    }
}
