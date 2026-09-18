package top.kzre.krro.canvas.core;

/**
 * OR 蒙版 —— 两个蒙版取并集。
 * <p>
 * 语义：任一个蒙版可见的地方就可见。
 * 因子取最大值：{@code max(a, b)}。
 * </p>
 * <p>
 * 无状态，线程安全（只要被组合的蒙版本身线程安全）。
 * </p>
 */
public final class OrMask implements Mask {

    private final Mask a;
    private final Mask b;

    public OrMask(Mask a, Mask b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("masks must not be null");
        }
        this.a = a;
        this.b = b;
    }

    @Override
    public float getValue(double x, double y) {
        return Math.max(a.getValue(x, y), b.getValue(x, y));
    }
}