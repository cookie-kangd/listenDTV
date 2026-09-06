package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;

/**
 * Compile shim for fongmi's private libass module.
 *
 * <p>The upstream FongMi/media releases do not contain the libass module, so this
 * placeholder keeps the app compiling. It carries the configuration values but the
 * engine never reports itself as available, which makes the player fall back to the
 * standard Media3 subtitle pipeline.
 */
public final class LibassConfiguration {

    @Nullable public final String fontConfig;
    @Nullable public final String fontsDirectory;
    @Nullable public final String defaultFontFamily;
    public final int maximumRenderPixels;
    public final int maximumGlyphCount;
    public final int maximumBitmapCacheSizeMb;

    private LibassConfiguration(Builder builder) {
        this.fontConfig = builder.fontConfig;
        this.fontsDirectory = builder.fontsDirectory;
        this.defaultFontFamily = builder.defaultFontFamily;
        this.maximumRenderPixels = builder.maximumRenderPixels;
        this.maximumGlyphCount = builder.maximumGlyphCount;
        this.maximumBitmapCacheSizeMb = builder.maximumBitmapCacheSizeMb;
    }

    public static final class Builder {

        @Nullable private String fontConfig;
        @Nullable private String fontsDirectory;
        @Nullable private String defaultFontFamily;
        private int maximumRenderPixels;
        private int maximumGlyphCount;
        private int maximumBitmapCacheSizeMb;

        public Builder setFontConfig(@Nullable String fontConfig) {
            this.fontConfig = fontConfig;
            return this;
        }

        public Builder setFontsDirectory(@Nullable String fontsDirectory) {
            this.fontsDirectory = fontsDirectory;
            return this;
        }

        public Builder setDefaultFontFamily(@Nullable String defaultFontFamily) {
            this.defaultFontFamily = defaultFontFamily;
            return this;
        }

        public Builder setMaximumRenderPixels(int maximumRenderPixels) {
            this.maximumRenderPixels = maximumRenderPixels;
            return this;
        }

        public Builder setMaximumGlyphCount(int maximumGlyphCount) {
            this.maximumGlyphCount = maximumGlyphCount;
            return this;
        }

        public Builder setMaximumBitmapCacheSizeMb(int maximumBitmapCacheSizeMb) {
            this.maximumBitmapCacheSizeMb = maximumBitmapCacheSizeMb;
            return this;
        }

        public LibassConfiguration build() {
            return new LibassConfiguration(this);
        }
    }
}
