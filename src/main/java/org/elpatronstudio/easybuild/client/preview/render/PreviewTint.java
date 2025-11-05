package org.elpatronstudio.easybuild.client.preview.render;

/**
 * Defines the color modulation used for different preview categories.
 */
public enum PreviewTint {

    MISSING(1.0F, 1.0F, 1.0F, 0.55F),
    CONFLICT(1.0F, 0.55F, 0.55F, 0.45F);

    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    PreviewTint(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    public float red() {
        return red;
    }

    public float green() {
        return green;
    }

    public float blue() {
        return blue;
    }

    public float alpha() {
        return alpha;
    }
}
