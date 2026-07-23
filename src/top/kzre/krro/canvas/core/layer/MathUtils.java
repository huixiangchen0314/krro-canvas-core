package top.kzre.krro.canvas.core.layer;

import top.kzre.krro.util.math.KMath;

/**
 * 2D 仿射矩阵工具（6 元素 float[]）。
 * 所有方法均委托给 {@link KMath}，此类仅为 Clojure 层提供便捷的静态入口。
 * 新代码建议直接使用 {@link KMath}。
 */
@Deprecated
public final class MathUtils {

    private MathUtils() {}

    /** 单位矩阵 [1, 0, 0, 1, 0, 0] */
    public static final float[] IDENTITY = KMath.mat2dIdentity();

    /**
     * 计算 2D 仿射矩阵的逆矩阵。
     * @param m 6 元素矩阵
     * @return 逆矩阵，若奇异则返回 null
     */
    public static float[] invert(float[] m) {
        return KMath.mat2dInvert(m);
    }

    /**
     * 右乘两个变换矩阵：parent * local。
     */
    public static float[] multiply(float[] parent, float[] local) {
        return KMath.mat2dMul(parent, local);
    }

    /**
     * 从平移、缩放、旋转构造 2D 仿射矩阵。
     * 变换顺序：缩放 → 旋转 → 平移。
     */
    public static float[] composeLocalTransform(float x, float y,
                                                float scaleX, float scaleY,
                                                float rotation) {
        return KMath.mat2dCompose(x, y, scaleX, scaleY, rotation);
    }


    /**
     * 构造图层的逆变换矩阵（世界 → 本地）。
     * 变换顺序为：逆平移 → 逆旋转 → 逆缩放，与正变换相反。
     */
    public static float[] composeInverseTransform(float x, float y,
                                                  float scaleX, float scaleY,
                                                  float rotation) {
        return KMath.mat2dComposeInverse(x, y, scaleX, scaleY, rotation);
    }
}

