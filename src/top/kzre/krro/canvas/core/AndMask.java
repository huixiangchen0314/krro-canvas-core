package top.kzre.krro.canvas.core;

/**
 * AND 蒙版 —— 两个蒙版取交集。
 * <p>
 * 语义：两个蒙版都可见的地方才可见。
 * 因子相乘：{@code a * b}。
 * </p>
 * <p>
 * 无状态，线程安全（只要被组合的蒙版本身线程安全）。
 * </p>
 */
public final class AndMask implements Mask {

    private final Mask a;
    private final Mask b;

    public AndMask(Mask a, Mask b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("masks must not be null");
        }
        this.a = a;
        this.b = b;
    }

    @Override
    public float getValue(double x, double y) {
        return a.getValue(x, y) * b.getValue(x, y);
    }
}