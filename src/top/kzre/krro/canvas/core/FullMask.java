package top.kzre.krro.canvas.core;

public final class FullMask implements Mask {
    public static final FullMask INSTANCE = new FullMask();
    private FullMask() {}

    @Override
    public float getValue(double x, double y) {
        return 0.0f;
    }
}