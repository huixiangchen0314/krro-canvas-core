package top.kzre.krro.canvas.core.layer;

public final class MathUtils {

    private MathUtils() {}

    public static final float[] IDENTITY = {1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f};

    /**
     * 右乘两个变换矩阵：parent * local。
     * 每个矩阵为 6 元素 float 数组 [a b c d tx ty]。
     */
    public static float[] multiply(float[] parent, float[] local) {
        float a1 = parent[0], b1 = parent[1], c1 = parent[2], d1 = parent[3], tx1 = parent[4], ty1 = parent[5];
        float a2 = local[0],  b2 = local[1],  c2 = local[2],  d2 = local[3],  tx2 = local[4],  ty2 = local[5];
        return new float[] {
                a1 * a2 + c1 * b2,
                b1 * a2 + d1 * b2,
                a1 * c2 + c1 * d2,
                b1 * c2 + d1 * d2,
                a1 * tx2 + c1 * ty2 + tx1,
                b1 * tx2 + d1 * ty2 + ty1
        };
    }

    /**
     * 从单独的变换参数构造局部变换矩阵。
     * 变换顺序：缩放 → 旋转 → 平移。
     */
    public static float[] composeLocalTransform(float x, float y,
                                                float scaleX, float scaleY,
                                                float rotation) {
        float cosR = (float) Math.cos(rotation);
        float sinR = (float) Math.sin(rotation);
        return new float[] {
                scaleX * cosR,
                scaleX * sinR,
                scaleY * (-sinR),
                scaleY * cosR,
                x,
                y
        };
    }
}