package net.nerdorg.minehop.render;

public final class GuiColors {
    private GuiColors() {
    }

    /**
     * Up to 1.21.5 the text renderer treated a colour without alpha bits (top 6 bits zero, e.g.
     * {@code 0xFFFFFF}) as fully opaque. Since 1.21.6 DrawContext skips text whose alpha is 0, so the
     * old RGB constants would draw nothing. Applies the old TextRenderer#tweakTransparency rule.
     */
    public static int text(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }
}
