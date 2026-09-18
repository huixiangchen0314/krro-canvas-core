package top.kzre.krro.canvas.core;

public final class Masks {
    private Masks() {}

    public static Mask and(Mask a, Mask b) {
        if (a == NoMask.INSTANCE) return b;
        if (b == NoMask.INSTANCE) return a;
        return new AndMask(a, b);
    }

    public static Mask or(Mask a, Mask b) {
        if (a == NoMask.INSTANCE || b == NoMask.INSTANCE) return NoMask.INSTANCE;
        return new OrMask(a, b);
    }

    public static Mask not(Mask m) {
        if (m == NoMask.INSTANCE) return FullMask.INSTANCE;   // 全隐藏
        return new NotMask(m);
    }
}