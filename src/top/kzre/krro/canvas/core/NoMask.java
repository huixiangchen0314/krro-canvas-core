package top.kzre.krro.canvas.core;

/**
 * 空蒙版——不做任何遮挡。
 * <p>
 * 单例模式：所有不需要蒙版的地方共享同一个实例。
 * 无状态，线程安全。
 * </p>
 */
public final class NoMask implements Mask {

    /** 单例——无状态，可安全共享 */
    public static final NoMask INSTANCE = new NoMask();

    private NoMask() {}

    @Override
    public float getValue(double x, double y) {
        return 1.0f;
    }
}