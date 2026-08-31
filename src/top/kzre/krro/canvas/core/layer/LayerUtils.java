package top.kzre.krro.canvas.core.layer;

import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.HashSet;
import java.util.Set;

public final class LayerUtils {
    private static final float EPSILON = 1e-9f;

    private LayerUtils() {}

    /**
     * 将局部脏瓦片集合通过仿射变换映射到目标空间（如世界坐标或视口坐标）。
     * 使用精确的瓦片相交检测，支持旋转和缩放。
     */
    public static Set<Long> transformTiles(
            Set<Long> localDirtyTiles,
            int tileSize,
            float[] mat2d) {

        if (localDirtyTiles == null) {
            return null;
        }
        if (localDirtyTiles.isEmpty()) {
            return new HashSet<>();
        }
        if (mat2d == null || mat2d.length < 6) {
            return new HashSet<>(localDirtyTiles);
        }



        Set<Long> result = new HashSet<>();
        for (Long tileKey : localDirtyTiles) {
            int localTX = TiledCanvas.unpackTx(tileKey);
            int localTY = TiledCanvas.unpackTy(tileKey);

            // 四个角点（逻辑坐标）
            float x1 = localTX * tileSize;
            float y1 = localTY * tileSize;
            float x2 = x1 + tileSize;
            float y2 = y1 + tileSize;

            // 变换四个角点到目标空间
            float[] p00 = KMath.mat2dTransformPoint(mat2d, x1, y1);
            float[] p10 = KMath.mat2dTransformPoint(mat2d, x2, y1);
            float[] p01 = KMath.mat2dTransformPoint(mat2d, x1, y2);
            float[] p11 = KMath.mat2dTransformPoint(mat2d, x2, y2);

            // 计算所有角点所在的瓦片坐标范围
            int minTx = Integer.MAX_VALUE, maxTx = Integer.MIN_VALUE;
            int minTy = Integer.MAX_VALUE, maxTy = Integer.MIN_VALUE;

            for (float[] p : new float[][]{p00, p10, p01, p11}) {
                int tx = (int) Math.floor(p[0] / tileSize);
                int ty = (int) Math.floor(p[1] / tileSize);
                if (tx < minTx) minTx = tx;
                if (tx > maxTx) maxTx = tx;
                if (ty < minTy) minTy = ty;
                if (ty > maxTy) maxTy = ty;
            }

            // 添加矩形范围内的所有瓦片
            for (int ty = minTy; ty <= maxTy; ty++) {
                for (int tx = minTx; tx <= maxTx; tx++) {
                    result.add(TiledCanvas.pack(tx, ty));
                }
            }
        }
        return result;
    }

}