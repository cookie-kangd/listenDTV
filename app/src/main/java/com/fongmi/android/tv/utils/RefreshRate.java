package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.view.Display;
import android.view.WindowManager;

import com.fongmi.android.tv.setting.Setting;

public class RefreshRate {

    public static void apply(Activity activity) {
        apply(activity, Setting.getHighRefresh());
    }

    /**
     * @param high true follows the panel's highest refresh rate; false stays as
     *             close to 60 Hz as the panel allows. Audio-only listening uses
     *             60 Hz: there is no motion to show, and the extra refresh
     *             cycles only cost power while the screen is on.
     */
    public static void apply(Activity activity, boolean high) {
        try {
            Display display = ResUtil.getDisplay(activity);
            Display.Mode current = display.getMode();
            Display.Mode target = current;
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() != current.getPhysicalWidth() || mode.getPhysicalHeight() != current.getPhysicalHeight()) continue;
                if (high && mode.getRefreshRate() > target.getRefreshRate()) target = mode;
                if (!high && Math.abs(mode.getRefreshRate() - 60f) < Math.abs(target.getRefreshRate() - 60f)) target = mode;
            }
            if (target.getModeId() == current.getModeId()) return;
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.preferredDisplayModeId = target.getModeId();
            activity.getWindow().setAttributes(params);
        } catch (Exception ignored) {
        }
    }
}
