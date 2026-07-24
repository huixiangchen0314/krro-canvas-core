package top.kzre.krro.canvas.core.layer;

import top.kzre.colorutils.blend.Blends;
import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.tile.AntiAlias;
import top.kzre.krro.util.tile.Canvas;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.HashSet;
import java.util.Set;

/**
 * 像素级混合器，将源画布（Canvas）通过仿射变换混合到目标画布，支持脏瓦片与抗锯齿采样。
 * 所有临时数组均通过对象池管理，避免 GC 抖动。
 */
public final class PixelBlitter {
    private static final FloatsPool pool4f = FloatsPools.getPool(4);

    /** 默认使用 SSAA2x2 抗锯齿 */
    public static void blit(Canvas dst, int w, int h, Canvas src,
                            float[] matrix2d, String blendMode, float opacity) {
        blit(dst, w, h, src, matrix2d, blendMode, opacity, AntiAlias.ssaa2x2(), null);
    }


    /**
     * @param dst        目标画布
     * @param w          图像宽度
     * @param h          图像高度
     * @param src        源画布
     * @param matrix2d   2D 仿射矩阵 [a,b,c,d,tx,ty]
     * @param blendMode  混合模式
     * @param opacity    不透明度
     * @param aa         抗锯齿采样策略
     * @param dirtyTiles 脏瓦片集合（null 表示全图），空集合直接返回
     */
    public static void blit(Canvas dst, int w, int h, Canvas src,
                            float[] matrix2d, String blendMode, float opacity,
                            AntiAlias aa, Set<Long> dirtyTiles) {
        if (dirtyTiles != null && dirtyTiles.isEmpty()) {
            return;
        }

        int tileSize = dst.getTileSize();
        int channels = dst.getChannels(); // 固定为 4

        // 收集目标瓦片（默认所有可能瓦片）
        Set<Long> tiles = dirtyTiles;
        if (tiles == null) {
            tiles = new HashSet<>();
            int startTx = TiledCanvas.tileX(0, tileSize);
            int endTx   = TiledCanvas.tileX(w - 1, tileSize);
            int startTy = TiledCanvas.tileY(0, tileSize);
            int endTy   = TiledCanvas.tileY(h - 1, tileSize);
            for (int ty = startTy; ty <= endTy; ty++) {
                for (int tx = startTx; tx <= endTx; tx++) {
                    tiles.add(TiledCanvas.pack(tx, ty));
                }
            }
        }

        // 单位矩阵快速路径
        float a = matrix2d[0], b = matrix2d[1], c = matrix2d[2],
                d = matrix2d[3], tx = matrix2d[4], ty = matrix2d[5];
        boolean identity = Math.abs(a - 1f) < 1e-5f && Math.abs(b) < 1e-5f &&
                Math.abs(c) < 1e-5f && Math.abs(d - 1f) < 1e-5f &&
                Math.abs(tx) < 1e-5f && Math.abs(ty) < 1e-5f;

        int tileBufLen = tileSize * tileSize * channels;
        FloatsPool tilePool = FloatsPools.getPool(tileBufLen);

        if (identity) {
            float[] srcTile = tilePool.acquire();
            float[] dstTile = tilePool.acquire();
            float[] srcPixel = pool4f.acquire();
            float[] bgPixel  = pool4f.acquire();
            try {
                for (long key : tiles) {
                    int tileX = TiledCanvas.unpackTx(key);
                    int tileY = TiledCanvas.unpackTy(key);
                    int x0 = tileX * tileSize, y0 = tileY * tileSize;
                    int x1 = Math.min(x0 + tileSize, w);
                    int y1 = Math.min(y0 + tileSize, h);
                    int bw = x1 - x0, bh = y1 - y0;
                    if (bw <= 0 || bh <= 0) continue;

                    dst.readBytes(dstTile, 0, x0, y0, bw, bh, bw);
                    src.readBytes(srcTile, 0, x0, y0, bw, bh, bw);

                    for (int i = 0; i < bw * bh * channels; i += channels) {
                        bgPixel[0] = dstTile[i];   bgPixel[1] = dstTile[i+1];
                        bgPixel[2] = dstTile[i+2]; bgPixel[3] = dstTile[i+3];

                        srcPixel[0] = srcTile[i];   srcPixel[1] = srcTile[i+1];
                        srcPixel[2] = srcTile[i+2]; srcPixel[3] = srcTile[i+3] * opacity;

                        if (srcPixel[3] == 0f) continue;

                        float[] blended = Blends.blendWithAlpha(blendMode, bgPixel, srcPixel);
                        dstTile[i]   = blended[0];
                        dstTile[i+1] = blended[1];
                        dstTile[i+2] = blended[2];
                        dstTile[i+3] = blended[3];
                    }
                    dst.writeBytes(dstTile, 0, x0, y0, bw, bh, bw);
                }
            } finally {
                tilePool.release(srcTile);
                tilePool.release(dstTile);
                pool4f.release(srcPixel);
                pool4f.release(bgPixel);
            }
            return;
        }

        // 一般变换路径
        float[] inv = KMath.mat2dInv(matrix2d);
        if (inv == null) return;

        float[] dstTile = tilePool.acquire();
        float[] srcColor = pool4f.acquire();   // 采样输出颜色
        float[] bgColor  = pool4f.acquire();   // 目标背景像素
        float[] sampleBuf = pool4f.acquire();  // 传递给 aa.read 的 color 参数，避免与 dst 共享
        try {
            for (long key : tiles) {
                int tileX = TiledCanvas.unpackTx(key);
                int tileY = TiledCanvas.unpackTy(key);
                int x0 = tileX * tileSize, y0 = tileY * tileSize;
                int x1 = Math.min(x0 + tileSize, w);
                int y1 = Math.min(y0 + tileSize, h);
                int bw = x1 - x0, bh = y1 - y0;
                if (bw <= 0 || bh <= 0) continue;

                dst.readBytes(dstTile, 0, x0, y0, bw, bh, bw);

                for (int y = 0; y < bh; y++) {
                    int worldY = y0 + y;
                    for (int x = 0; x < bw; x++) {
                        int worldX = x0 + x;
                        float sx = inv[0] * worldX + inv[2] * worldY + inv[4];
                        float sy = inv[1] * worldX + inv[3] * worldY + inv[5];

                        // 抗锯齿采样，使用独立的 sampleBuf 避免自我覆盖
                        aa.read(srcColor, src, sx, sy, sampleBuf);
                        float sa = srcColor[3] * opacity;
                        if (sa == 0f) continue;

                        int dstIdx = (y * bw + x) * channels;
                        bgColor[0] = dstTile[dstIdx];
                        bgColor[1] = dstTile[dstIdx+1];
                        bgColor[2] = dstTile[dstIdx+2];
                        bgColor[3] = dstTile[dstIdx+3];

                        srcColor[3] = sa;
                        float[] blended = Blends.blendWithAlpha(blendMode, bgColor, srcColor);
                        dstTile[dstIdx]   = blended[0];
                        dstTile[dstIdx+1] = blended[1];
                        dstTile[dstIdx+2] = blended[2];
                        dstTile[dstIdx+3] = blended[3];
                    }
                }
                dst.writeBytes(dstTile, 0, x0, y0, bw, bh, bw);
            }
        } finally {
            tilePool.release(dstTile);
            pool4f.release(srcColor);
            pool4f.release(bgColor);
            pool4f.release(sampleBuf);
        }
    }
}