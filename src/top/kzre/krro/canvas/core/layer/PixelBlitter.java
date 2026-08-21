package top.kzre.krro.canvas.core.layer;

import top.kzre.colorutils.blend.Blends;
import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.tile.*;

import java.util.*;
import java.util.concurrent.ForkJoinPool;

/**
 * 像素级混合器 —— 将源画布通过仿射变换混合到目标画布。
 * 使用专用 {@link ForkJoinPool} 并行处理瓦片，工作窃取调度。
 * 不进行基于图像尺寸的裁剪，完全由底层画布处理越界坐标。
 */
public final class PixelBlitter {

    private static final FloatsPool pool4f = FloatsPools.getPool(4);
    private static final float ALPHA_THRESHOLD = 1e-6f;
    private static final int PARRAL_TILES_THRESHOLD = 10;

    /** 专用渲染线程池，避免与公共池竞争 */
    private static final ForkJoinPool RENDER_POOL =
            new ForkJoinPool(Runtime.getRuntime().availableProcessors() + 2);

    // ──────────── 对外 API ────────────
    public static void blit(Canvas dst, int w, int h, Canvas src,
                            float[] matrix2d, String blendMode, float opacity) {
        blit(dst, w, h, src, matrix2d, blendMode, opacity, true);
    }

    public static void blit(Canvas dst, int w, int h, Canvas src,
                            float[] matrix2d, String blendMode, float opacity,
                            boolean subpixel) {
        blit(dst, w, h, src, matrix2d, blendMode, opacity,
                AntiAlias.ssaa2x2(), null, subpixel);
    }

