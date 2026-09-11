package top.kzre.krro.canvas.core.layer;

import top.kzre.colorutils.blend.Blends;
import top.kzre.colorutils.color.RGB;
import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.pool.FloatsHolder;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.PoolManagers;
import top.kzre.krro.util.tile.*;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

/**
 * 像素级混合器 —— 将源画布通过仿射变换混合到目标画布。
 * 使用专用 {@link ForkJoinPool} 并行处理瓦片，工作窃取调度。
 * 不进行基于图像尺寸的裁剪，完全由底层画布处理越界坐标。
 */
public final class PixelBlitter {

   private static final FloatsPool pool4f;

   static {
       FloatsHolder holder = PoolManagers.floats().getHolder();
       pool4f = holder.getPool(4);
   }

    private static final float ALPHA_THRESHOLD = 1e-6f;

    @Deprecated
    public static void blit(TiledCanvas dst, int canvasW, int canvasH, TiledCanvas src,
                            float[] matrix2d, String blendMode, float opacity,
                            AntiAlias aa, Set<Long> dirtyTiles, boolean subpixel) {
        blit(dst, canvasW, canvasH, src, matrix2d, blendMode, opacity, dirtyTiles, subpixel);
    }

    public static void blit(TiledCanvas dst, int canvasW, int canvasH, TiledCanvas src,
                            float[] matrix2d, String blendMode, float opacity,
                             Set<Long> dirtyTiles, boolean subpixel) {
        if (dirtyTiles == null) {
            throw new IllegalArgumentException("dirtyTiles should not be null");
        }

        if (dirtyTiles.isEmpty()) return;

        final int tileSize = dst.getTileSize();
        final int channels = dst.getChannels();
        assert channels == 4;

        // 收集所有需要处理的瓦片（若未指定则默认全图）

        boolean identity = KMath.mat2dIsIdentity(matrix2d);

        // 构建任务列表
        List<RecursiveAction> tasks = new ArrayList<>(dirtyTiles.size());
        if (identity) {
            for (long key : dirtyTiles) {
                tasks.add(new BlitTaskIdentity(dst, canvasW, canvasH, src, blendMode, opacity, tileSize, key));
            }
        } else {
            float[] inv = KMath.mat2dInv(matrix2d);
            if (inv == null) return;
            for (long key : dirtyTiles) {
                tasks.add(new BlitTaskTransform(dst, canvasW, canvasH, src, inv, blendMode, opacity,
                        tileSize, subpixel, key));
            }
        }

        // 并行执行所有任务
        try {
            ForkJoinTask.invokeAll(tasks);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ──────────── 瓦片并行任务（单位矩阵） ────────────
    private static final class BlitTaskIdentity extends RecursiveAction {
        private final TiledCanvas dst;
        private final int w, h;
        private final TiledCanvas src;
        private final String blendMode;
        private final float opacity;
        private final int tileSize;
        private final long key;

        BlitTaskIdentity(TiledCanvas dst, int w, int h, TiledCanvas src,
                         String blendMode, float opacity, int tileSize, long key) {
            this.dst = dst;
            this.w = w;
            this.h = h;
            this.src = src;
            this.blendMode = blendMode;
            this.opacity = opacity;
            this.tileSize = tileSize;
            this.key = key;
        }

        @Override
        protected void compute() {
            int tileX = TiledCanvas.unpackTx(key);
            int tileY = TiledCanvas.unpackTy(key);
            int x0 = tileX * tileSize;
            int y0 = tileY * tileSize;
            int x1 = Math.min(x0 + tileSize, w);
            int y1 = Math.min(y0 + tileSize, h);
            int bw = x1 - x0, bh = y1 - y0;
            if (bw <= 0 || bh <= 0) return;

            Tile srcTile = src.getTile(tileX, tileY);
            if (srcTile == null) return;

            Tile dstTile = dst.ensureTile(tileX, tileY);
            float[] srcData = srcTile.getPixelsSnapshot();
            float[] dstData = dstTile.getPixelsForWrite();
            int channels = dst.getChannels();

            float[] srcPixel = pool4f.acquire();
            float[] res = pool4f.acquire();
            try {
                if (opacity >= 1.0f - ALPHA_THRESHOLD
                        && Blends.NORMAL.equals(blendMode)
                        && isRegionOpaque(srcData, tileSize, x0, y0, bw, bh)) {
                    if (bw == tileSize && bh == tileSize) {
                        // 整 tile：一次 memcpy
                        System.arraycopy(srcData, 0, dstData, 0, srcData.length);
                    } else {
                        // 边缘 tile：逐行 memcpy
                        int localX0 = TiledCanvas.local(x0, tileSize);
                        int bytesPerRow = bw * channels;
                        for (int row = 0; row < bh; row++) {
                            int localY = TiledCanvas.local(y0 + row, tileSize);
                            int rowBase = (localY * tileSize + localX0) * channels;
                            System.arraycopy(srcData, rowBase, dstData, rowBase, bytesPerRow);
                        }
                    }
                }else {
                    for (int row = 0; row < bh; row++) {
                        int localY = TiledCanvas.local(y0 + row, tileSize);
                        int rowBase = localY * tileSize * channels;
                        for (int col = 0; col < bw; col++) {
                            int localX = TiledCanvas.local(x0 + col, tileSize);
                            int idx = rowBase + localX * channels;

                            // 原图层不属于我们，必须拷贝
                            System.arraycopy(srcData, idx, srcPixel, 0, 4);
                            srcPixel[3] *= opacity;

                            if (srcPixel[3] < ALPHA_THRESHOLD) continue;

                            // 原地合成
                            Blends.blendWithAlpha(blendMode, dstData, idx, dstData, idx, srcPixel, 0);
                        }
                    }
                }

            }finally {
                pool4f.release(srcPixel);
                pool4f.release(res);
            }
        }
    }


    // ──────────── 瓦片并行任务（一般变换） ────────────
    private static final class BlitTaskTransform extends RecursiveAction {
        private final TiledCanvas dst;
        private final int canvasW, canvasH;
        private final TiledCanvas src;
        private final float[] inv;
        private final String blendMode;
        private final float opacity;
        private final int tileSize;
        private final boolean subpixel;
        private final long key;

        BlitTaskTransform(TiledCanvas dst, int canvasW, int canvasH, TiledCanvas src,
                          float[] inv, String blendMode, float opacity,
                          int tileSize, boolean subpixel, long key) {
            this.dst = dst;
            this.canvasW = canvasW;
            this.canvasH = canvasH;
            this.src = src;
            this.inv = inv;
            this.blendMode = blendMode;
            this.opacity = opacity;
            this.tileSize = tileSize;
            this.subpixel = subpixel;
            this.key = key;
        }

        @Override
        protected void compute() {
            blitGeneral(dst, canvasW, canvasH, src, inv, blendMode, opacity, tileSize, subpixel, key);
        }
    }

    // ──────────── 一般变换（旋转/缩放/亚像素） ────────────
    private static void blitGeneral(TiledCanvas dst, int w, int h, TiledCanvas src,
                                    float[] inv, String blendMode, float opacity,
                                    int tileSize, boolean subpixel,
                                    long key) {
        int tileX = TiledCanvas.unpackTx(key);
        int tileY = TiledCanvas.unpackTy(key);
        int x0 = tileX * tileSize, y0 = tileY * tileSize;
        int x1 = Math.min(x0 + tileSize, w);
        int y1 = Math.min(y0 + tileSize, h);
        int bw = x1 - x0, bh = y1 - y0;
        if (bw <= 0 || bh <= 0) return;

        Tile dstTile = dst.ensureTile(tileX, tileY);
        float[] dstData = dstTile.getPixelsForWrite();
        int channels = dst.getChannels();


        float[] srcColor = pool4f.acquire();
        float[] s00 = subpixel ? pool4f.acquire() : null;
        float[] s10 = subpixel ? pool4f.acquire() : null;
        float[] s01 = subpixel ? pool4f.acquire() : null;
        float[] s11 = subpixel ? pool4f.acquire() : null;
        try {
            float a = inv[0], b = inv[2], c = inv[4];
            float d = inv[1], e = inv[3], f = inv[5];

            for (int y = 0; y < bh; y++) {
                int worldY = y0 + y;
                int localY = TiledCanvas.local(worldY, tileSize);
                int rowBase = localY * tileSize * channels;

                float srcX = a * (x0 + 0.5f) + b * (worldY + 0.5f) + c;
                float srcY = d * (x0 + 0.5f) + e * (worldY + 0.5f) + f;
                float stepX = a;
                float stepY = d;

                for (int x = 0; x < bw; x++) {
                    int worldX = x0 + x;
                    int localX = TiledCanvas.local(worldX, tileSize);
                    int dstIdx = rowBase + localX * channels;

                    if (subpixel) {
                        // ---- 源画布颜色双线性采样（从瓦片数组直接读取） ----
                        int srcX0 = (int) Math.floor(srcX);
                        int srcY0 = (int) Math.floor(srcY);
                        float fx = srcX - srcX0;
                        float fy = srcY - srcY0;

                        // 读取源画布四个角点（快速瓦片访问）
                        readPixelFast(src, srcX0, srcY0, s00);
                        readPixelFast(src, srcX0 + 1, srcY0, s10);
                        readPixelFast(src, srcX0, srcY0 + 1, s01);
                        readPixelFast(src, srcX0 + 1, srcY0 + 1, s11);

                        // 预乘
                        RGB.preMultiAlpha(s00);
                        RGB.preMultiAlpha(s10);
                        RGB.preMultiAlpha(s01);
                        RGB.preMultiAlpha(s11);

                        if (s00[3] == 0 && s10[3] == 0 && s01[3] == 0 && s11[3] == 0) {
                            Arrays.fill(srcColor, 0f);
                        } else {
                            for (int i = 0; i < 4; i++) {
                                float top = s00[i] + (s10[i] - s00[i]) * fx;
                                float bot = s01[i] + (s11[i] - s01[i]) * fx;
                                srcColor[i] = top + (bot - top) * fy;
                            }
                            // 插值后反预乘，恢复 straight alpha
                            RGB.invPreMultiAlpha(srcColor);
                        }
                    } else {
                        // 最近邻采样
                        int srcXInt = (int) Math.floor(srcX + 0.5f);
                        int srcYInt = (int) Math.floor(srcY + 0.5f);
                        readPixelFast(src, srcXInt, srcYInt, srcColor);
                    }

                    float aSrc = srcColor[3] * opacity;
                    if (aSrc < ALPHA_THRESHOLD) {
                        srcX += stepX;
                        srcY += stepY;
                        continue;
                    }
                    srcColor[3] = aSrc;

                    // ═══════════ 快路径：覆盖 ═══════════
                    if (aSrc >= 1.0f - ALPHA_THRESHOLD && Blends.NORMAL.equals(blendMode)) {
                        System.arraycopy(srcColor, 0, dstData, dstIdx, 3);
                        dstData[dstIdx + 3] = 1.0f;
                    } else {
                        // ═══════════ 慢路径 ═══════════
                        srcColor[3] = aSrc;
                        Blends.blendWithAlpha(blendMode,
                                dstData, dstIdx,
                                dstData, dstIdx,
                                srcColor, 0);
                    }


                    srcX += stepX;
                    srcY += stepY;
                }
            }
        } finally {
            pool4f.release(srcColor);
            if (subpixel) {
                pool4f.release(s00);
                pool4f.release(s10);
                pool4f.release(s01);
                pool4f.release(s11);
            }
        }
    }

    /**
     * 从 TiledCanvas 快速读取一个像素，直接从瓦片数组获取，无方法调用开销。
     */
    private static void readPixelFast(TiledCanvas canvas, int x, int y, float[] out) {
        if (x < 0 || y < 0) {
            Arrays.fill(out, 0);
            return;
        }
        int tileSize = canvas.getTileSize();
        int tx = TiledCanvas.tile(x, tileSize);
        int ty = TiledCanvas.tile(y, tileSize);
        Tile tile = canvas.getTile(tx, ty);
        if (tile == null) {
            Arrays.fill(out, 0f);
            return;
        }
        float[] data = tile.getPixelsSnapshot();
        int lx = TiledCanvas.local(x, tileSize);
        int ly = TiledCanvas.local(y, tileSize);
        int idx = (ly * tileSize + lx) * 4;
        System.arraycopy(data, idx, out, 0, 4);
    }
    /**
     * 检测源 tile 在指定区域内是否完全不透明（alpha ≥ 1 - ε）。
     * 只扫描有效区域（边缘 tile 不会扫全 tile）。
     *
     * 若命中第一个半透明像素立即返回 false，半透明 tile 检测很快。
     * 不透明 tile 需要扫完整个区域，但之后可走 memcpy 快路径。
     */
    private static boolean isRegionOpaque(float[] data, int tileSize,
                                          int x0, int y0, int bw, int bh) {
        int localX0 = TiledCanvas.local(x0, tileSize);
        for (int row = 0; row < bh; row++) {
            int localY = TiledCanvas.local(y0 + row, tileSize);
            int rowBase = localY * tileSize * 4;
            int startIdx = rowBase + localX0 * 4 + 3;
            int endIdx = startIdx + bw * 4;
            for (int i = startIdx; i < endIdx; i += 4) {
                if (data[i] < 1.0f - ALPHA_THRESHOLD) return false;
            }
        }
        return true;
    }
}