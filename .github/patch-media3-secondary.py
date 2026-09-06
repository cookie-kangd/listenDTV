#!/usr/bin/env python3
"""Inject no-op secondary-subtitle APIs into the .media3 composite source.

The app was built against a private Media3 build that ships secondary
subtitle support (per-track selection overrides, secondary subtitle
styling). The public FongMi/media tree lacks these APIs, so we add
compile-compatible no-op implementations before building. Secondary
subtitle *selection* degrades to a no-op; normal subtitle rendering
is unaffected.
"""
import io
import sys

MPV_PLAYER = ".media3/libraries/mpvplayer/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java"
SUB_OPTIONS = ".media3/libraries/mpvplayer/src/main/java/androidx/media3/mpvplayer/MpvSubtitleOptions.java"

MARKER = "listenDTV injected"

PLAYER_METHODS = """\
  // BEGIN listenDTV injected secondary-subtitle shims (no-op)
  @androidx.annotation.Nullable
  public androidx.media3.common.TrackSelectionOverride getPrimaryTextTrackSelectionOverride() {
    return null;
  }

  @androidx.annotation.Nullable
  public androidx.media3.common.TrackSelectionOverride getSecondaryTextTrackSelectionOverride() {
    return null;
  }

  public java.util.List<androidx.media3.common.TrackSelectionOverride>
  getSecondaryTextTrackSelectionOverrides() {
    return java.util.List.of();
  }

  public boolean isSecondaryTextTrackSuppressed() {
    return false;
  }

  public void setSecondaryTextTrackSelectionOverride(
      @androidx.annotation.Nullable androidx.media3.common.TrackSelectionOverride override) {}

  public void resetSecondaryTextTrackSelection() {}

  public void setSecondaryTextTrackAutoSelectionEnabled(boolean enabled) {}
  // END listenDTV injected secondary-subtitle shims
"""

BUILDER_METHODS = """\
    // BEGIN listenDTV injected secondary-subtitle builder shims (no-op)
    public Builder setSecondarySubtitlePosition(double position) {
      return this;
    }

    public Builder setSecondaryAssStyleOverride(boolean overrideAssStyles) {
      return this;
    }

    public Builder setFontFamily(@androidx.annotation.Nullable String fontFamily) {
      return this;
    }

    public Builder setFontsDirectory(@androidx.annotation.Nullable String fontsDirectory) {
      return this;
    }
    // END listenDTV injected secondary-subtitle builder shims
"""


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, src):
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        f.write(src)


def patch_player(path):
    src = read(path)
    if MARKER in src:
        print("skip MpvPlayer: already patched")
        return
    body = src.rstrip()
    if not body.endswith("}"):
        print("FATAL: MpvPlayer.java does not end with '}'")
        sys.exit(1)
    # Insert before the final closing brace of the top-level class.
    body = body[:-1].rstrip("\n") + "\n\n" + PLAYER_METHODS + "}\n"
    write(path, body)
    print("patched MpvPlayer.java (secondary-subtitle methods)")


def patch_sub_options(path):
    src = read(path)
    if MARKER in src:
        print("skip MpvSubtitleOptions: already patched")
        return
    anchor = "public MpvSubtitleOptions build() {"
    idx = src.find(anchor)
    if idx < 0:
        print("FATAL: build() anchor not found in MpvSubtitleOptions.java")
        sys.exit(1)
    # Start of the line containing the anchor.
    line_start = src.rfind("\n", 0, idx) + 1
    src = src[:line_start] + BUILDER_METHODS + "\n" + src[line_start:]
    write(path, src)
    print("patched MpvSubtitleOptions.java (Builder setters)")


if __name__ == "__main__":
    patch_player(MPV_PLAYER)
    patch_sub_options(SUB_OPTIONS)
