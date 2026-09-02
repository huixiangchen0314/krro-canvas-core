package top.kzre.krro.canvas.core.layer;

import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.HashSet;
import java.util.Set;

public final class LayerUtils {

    private static final float EPSILON = 1e-6f;

    private LayerUtils() {}

    public static Set<Long> dirtyTiles(int canvasW, int canvasH, int tileSize) {
        Set<Long> tiles = new HashSet<>();
        int minTx = TiledCanvas.tileX(0, tileSize);
        int maxTx = TiledCanvas.tileX(canvasW - 1, tileSize);
        int minTy = TiledCanvas.tileY(0, tileSize);
        int maxTy = TiledCanvas.tileY(canvasH - 1, tileSize);
        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                tiles.add(TiledCanvas.pack(tx, ty));
            }
        }
        return tiles;
    }

    /**
     * aabb 包围盒转 脏瓦片
     */
    public static Set<Long> aabbTiles(int tileSize,
                                      float minX, float minY,
                                      float maxX, float maxY) {
        Set<Long> result = new HashSet<>();
        if (minX > maxX || minY > maxY) {
            return result;
        }
        int minTx = (int) Math.floor(minX / tileSize);
        int maxTx = (int) Math.floor((maxX - 1e-12f) / tileSize);
        int minTy = (int) Math.floor(minY / tileSize);
        int maxTy = (int) Math.floor((maxY - 1e-12f) / tileSize);
        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                result.add(TiledCanvas.pack(tx, ty));
            }
        }
        return result;
    }

    public static Set<Long> transformTiles(
            Set<Long> localDirtyTiles,
            int tileSize,
            float[] mat2d) {

        if (localDirtyTiles == null || mat2d == null || mat2d.length < 6) {
            return null;
        }
        if (localDirtyTiles.isEmpty()) {
            return new HashSet<>();
        }

        Set<Long> result = new HashSet<>();
        for (Long tileKey : localDirtyTiles) {
            int localTX = TiledCanvas.unpackTx(tileKey);
            int localTY = TiledCanvas.unpackTy(tileKey);

            float x1 = localTX * tileSize;
            float y1 = localTY * tileSize;
            float x2 = x1 + tileSize;
            float y2 = y1 + tileSize;

            float[] p00 = KMath.mat2dTransformPoint(mat2d, x1, y1);
            float[] p10 = KMath.mat2dTransformPoint(mat2d, x2, y1);
            float[] p01 = KMath.mat2dTransformPoint(mat2d, x1, y2);
            float[] p11 = KMath.mat2dTransformPoint(mat2d, x2, y2);
            float[][] quad = {p00, p10, p11, p01};

            // 计算 AABB（float精度）
            float minX = p00[0], maxX = p00[0], minY = p00[1], maxY = p00[1];
            for (float[] p : quad) {
                if (p[0] < minX) minX = p[0];
                if (p[0] > maxX) maxX = p[0];
                if (p[1] < minY) minY = p[1];
                if (p[1] > maxY) maxY = p[1];
            }

            int minWorldTX = (int) Math.floor((minX - EPSILON) / tileSize);
            int maxWorldTX = (int) Math.floor((maxX + EPSILON - 1e-12f) / tileSize);
            int minWorldTY = (int) Math.floor((minY - EPSILON) / tileSize);
            int maxWorldTY = (int) Math.floor((maxY + EPSILON - 1e-12f) / tileSize);

            for (int wy = minWorldTY; wy <= maxWorldTY; wy++) {
                for (int wx = minWorldTX; wx <= maxWorldTX; wx++) {
                    float rx = wx * tileSize;
                    float ry = wy * tileSize;
                    if (rectIntersectsQuad(rx, ry, tileSize, quad)) {
                        result.add(TiledCanvas.pack(wx, wy));
                    }
                }
            }
        }
        return result;
    }

    private static boolean pointInQuad(float px, float py, float[][] quad) {
        boolean positive = false, negative = false;
        for (int i = 0; i < 4; i++) {
            float[] a = quad[i];
            float[] b = quad[(i + 1) % 4];
            float cross = (b[0] - a[0]) * (py - a[1]) - (b[1] - a[1]) * (px - a[0]);
            if (cross > EPSILON) positive = true;
            else if (cross < -EPSILON) negative = true;
            if (positive && negative) return false;
        }
        return true;
    }

    private static boolean segmentsIntersect(float x1, float y1, float x2, float y2,
                                             float x3, float y3, float x4, float y4) {
        float d1 = direction(x3, y3, x4, y4, x1, y1);
        float d2 = direction(x3, y3, x4, y4, x2, y2);
        float d3 = direction(x1, y1, x2, y2, x3, y3);
        float d4 = direction(x1, y1, x2, y2, x4, y4);
        if (((d1 > EPSILON && d2 < -EPSILON) || (d1 < -EPSILON && d2 > EPSILON)) &&
                ((d3 > EPSILON && d4 < -EPSILON) || (d3 < -EPSILON && d4 > EPSILON))) {
            return true;
        }
        return false;
    }

    private static float direction(float x1, float y1, float x2, float y2, float x3, float y3) {
        return (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
    }

    private static boolean rectIntersectsQuad(float rx, float ry, int tileSize, float[][] quad) {
        float rectLeft = rx;
        float rectRight = rx + tileSize;
        float rectTop = ry + tileSize;
        float rectBottom = ry;

        if (pointInQuad(rectLeft, rectBottom, quad) ||
                pointInQuad(rectLeft, rectTop, quad) ||
                pointInQuad(rectRight, rectBottom, quad) ||
                pointInQuad(rectRight, rectTop, quad)) {
            return true;
        }

        for (float[] p : quad) {
            if (p[0] >= rectLeft - EPSILON && p[0] <= rectRight + EPSILON &&
                    p[1] >= rectBottom - EPSILON && p[1] <= rectTop + EPSILON) {
                return true;
            }
        }

        for (int i = 0; i < 4; i++) {
            float[] a = quad[i];
            float[] b = quad[(i + 1) % 4];
            if (segmentsIntersect(a[0], a[1], b[0], b[1],
                    rectLeft, rectBottom, rectLeft, rectTop) ||
                    segmentsIntersect(a[0], a[1], b[0], b[1],
                            rectLeft, rectTop, rectRight, rectTop) ||
                    segmentsIntersect(a[0], a[1], b[0], b[1],
                            rectRight, rectTop, rectRight, rectBottom) ||
                    segmentsIntersect(a[0], a[1], b[0], b[1],
                            rectRight, rectBottom, rectLeft, rectBottom)) {
                return true;
            }
        }
        return false;
    }
}