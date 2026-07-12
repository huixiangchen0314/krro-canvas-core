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

    /**
     * 将浮点数组进行游程编码并写入输出流。
     * 格式：总长度(int) → [计数(int) 值(float)]...
     * 计数 > 0 表示不重复的浮点数个数，紧跟相应数量的 float；
     * 计数 < 0 表示重复次数（绝对值），紧跟一个 float 作为重复值。
     */
    public static void writeRLE(float[] data, DataOutputStream out) throws IOException {
        out.writeInt(data.length);
        int i = 0;
        while (i < data.length) {
            // 寻找连续相等段
            int j = i + 1;
            while (j < data.length && data[j] == data[i]) {
                j++;
            }
            int repeatCount = j - i;
            if (repeatCount >= 2) {          // 至少重复2次才压缩，避免膨胀
                out.writeInt(-repeatCount);
                out.writeFloat(data[i]);
                i = j;
            } else {
                // 收集不重复段
                int start = i;
                while (i < data.length) {
                    int k = i + 1;
                    while (k < data.length && data[k] == data[i]) {
                        k++;
                    }
                    if (k - i >= 2) {        // 遇到下一个重复段，停止收集
                        break;
                    }
                    i++;
                }
                int len = i - start;
                out.writeInt(len);
                for (int t = start; t < i; t++) {
                    out.writeFloat(data[t]);
                }
            }
        }
    }

    /**
     * 从输入流读取游程编码数据并解码为浮点数组。
     * 输入流必须包含由 encodeRLE 写入的长度前缀。
     */
    public static float[] readRLE(DataInputStream in) throws IOException {
        int totalLength = in.readInt();
        float[] result = new float[totalLength];
        int index = 0;
        while (index < totalLength) {
            int count = in.readInt();
            if (count < 0) {
                int repeat = -count;
                float val = in.readFloat();
                for (int i = 0; i < repeat; i++) {
                    result[index++] = val;
                }
            } else if (count > 0) {
                for (int i = 0; i < count; i++) {
                    result[index++] = in.readFloat();
                }
            } else {
                throw new IOException("无效的游程编码数据：count为0");
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════
    // 便捷临时文件操作（内部复用 RLE 工具）
    // ═══════════════════════════════════════════════════

    public static String writeTemp(float[] v) {
        try {
            File tempFile = File.createTempFile("temp_floats_", ".dat");
            try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(tempFile.toPath()))) {
                writeRLE(v, dos);
            }
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("写入临时文件失败", e);
        }
    }

    public static float[] readTemp(String path) {
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(path)))) {
            return readRLE(dis);
        } catch (IOException e) {
            throw new RuntimeException("读取临时文件失败: " + path, e);
        }
    }

}