package top.kzre.krro.canvas.core;

import clojure.lang.ISeq;
import clojure.lang.PersistentVector;

public class Arrays {
    /**
     * 将浮点数组填充为指定值，零反射开销。
     */
    public static void fill(float[] a, float val) {
        java.util.Arrays.fill(a, val);
    }

    /** 填充为 0.0f */
    public static void zero(float[] a) {
        fill(a, 0.0f);
    }

    // 基本拷贝
    public static void copy(float[] src, float[] dest) {
        System.arraycopy(src, 0, dest, 0, Math.min(src.length, dest.length));
    }

    // 2 个目标
    public static void copy(float[] src, float[] dest, float[] dest2) {
        copy(src, dest);
        copy(src, dest2);
    }

    // 3 个目标
    public static void copy(float[] src, float[] dest, float[] dest2, float[] dest3) {
        copy(src, dest);
        copy(src, dest2);
        copy(src, dest3);
    }

    // 4 个目标
    public static void copy(float[] src, float[] dest, float[] dest2, float[] dest3, float[] dest4) {
        copy(src, dest);
        copy(src, dest2);
        copy(src, dest3);
        copy(src, dest4);
    }

    // 5 个目标
    public static void copy(float[] src, float[] dest, float[] dest2, float[] dest3, float[] dest4, float[] dest5) {
        copy(src, dest);
        copy(src, dest2);
        copy(src, dest3);
        copy(src, dest4);
        copy(src, dest5);
    }

    // 6 个目标
    public static void copy(float[] src, float[] dest, float[] dest2, float[] dest3, float[] dest4, float[] dest5, float[] dest6) {
        copy(src, dest);
        copy(src, dest2);
        copy(src, dest3);
        copy(src, dest4);
        copy(src, dest5);
        copy(src, dest6);
    }


    public static PersistentVector toVec(float[] a) {
        if (a == null || a.length == 0) {
            return PersistentVector.EMPTY;
        }
        Object[] boxed = new Object[a.length];
        for (int i = 0; i < a.length; i++) {
            boxed[i] = a[i]; // 自动装箱为 Float
        }
        return PersistentVector.create(boxed);
    }

    public static float[] toFloats(PersistentVector vec) {
        if (vec == null || vec.count() == 0) {
            return new float[0];
        }
        int len = vec.count();
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            Object val = vec.nth(i);
            if (val instanceof Number) {
                result[i] = ((Number) val).floatValue();
            } else {
                throw new IllegalArgumentException("Vector element at index " + i + " is not a number: " + val);
            }
        }
        return result;
    }

}