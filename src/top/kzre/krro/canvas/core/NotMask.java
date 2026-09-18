package top.kzre.krro.canvas.core;

/**
 * NOT 蒙版 —— 反转蒙版。
 * <p>
 * 语义：原蒙版可见的地方不可见，原蒙版不可见的地方可见。
 * 因子反转：{@code 1.0 - inner.getValue(x, y)}。
 * </p>
 * <p>
 * 无状态，线程安全（只要被包装的蒙版本身线程安全）。
 * </p>
 */
public final class NotMask implements Mask {

    private final Mask inner;

    public NotMask(Mask inner) {
        if (inner == null) {
            throw new IllegalArgumentException("inner mask must not be null");
        }
        this.inner = inner;
    }

    @Override
    public float getValue(double x, double y) {
        return 1.0f - inner.getValue(x, y);
    }
}