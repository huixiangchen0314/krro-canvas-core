package top.kzre.krro.canvas.core.layer;

import top.kzre.colorutils.blend.Blends;
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

    // ──────────── 对外 API ────────────
    public static void blit(TiledCanvas dst, int w, int h, TiledCanvas src,
                            float[] matrix2d, String blendMode, float opacity) {
        blit(dst, w, h, src, matrix2d, blendMode, opacity, true);
    }

    public static void blit(TiledCanvas dst, int canvasW, int canvasH, TiledCanvas src,
                            float[] matrix2d, String blendMode, float opacity,
                            boolean subpixel) {
        blit(dst, canvasW, canvasH, src, matrix2d, blendMode, opacity,
                AntiAlias.ssaa2x2(), null, subpixel);
    }

    @Deprecated
    public static void blit(TiledCanvas dst, int canvasW, int canvasH, TiledCanvas src,
                            float[] matrix2d, String blendMode, float opacity,
                            AntiAlias aa, Set<Long> dirtyTiles, boolean subpixel) {
        blit(dst, canvasW, canvasH, src, matrix2d, blendMode, opacity, dirtyTiles, subpixel);
    }

    public static void blit(TiledCanvas dst, int canvasW, int canvasH, TiledCanvas src,
                            float[] matrix2d, String blendMode, float opacity,
                             Set<Long> dirtyTiles, boolean subpixel) {
        if (dirtyTiles != null && dirtyTiles.isEmpty()) return;

        final int tileSize = dst.getTileSize();
        final int channels = dst.getChannels();
        assert channels == 4;

        // 收集所有需要处理的瓦片（若未指定则默认全图）
        Set<Long> tiles = dirtyTiles;
        if (tiles == null) {
            tiles = new HashSet<>();
            int startTx = TiledCanvas.tileX(0, tileSize);
            int endTx   = TiledCanvas.tileX(canvasW - 1, tileSize);
            int startTy = TiledCanvas.tileY(0, tileSize);
            int endTy   = TiledCanvas.tileY(canvasH - 1, tileSize);
            for (int ty = startTy; ty <= endTy; ty++)
                for (int tx = startTx; tx <= endTx; tx++)
                    tiles.add(TiledCanvas.pack(tx, ty));
        }

        boolean identity = KMath.mat2dIsIdentity(matrix2d);

        // 构建任务列表
        List<RecursiveAction> tasks = new ArrayList<>(tiles.size());
        if (identity) {
            for (long key : tiles) {
                tasks.add(new BlitTaskIdentity(dst, canvasW, canvasH, src, blendMode, opacity, tileSize, key));
            }
        } else {
            float[] inv = KMath.mat2dInv(matrix2d);
            if (inv == null) return;
            for (long key : tiles) {
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

            FloatsHolder holder = PoolManagers.floats().getHolder();
            FloatsPool pool = holder.getPool(4);
            float[] srcPixel = pool.acquire();
            float[] bgPixel  = pool.acquire();
            float[] res = pool.acquire();
            try {
                for (int row = 0; row < bh; row++) {
                    int localY = TiledCanvas.localY(y0 + row, tileSize);
                    int rowBase = localY * tileSize * channels;
                    for (int col = 0; col < bw; col++) {
                        int localX = TiledCanvas.localX(x0 + col, tileSize);
                        int idx = rowBase + localX * channels;

                        System.arraycopy(dstData, idx, bgPixel, 0, 4);
                        System.arraycopy(srcData, idx, srcPixel, 0, 4);
                        srcPixel[3] *= opacity;

                        if (srcPixel[3] < ALPHA_THRESHOLD) continue;

                        Blends.blendWithAlpha(blendMode,res, bgPixel, srcPixel);
                        System.arraycopy(res, 0, dstData, idx, 4);
                    }
                }
            }finally {
                pool.release(srcPixel);
                pool.release(bgPixel);
                pool.release(res);
            }
        }
    }


    // ──────────── 瓦片并行任务（一般变换） ────────────
    private static final class BlitTaskTransform extends RecursiveAction {
        private final TiledCanvas dst;
        private final int w, h;
        private final TiledCanvas src;
        private final float[] inv;
        private final String blendMode;
        private final float opacity;
        private final int tileSize;
        private final boolean subpixel;
        private final long key;

        BlitTaskTransform(TiledCanvas dst, int w, int h, TiledCanvas src,
                          float[] inv, String blendMode, float opacity,
                          int tileSize, boolean subpixel, long key) {
            this.dst = dst;
            this.w = w;
            this.h = h;
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
            blitGeneral(dst, w, h, src, inv, blendMode, opacity, tileSize, subpixel, key);
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
                int localY = TiledCanvas.localY(worldY, tileSize);
                int rowBase = localY * tileSize * channels;

                float srcX = a * (x0 + 0.5f) + b * (worldY + 0.5f) + c;
                float srcY = d * (x0 + 0.5f) + e * (worldY + 0.5f) + f;
                float stepX = a;
                float stepY = d;

                for (int x = 0; x < bw; x++) {
                    int worldX = x0 + x;
                    int localX = TiledCanvas.localX(worldX, tileSize);
                    int dstIdx = rowBase + localX * channels;

                    if (subpixel) {
                        // ---- 内联双线性采样（从瓦片数组直接读取） ----
                        int srcX0 = (int) Math.floor(srcX);
                        int srcY0 = (int) Math.floor(srcY);
                        float fx = srcX - srcX0;
                        float fy = srcY - srcY0;

                        // 读取四个角点（快速瓦片访问）
                        readPixelFast(src, srcX0, srcY0, s00);
                        readPixelFast(src, srcX0 + 1, srcY0, s10);
                        readPixelFast(src, srcX0, srcY0 + 1, s01);
                        readPixelFast(src, srcX0 + 1, srcY0 + 1, s11);

                        // 预乘
                        premultiply(s00);
                        premultiply(s10);
                        premultiply(s01);
                        premultiply(s11);

                        if (s00[3] == 0 && s10[3] == 0 && s01[3] == 0 && s11[3] == 0) {
                            srcColor[0] = srcColor[1] = srcColor[2] = srcColor[3] = 0f;
                        } else {
                            for (int i = 0; i < 4; i++) {
                                float top = s00[i] + (s10[i] - s00[i]) * fx;
                                float bot = s01[i] + (s11[i] - s01[i]) * fx;
                                srcColor[i] = top + (bot - top) * fy;
                            }
                            // 插值后反预乘，恢复 straight alpha
                            float aCol = srcColor[3];
                            if (aCol > 1e-6f) {
                                float invA = 1.0f / aCol;
                                srcColor[0] *= invA;
                                srcColor[1] *= invA;
                                srcColor[2] *= invA;
                            } else {
                                Arrays.fill(srcColor, 0f);
                            }
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

                    Blends.blendWithAlpha(blendMode,
                            dstData, dstIdx,
                            dstData, dstIdx,
                            srcColor, 0);

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
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }
        int tileSize = canvas.getTileSize();
        int tx = TiledCanvas.tileX(x, tileSize);
        int ty = TiledCanvas.tileY(y, tileSize);
        Tile tile = canvas.getTile(tx, ty);
        if (tile == null) {
            out[0] = out[1] = out[2] = out[3] = 0f;
            return;
        }
        float[] data = tile.getPixelsSnapshot();
        int lx = TiledCanvas.localX(x, tileSize);
        int ly = TiledCanvas.localY(y, tileSize);
        int idx = (ly * tileSize + lx) * 4;
        out[0] = data[idx];
        out[1] = data[idx + 1];
        out[2] = data[idx + 2];
        out[3] = data[idx + 3];
    }


    private static void premultiply(float[] pixel) {
        if (pixel.length >= 4) {
            float a = pixel[3];
            if (a < 1e-6f) {
                pixel[0] = pixel[1] = pixel[2] = pixel[3] = 0f;
            } else {
                pixel[0] *= a;
                pixel[1] *= a;
                pixel[2] *= a;
            }
        }
    }
}