package top.kzre.krro.canvas.core.layer;

public final class MathUtils {

    private MathUtils() {}

    public static final float[] IDENTITY = {1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f};

    /**
     * 计算 2D 仿射矩阵的逆矩阵。
     * 矩阵格式：[a b c d tx ty]
     * 逆矩阵公式：
     * det = a*d - b*c
     * 若 det 为 0，返回 null。
     */
    public static float[] invert(float[] m) {
        float a = m[0], b = m[1], c = m[2], d = m[3], tx = m[4], ty = m[5];
        double det = (double) a * d - (double) b * c;
        if (Math.abs(det) < 1e-10) {
            return null;
        }
        double invDet = 1.0 / det;
        float aInv = (float) (d * invDet);
        float bInv = (float) (-b * invDet);
        float cInv = (float) (-c * invDet);
        float dInv = (float) (a * invDet);
        float txInv = (float) ((c * ty - d * tx) * invDet);
        float tyInv = (float) ((b * tx - a * ty) * invDet);
        return new float[] {aInv, bInv, cInv, dInv, txInv, tyInv};
    }

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

    /**
     * 构造图层的逆变换矩阵（世界 → 本地）。
     * 变换顺序为：逆平移 → 逆旋转 → 逆缩放，与正变换相反。
     */
    public static float[] composeInverseTransform(float x, float y,
                                                  float scaleX, float scaleY,
                                                  float rotation) {
        float cosR = (float) Math.cos(rotation);
        float sinR = (float) Math.sin(rotation);
        // 处理缩放为0的退化情况，避免除零
        float invSX = 1.0f / (scaleX == 0 ? 1e-6f : scaleX);
        float invSY = 1.0f / (scaleY == 0 ? 1e-6f : scaleY);
        return new float[] {
                cosR * invSX,              // a
                -sinR * invSY,             // b
                sinR * invSX,              // c
                cosR * invSY,              // d
                -(cosR * x + sinR * y) * invSX,  // tx
                (sinR * x - cosR * y) * invSY    // ty
        };
    }
}