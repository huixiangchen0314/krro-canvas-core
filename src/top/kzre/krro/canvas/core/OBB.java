package top.kzre.krro.canvas.core;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public final class OBB {
    public final float centerX, centerY;
    public final float halfWidth, halfHeight;
    public final float angle; // 主轴与X轴夹角（弧度）
    public final float[] axisU, axisV; // 单位化后的两个轴

    public OBB(float cx, float cy, float hw, float hh, float angle, float[] axisU, float[] axisV) {
        this.centerX = cx;
        this.centerY = cy;
        this.halfWidth = hw;
        this.halfHeight = hh;
        this.angle = angle;
        this.axisU = axisU;
        this.axisV = axisV;
    }

    /**
     * 从一组 AABB 矩形计算有向包围盒（基于 PCA）。
     * @param rects 矩形列表，每个矩形为 [x, y, width, height] 的 int 数组
     */
    public static OBB computeOBB(List<int[]> rects) {
        // 收集所有矩形的四个角点
        List<float[]> points = new ArrayList<>();
        for (int[] r : rects) {
            int x = r[0], y = r[1], w = r[2], h = r[3];
            points.add(new float[]{x, y});
            points.add(new float[]{x + w, y});
            points.add(new float[]{x, y + h});
            points.add(new float[]{x + w, y + h});
        }

        int n = points.size();
        if (n == 0) return new OBB(0,0,0,0,0, new float[]{1,0}, new float[]{0,1});

        // 计算中心
        float cx = 0, cy = 0;
        for (float[] p : points) { cx += p[0]; cy += p[1]; }
        cx /= n; cy /= n;

        // 协方差矩阵
        float m00 = 0, m01 = 0, m11 = 0;
        for (float[] p : points) {
            float dx = p[0] - cx;
            float dy = p[1] - cy;
            m00 += dx * dx;
            m01 += dx * dy;
            m11 += dy * dy;
        }
        m00 /= n; m01 /= n; m11 /= n;

        // 2x2 协方差矩阵的特征值分解
        float trace = m00 + m11;
        float det = m00 * m11 - m01 * m01;
        float disc = (float) Math.sqrt(Math.max(0, (double) trace * trace - 4.0 * det));
        float e1 = (trace + disc) / 2.0f;
        float e2 = (trace - disc) / 2.0f;

        // 取较大特征值对应的特征向量为主轴
        float[] vecU;
        if (e1 >= e2) {
            vecU = new float[]{m01, e1 - m00};
        } else {
            vecU = new float[]{m01, e2 - m00};
        }
        // 单位化
        float lenU = (float) Math.sqrt(vecU[0] * vecU[0] + vecU[1] * vecU[1]);
        if (lenU < 1e-7f) {
            vecU = new float[]{1, 0};
            lenU = 1.0f;
        }
        vecU[0] /= lenU; vecU[1] /= lenU;

        float[] vecV = new float[]{-vecU[1], vecU[0]}; // 垂直轴

        // 投影并求极值
        float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE;
        float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
        for (float[] p : points) {
            float dx = p[0] - cx;
            float dy = p[1] - cy;
            float projU = dx * vecU[0] + dy * vecU[1];
            float projV = dx * vecV[0] + dy * vecV[1];
            if (projU < minU) minU = projU;
            if (projU > maxU) maxU = projU;
            if (projV < minV) minV = projV;
            if (projV > maxV) maxV = projV;
        }
        float halfW = (maxU - minU) / 2.0f;
        float halfH = (maxV - minV) / 2.0f;
        float angle = (float) Math.atan2(vecU[1], vecU[0]);

        // 中心调整到投影中点
        float newCx = cx + (maxU + minU) * 0.5f * vecU[0] + (maxV + minV) * 0.5f * vecV[0];
        float newCy = cy + (maxU + minU) * 0.5f * vecU[1] + (maxV + minV) * 0.5f * vecV[1];

        return new OBB(newCx, newCy, halfW, halfH, angle, vecU, vecV);
    }

    /**
     * 判断点 (px, py) 是否在 OBB 内部（含边界）。
     */
    public boolean contains(float px, float py) {
        float dx = px - centerX;
        float dy = py - centerY;
        float projU = dx * axisU[0] + dy * axisU[1];
        float projV = dx * axisV[0] + dy * axisV[1];
        return Math.abs(projU) <= halfWidth && Math.abs(projV) <= halfHeight;
    }

    /**
     * 获取 OBB 的轴对齐边界框（AABB），用于分配快照缓冲区。
     */
    public int[] getAABB() {
        float[] cornersX = new float[4];
        float[] cornersY = new float[4];
        float[][] signs = {{1,1}, {1,-1}, {-1,1}, {-1,-1}};
        for (int i = 0; i < 4; i++) {
            float sx = signs[i][0] * halfWidth;
            float sy = signs[i][1] * halfHeight;
            cornersX[i] = centerX + sx * axisU[0] + sy * axisV[0];
            cornersY[i] = centerY + sx * axisU[1] + sy * axisV[1];
        }
        int minX = (int) Math.floor(min(cornersX));
        int maxX = (int) Math.ceil(max(cornersX));
        int minY = (int) Math.floor(min(cornersY));
        int maxY = (int) Math.ceil(max(cornersY));
        return new int[]{minX, minY, maxX - minX, maxY - minY};
    }

    private static float min(float[] arr) {
        float m = arr[0];
        for (int i = 1; i < arr.length; i++) if (arr[i] < m) m = arr[i];
        return m;
    }
    private static float max(float[] arr) {
        float m = arr[0];
        for (int i = 1; i < arr.length; i++) if (arr[i] > m) m = arr[i];
        return m;
    }

    /**
     * 保存 OBB 快照，返回 float[] 数据，并通过 outSize 传出宽度和高度。
     */
    public static float[] saveSnapshot(float[] src, int canvasW, int canvasH, OBB obb, int[] outSize) {
        int[] aabb = obb.getAABB();
        int startX = aabb[0];
        int startY = aabb[1];
        int bw = aabb[2];
        int bh = aabb[3];
        float[] dst = new float[bw * bh * 4];
        for (int row = 0; row < bh; row++) {
            for (int col = 0; col < bw; col++) {
                int px = startX + col;
                int py = startY + row;
                boolean inside = (px >= 0 && px < canvasW && py >= 0 && py < canvasH) && obb.contains(px, py);
                int dstIdx = (row * bw + col) * 4;
                if (inside) {
                    int srcIdx = (py * canvasW + px) * 4;
                    System.arraycopy(src, srcIdx, dst, dstIdx, 4);
                } else {
                    dst[dstIdx] = 0.0f;
                    dst[dstIdx + 1] = 0.0f;
                    dst[dstIdx + 2] = 0.0f;
                    dst[dstIdx + 3] = 0.0f;
                }
            }
        }
        outSize[0] = bw;
        outSize[1] = bh;
        return dst;
    }

    /**
     * 将快照数据恢复到目标数组的 OBB 区域内。
     */
    public static void restoreSnapshot(float[] dest, int canvasW, int canvasH,
                                       float[] snapshot, int snapW, int snapH, OBB obb) {
        int[] aabb = obb.getAABB();
        int startX = aabb[0];
        int startY = aabb[1];
        for (int row = 0; row < snapH; row++) {
            for (int col = 0; col < snapW; col++) {
                int px = startX + col;
                int py = startY + row;
                if (px >= 0 && px < canvasW && py >= 0 && py < canvasH && obb.contains(px, py)) {
                    int srcIdx = (row * snapW + col) * 4;
                    int dstIdx = (py * canvasW + px) * 4;
                    System.arraycopy(snapshot, srcIdx, dest, dstIdx, 4);
                }
            }
        }
    }

    /**
     * 将快照数据写入输出流（使用游程编码压缩）。
     */
    public static void writeSnapshot(float[] data, int width, int height, OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(width);
        dos.writeInt(height);
        // 使用 Arrays 的 RLE 压缩代替逐元素写入
        Arrays.writeRLE(data, dos);
        dos.flush();
    }

    /**
     * 从输入流读取快照数据（自动解压游程编码）。
     */
    public static SnapshotData readSnapshot(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        int w = dis.readInt();
        int h = dis.readInt();
        // 使用 Arrays 的 RLE 解压
        float[] data = Arrays.readRLE(dis);
        return new SnapshotData(data, w, h);
    }

    /**
     * 将快照数据保存到临时文件（内部调用 writeSnapshot，自动 RLE 压缩）。
     */
    public static String writeSnapshotToTempFile(float[] data, int width, int height) {
        try {
            File tempFile = File.createTempFile("snapshot_", ".dat");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                writeSnapshot(data, width, height, fos);
            }
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("保存快照到临时文件失败", e);
        }
    }

    /**
     * 从临时文件路径读取快照数据（自动 RLE 解压）。
     */
    public static SnapshotData readSnapshotFromTempFile(String path) {
        try (FileInputStream fis = new FileInputStream(path)) {
            return readSnapshot(fis);
        } catch (IOException e) {
            throw new RuntimeException("从临时文件读取快照失败: " + path, e);
        }
    }

    /**
     * 快照数据容器
     */
    public static class SnapshotData {
        public final float[] data;
        public final int width;
        public final int height;

        public SnapshotData(float[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }
}