    public static void blit(Canvas dst, int w, int h, Canvas src,
                            float[] matrix2d, String blendMode, float opacity,
                            AntiAlias aa, Set<Long> dirtyTiles, boolean subpixel) {
        if (dirtyTiles != null && dirtyTiles.isEmpty()) return;

        final int tileSize = dst.getTileSize();
        final int channels = dst.getChannels(); // 4

        // 收集所有需要处理的瓦片（若未指定则默认全图）
        Set<Long> tiles = dirtyTiles;
        if (tiles == null) {
            tiles = new HashSet<>();
            int startTx = TiledCanvas.tileX(0, tileSize);
            int endTx   = TiledCanvas.tileX(w - 1, tileSize);
            int startTy = TiledCanvas.tileY(0, tileSize);
            int endTy   = TiledCanvas.tileY(h - 1, tileSize);
            for (int ty = startTy; ty <= endTy; ty++)
                for (int tx = startTx; tx <= endTx; tx++)
                    tiles.add(TiledCanvas.pack(tx, ty));
        }

        float a = matrix2d[0], b = matrix2d[1], c = matrix2d[2],
                d = matrix2d[3], tx = matrix2d[4], ty = matrix2d[5];

        boolean identity = Math.abs(a - 1f) < 1e-5f && Math.abs(b) < 1e-5f &&
                Math.abs(c) < 1e-5f && Math.abs(d - 1f) < 1e-5f &&
                Math.abs(tx) < 1e-5f && Math.abs(ty) < 1e-5f;

        boolean translateOnly = Math.abs(a - 1f) < 1e-5f && Math.abs(b) < 1e-5f &&
                Math.abs(c) < 1e-5f && Math.abs(d - 1f) < 1e-5f;

        // 提前转为列表，便于并行
        List<Long> tileList = new ArrayList<>(tiles);

        // ========== 单位矩阵快速路径 ==========
        if (identity) {
            if (tileList.size() > PARRAL_TILES_THRESHOLD) {
                // 多瓦片并行
                try {
                    RENDER_POOL.submit(() ->
                            tileList.parallelStream().forEach(key ->
                                    blitIdentity(dst, w, h, src, blendMode, opacity, tileSize, key))
                    ).get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                // 单瓦片串行，复用池化数组
                float[] srcPixel = pool4f.acquire();
                float[] bgPixel  = pool4f.acquire();
                try {
                    for (long key : tileList) {
                        blitIdentitySerial(dst, w, h, src, blendMode, opacity, tileSize, key, srcPixel, bgPixel);
                    }
                } finally {
                    pool4f.release(srcPixel);
                    pool4f.release(bgPixel);
                }
            }
            return;
        }

        // ========== 非单位矩阵路径（并行或串行） ==========
        try {
            RENDER_POOL.submit(() -> {
                if (tileList.size() > PARRAL_TILES_THRESHOLD) {
                    if (!subpixel && translateOnly) {
                        tileList.parallelStream().forEach(key ->
                                blitTranslateNearest(dst, w, h, src, matrix2d,
                                        blendMode, opacity, aa, tileSize, key));
                    } else {
                        float[] inv = KMath.mat2dInv(matrix2d);
                        if (inv == null) return;
                        tileList.parallelStream().forEach(key ->
                                blitGeneral(dst, w, h, src, inv, blendMode,
                                        opacity, aa, tileSize, subpixel, key));
                    }
                } else {
                    // 单瓦片串行
                    for (long key : tileList) {
                        if (!subpixel && translateOnly) {
                            blitTranslateNearest(dst, w, h, src, matrix2d,
                                    blendMode, opacity, aa, tileSize, key);
                        } else {
                            float[] inv = KMath.mat2dInv(matrix2d);
                            if (inv == null) return;
                            blitGeneral(dst, w, h, src, inv, blendMode,
                                    opacity, aa, tileSize, subpixel, key);
                        }
                    }
                }
            }).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ──────────── 单位矩阵并行版 ────────────
    private static void blitIdentity(Canvas dst, int w, int h, Canvas src,
                                     String blendMode, float opacity,
                                     int tileSize, long key) {
        int tileX = TiledCanvas.unpackTx(key);
        int tileY = TiledCanvas.unpackTy(key);
        int x0 = tileX * tileSize, y0 = tileY * tileSize;
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

        float[] srcPixel = new float[4];
        float[] bgPixel  = new float[4];

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

                float[] res = Blends.blendWithAlpha(blendMode, bgPixel, srcPixel);
                System.arraycopy(res, 0, dstData, idx, 4);
            }
        }
    }

    // ──────────── 单位矩阵串行版（复用池化数组） ────────────
    private static void blitIdentitySerial(Canvas dst, int w, int h, Canvas src,
                                           String blendMode, float opacity,
                                           int tileSize, long key,
                                           float[] srcPixel, float[] bgPixel) {
        int tileX = TiledCanvas.unpackTx(key);
        int tileY = TiledCanvas.unpackTy(key);
        int x0 = tileX * tileSize, y0 = tileY * tileSize;
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

                float[] res = Blends.blendWithAlpha(blendMode, bgPixel, srcPixel);
                System.arraycopy(res, 0, dstData, idx, 4);
            }
        }
    }

    // ──────────── 纯平移 + 最近邻 ────────────
    private static void blitTranslateNearest(Canvas dst, int w, int h, Canvas src,
                                             float[] matrix2d, String blendMode,
                                             float opacity, AntiAlias aa,
                                             int tileSize, long key) {
        int tileX = TiledCanvas.unpackTx(key);
        int tileY = TiledCanvas.unpackTy(key);
        int x0 = tileX * tileSize, y0 = tileY * tileSize;
        int x1 = Math.min(x0 + tileSize, w);
        int y1 = Math.min(y0 + tileSize, h);
        int bw = x1 - x0, bh = y1 - y0;
        if (bw <= 0 || bh <= 0) return;

        float tx = matrix2d[4], ty = matrix2d[5];

        Tile dstTile = dst.ensureTile(tileX, tileY);
        float[] dstData = dstTile.getPixelsForWrite();
        int channels = dst.getChannels();

        float[] srcPixel = new float[4];
        float[] bgPixel  = new float[4];

        for (int y = 0; y < bh; y++) {
            int worldY = y0 + y;
            int localY = TiledCanvas.localY(worldY, tileSize);
            int rowBase = localY * tileSize * channels;
            for (int x = 0; x < bw; x++) {
                int worldX = x0 + x;
                int localX = TiledCanvas.localX(worldX, tileSize);
                int dstIdx = rowBase + localX * channels;

                float sx = worldX - tx;
                float sy = worldY - ty;
                int srcX = (int) Math.floor(sx + 0.5f);
                int srcY = (int) Math.floor(sy + 0.5f);

                src.getPixel(srcX, srcY, srcPixel);
                srcPixel[3] *= opacity;
                if (srcPixel[3] < ALPHA_THRESHOLD) continue;

                System.arraycopy(dstData, dstIdx, bgPixel, 0, 4);
                float[] res = Blends.blendWithAlpha(blendMode, bgPixel, srcPixel);
                System.arraycopy(res, 0, dstData, dstIdx, 4);
            }
        }
    }

    // ──────────── 一般变换（旋转/缩放/亚像素） ────────────
    private static void blitGeneral(Canvas dst, int w, int h, Canvas src,
                                    float[] inv, String blendMode, float opacity,
                                    AntiAlias aa, int tileSize, boolean subpixel,
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

        float[] srcColor = new float[4];
        float[] blended  = new float[4];
        float[] sample00 = null, sample10 = null, sample01 = null, sample11 = null;
        if (subpixel) {
            sample00 = new float[4]; sample10 = new float[4];
            sample01 = new float[4]; sample11 = new float[4];
        }

        for (int y = 0; y < bh; y++) {
            int worldY = y0 + y;
            int localY = TiledCanvas.localY(worldY, tileSize);
            int rowBase = localY * tileSize * channels;
            for (int x = 0; x < bw; x++) {
                int worldX = x0 + x;
                int localX = TiledCanvas.localX(worldX, tileSize);
                int dstIdx = rowBase + localX * channels;

                if (subpixel) {
                    float sx = inv[0] * (worldX + 0.5f) + inv[2] * (worldY + 0.5f) + inv[4];
                    float sy = inv[1] * (worldX + 0.5f) + inv[3] * (worldY + 0.5f) + inv[5];
                    CanvasUtils.bilinearSample(src, sx, sy, srcColor,
                            sample00, sample10, sample01, sample11);
                } else {
                    float sx = inv[0] * (worldX + 0.5f) + inv[2] * (worldY + 0.5f) + inv[4];
                    float sy = inv[1] * (worldX + 0.5f) + inv[3] * (worldY + 0.5f) + inv[5];
                    int srcX = (int) Math.floor(sx + 0.5f);
                    int srcY = (int) Math.floor(sy + 0.5f);
                    src.getPixel(srcX, srcY, srcColor);
                }

                srcColor[3] *= opacity;
                if (srcColor[3] < ALPHA_THRESHOLD) continue;

                aa.read(blended, dst, worldX + 0.5, worldY + 0.5, srcColor);
                System.arraycopy(blended, 0, dstData, dstIdx, 4);
            }
        }
    }
}