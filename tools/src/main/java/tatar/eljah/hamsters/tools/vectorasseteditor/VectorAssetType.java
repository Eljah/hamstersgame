package tatar.eljah.hamsters.tools.vectorasseteditor;

enum VectorAssetType {
    STATIC_BLOCK("Static block", false),
    ANIMATED_MOVING_BLOCK("Animated moving block", true),
    ANIMATED_CHARACTER("Animated character", true);

    private final String displayName;
    private final boolean animated;

    VectorAssetType(String displayName, boolean animated) {
        this.displayName = displayName;
        this.animated = animated;
    }

    boolean isAnimated() {
        return animated;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
