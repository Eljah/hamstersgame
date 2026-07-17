package tatar.eljah.hamsters.tools.vectorasseteditor;

enum EditorMode {
    DRAW_CURVE("Draw Bezier"),
    MOVE_CURVES("Move curves"),
    BOUNDARIES("Boundaries");

    private final String displayName;

    EditorMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
