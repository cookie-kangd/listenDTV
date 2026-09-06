package androidx.media3.exoplayer.text;

import androidx.media3.common.text.CueGroup;

/**
 * Compile-time shim for the secondary-subtitle text output.
 *
 * <p>The upstream FongMi/TV app expects a private Media3 build that provides
 * {@code SecondaryTextOutput}. The public FongMi/media source tree does not
 * contain this class, so this shim implements the standard {@link TextOutput}
 * interface as a no-op. Secondary subtitle rendering via the standard Media3
 * text pipeline still works; only this dedicated output hook is inert.
 */
public final class SecondaryTextOutput implements TextOutput {

    public SecondaryTextOutput() {}

    @Override
    public void onCues(CueGroup cueGroup) {
        // No-op: secondary text output is not wired in this build.
    }
}
