package net.nerdorg.minehop.util;

import java.util.Locale;

public final class SurfRampVisualStyle {
    public static final String MODE_BLOCK = "block";
    public static final String MODE_WIREFRAME = "wireframe";

    public static final int DEFAULT_WIREFRAME_COLOR = 0x00D7FF;
    public static final boolean DEFAULT_WIREFRAME_FILL = false;
    public static final int DEFAULT_WIREFRAME_FILL_COLOR = 0x5A8CFF;
    public static final int DEFAULT_WIREFRAME_FILL_ALPHA = 118;

    private SurfRampVisualStyle() {
    }

    public static String sanitizeMode(String rawMode) {
        if (rawMode == null) {
            return MODE_BLOCK;
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        if (MODE_WIREFRAME.equals(normalized) || "wire".equals(normalized) || "line".equals(normalized)) {
            return MODE_WIREFRAME;
        }
        return MODE_BLOCK;
    }

    public static int sanitizeColor(int rgb, int fallback) {
        int fallbackMasked = fallback & 0xFFFFFF;
        if (rgb < 0) {
            return fallbackMasked;
        }
        return rgb & 0xFFFFFF;
    }

    public static int sanitizeAlpha(int alpha, int fallback) {
        if (alpha < 0 || alpha > 255) {
            return Math.max(0, Math.min(255, fallback));
        }
        return alpha;
    }

    public static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    public static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    public static int blue(int rgb) {
        return rgb & 0xFF;
    }
}
