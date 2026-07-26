package top.kzre.krro.canvas.core.layer;

import top.kzre.colorutils.blend.Blends;
import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.FloatsPools;
import top.kzre.krro.util.tile.AntiAlias;
import top.kzre.krro.util.tile.Canvas;
import top.kzre.krro.util.tile.CanvasUtils;
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
        if (inv == null) {
            System.out.println("layer matrix cannot inv.");
            return;
        }

// 用于双线性采样的临时数组（池化）
        float[] sample00 = pool4f.acquire();
        float[] sample10 = pool4f.acquire();
        float[] sample01 = pool4f.acquire();
        float[] sample11 = pool4f.acquire();

        float[] srcColor = pool4f.acquire();   // 源像素采样结果
        float[] blended  = pool4f.acquire();   // aa.read 输出

// 分配一个输出缓冲（大小同瓦片），用于收集混合结果后统一写回
        float[] outTile = tilePool.acquire();  // tileBufLen 大小

        try {
            for (long key : tiles) {
                int tileX = TiledCanvas.unpackTx(key);
                int tileY = TiledCanvas.unpackTy(key);
                int x0 = tileX * tileSize, y0 = tileY * tileSize;
                int x1 = Math.min(x0 + tileSize, w);
                int y1 = Math.min(y0 + tileSize, h);
                int bw = x1 - x0, bh = y1 - y0;
                if (bw <= 0 || bh <= 0) continue;

                // 无需预先读取目标瓦片，aa.read 会直接从 dst 画布获取背景色

                for (int y = 0; y < bh; y++) {
                    int worldY = y0 + y;
                    for (int x = 0; x < bw; x++) {
                        int worldX = x0 + x;

                        // 1. 逆变换到源图坐标（像素中心采样，提升质量）
                        float sx = inv[0] * (worldX + 0.5f) + inv[2] * (worldY + 0.5f) + inv[4];
                        float sy = inv[1] * (worldX + 0.5f) + inv[3] * (worldY + 0.5f) + inv[5];

                        // 2. 双线性采样源画布颜色（无抗锯齿，直接读取）
                        CanvasUtils.bilinearSample(src, sx, sy, srcColor, sample00, sample10, sample01, sample11);
                        // 乘上全局透明度
                        srcColor[3] *= opacity;
                        if (srcColor[3] == 0f) {
                            // 完全透明则保留背景，无需混合
                            // 但由于 outTile 未初始化，需手动复制背景色
                            // 这里直接从 dst 读取背景色写入 outTile
                            dst.getPixel(worldX, worldY, blended);
                            int idx = (y * bw + x) * channels;
                            System.arraycopy(blended, 0, outTile, idx, channels);
                            continue;
                        }

                        // 3. 使用 AntiAlias 将前景色混合到目标画布上
                        //    aa.read 内部会以 dst 为背景、srcColor 为前景，
                        //    在 (worldX+0.5, worldY+0.5) 处按子像素覆盖度混合
                        aa.read(blended, dst, worldX + 0.5, worldY + 0.5, srcColor);

                        // 4. 将混合结果存入输出瓦片
                        int idx = (y * bw + x) * channels;
                        System.arraycopy(blended, 0, outTile, idx, channels);
                    }
                }

                // 5. 一次性写回目标画布
                dst.writeBytes(outTile, 0, x0, y0, bw, bh, bw);
            }
        } finally {
            tilePool.release(outTile);
            pool4f.release(sample00);
            pool4f.release(sample10);
            pool4f.release(sample01);
            pool4f.release(sample11);
            pool4f.release(srcColor);
            pool4f.release(blended);
        }

    }
}