package top.kzre.krro.canvas.core;

import clojure.lang.PersistentVector;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class Arrays {
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

    public static String writeTemp(float[] v) {
        try {
            File tempFile = File.createTempFile("temp_floats_", ".dat");
            try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(tempFile.toPath()))) {
                // 写入原始长度
                dos.writeInt(v.length);
                // 游程编码并写入
                int i = 0;
                while (i < v.length) {
                    // 寻找连续相等序列
                    int j = i + 1;
                    while (j < v.length && v[j] == v[i]) {
                        j++;
                    }
                    int repeatCount = j - i;
                    if (repeatCount >= 2) {  // 至少重复2次才用 RLE，避免膨胀
                        // 写负数表示重复次数
                        dos.writeInt(-repeatCount);
                        dos.writeFloat(v[i]);
                        i = j;
                    } else {
                        // 收集不重复的序列
                        int start = i;
                        // 找到下一个重复段开始或结束
                        while (i < v.length) {
                            int k = i + 1;
                            while (k < v.length && v[k] == v[i]) {
                                k++;
                            }
                            if (k - i >= 2) {
                                // 遇到了至少2次重复，不重复段到此为止
                                break;
                            }
                            i++;
                        }
                        int len = i - start;
                        // 写入正数表示不重复元素个数
                        dos.writeInt(len);
                        for (int t = start; t < i; t++) {
                            dos.writeFloat(v[t]);
                        }
                    }
                }
            }
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("写入临时文件失败", e);
        }
    }

    public static float[] readTemp(String path) {
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(path)))) {
            int totalLength = dis.readInt();
            float[] result = new float[totalLength];
            int index = 0;
            while (index < totalLength) {
                int count = dis.readInt();
                if (count < 0) {
                    int repeat = -count;
                    float val = dis.readFloat();
                    for (int i = 0; i < repeat; i++) {
                        result[index++] = val;
                    }
                } else if (count > 0) {
                    for (int i = 0; i < count; i++) {
                        result[index++] = dis.readFloat();
                    }
                } else {
                    throw new IOException("无效的游程编码数据：count为0");
                }
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("读取临时文件失败: " + path, e);
        }
    }

}