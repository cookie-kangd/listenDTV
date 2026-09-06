package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Compile shim for fongmi's private libass module. Font family detection is not
 * available, so external fonts cannot be resolved by family name.
 */
public final class LibassFontFile {

    @Nullable
    public static String getFamilyName(File file) throws IOException {
        return null;
    }

    private LibassFontFile() {
    }
}
