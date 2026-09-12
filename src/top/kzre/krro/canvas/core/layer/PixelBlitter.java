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

    // ═══════════════════════════════════════════════
    // 请求对象（不可变，通过 Builder 构造）
    // ═══════════════════════════════════════════════

    public static final class BlitterRequest {
        private final TiledCanvas dst;
        private final int viewWidth;
        private final int viewHeight;
        private final TiledCanvas src;
        private final float[] matrix2d;
        private final String blendMode;
        private final float opacity;
        private final Set<Long> dirtyTiles;
        private final boolean subpixel;
        private final double imageMinX;
        private final double imageMinY;
        private final double imageMaxX;
        private final double imageMaxY;

        private BlitterRequest(BlitterRequestBuilder b) {
            this.dst        = b.dst;
            this.viewWidth  = b.viewWidth;
            this.viewHeight = b.viewHeight;
            this.src        = b.src;
            this.matrix2d   = b.matrix2d;
            this.blendMode  = b.blendMode;
            this.opacity    = b.opacity;
            this.dirtyTiles = b.dirtyTiles;
            this.subpixel   = b.subpixel;
            this.imageMinX = b.imageMinX;
            this.imageMinY = b.imageMinY;
            this.imageMaxX = b.imageMaxX;
            this.imageMaxY = b.imageMaxY;
        }


        // ── Builder ──
        public static BlitterRequestBuilder builder() {
            return new BlitterRequestBuilder();
        }

        public double getImageMinX() {
            return imageMinX;
        }

        public double getImageMinY() {
            return imageMinY;
        }

        public double getImageMaxX() {
            return imageMaxX;
        }

        public double getImageMaxY() {
            return imageMaxY;
        }

        public TiledCanvas getDst() {
            return dst;
        }

        public int getViewWidth() {
            return viewWidth;
        }

        public int getViewHeight() {
            return viewHeight;
        }

        public TiledCanvas getSrc() {
            return src;
        }

        public float[] getMatrix2d() {
            return matrix2d;
        }

        public String getBlendMode() {
            return blendMode;
        }

        public float getOpacity() {
            return opacity;
        }

        public Set<Long> getDirtyTiles() {
            return dirtyTiles;
        }

        public boolean isSubpixel() {
            return subpixel;
        }
    }

    public static final class BlitterRequestBuilder {
        private static final float[] IDENTITY_MATRIX = KMath.mat2dIdentity();

        private TiledCanvas dst;
        private int viewWidth;
        private int viewHeight;
        private TiledCanvas src;
        private float[] matrix2d   = IDENTITY_MATRIX;   // 默认单位矩阵
        private String blendMode   = Blends.NORMAL;              // 默认 normal
        private float opacity      = 1.0f;                       // 默认不透明
        private Set<Long> dirtyTiles;
        private boolean subpixel   = false;                      // 默认最近邻
        private double imageMinX = 0;
        private double imageMinY = 0;
        private double imageMaxX = Double.MAX_VALUE;
        private double imageMaxY = Double.MAX_VALUE;
        public BlitterRequestBuilder dst(TiledCanvas dst) {
            this.dst = dst;
            return this;
        }

        public BlitterRequestBuilder viewSize(int width, int height) {
            this.viewWidth  = width;
            this.viewHeight = height;
            return this;
        }

        public BlitterRequestBuilder imageSize(double minX, double minY, double maxX, double maxY) {
            this.imageMinX = Math.max(this.imageMinX, minX);
            this.imageMinY = Math.max(this.imageMinY, minY);
            this.imageMaxX = Math.min(this.imageMaxX, maxX);
            this.imageMaxY = Math.min(this.imageMaxY, maxY);
            return this;
        }

        public BlitterRequestBuilder src(TiledCanvas src) {
            this.src = src;
            return this;
        }

        public BlitterRequestBuilder matrix2d(float[] matrix2d) {
            this.matrix2d = matrix2d;
            return this;
        }

        public BlitterRequestBuilder blendMode(String blendMode) {
            this.blendMode = blendMode;
            return this;
        }

        public BlitterRequestBuilder opacity(float opacity) {
            this.opacity = opacity;
            return this;
        }

        public BlitterRequestBuilder dirtyTiles(Set<Long> dirtyTiles) {
            this.dirtyTiles = dirtyTiles;
            return this;
        }

        public BlitterRequestBuilder subpixel(boolean subpixel) {
            this.subpixel = subpixel;
            return this;
        }

        public BlitterRequest build() {
            // ── 校验 ──
            if (dst == null)        throw new IllegalStateException("dst is required");
            if (src == null)        throw new IllegalStateException("src is required");
            if (viewWidth <= 0)     throw new IllegalStateException("viewWidth must be positive");
            if (viewHeight <= 0)    throw new IllegalStateException("viewHeight must be positive");
            if (blendMode == null)  throw new IllegalStateException("blendMode must not be null");
            if (opacity < 0f || opacity > 1f)
                throw new IllegalStateException("opacity must be in [0,1]: " + opacity);
            if (matrix2d == null)   throw new IllegalStateException("matrix2d must not be null");
            if (dirtyTiles == null){
                throw new IllegalStateException("dirtyTiles must not be null");
            }
            return new BlitterRequest(this);
        }
    }

   private static final FloatsPool pool4f;

   static {
       FloatsHolder holder = PoolManagers.floats().getHolder();
       pool4f = holder.getPool(4);
   }

    private static final float ALPHA_THRESHOLD = 1e-6f;

    @Deprecated
    public static void blit(TiledCanvas dst, int viewWidth, int viewHeight, TiledCanvas src,
                            float[] matrix2d, String blendMode, float opacity,
                            AntiAlias aa, Set<Long> dirtyTiles, boolean subpixel) {
        blit(dst, viewWidth, viewHeight, src, matrix2d, blendMode, opacity, dirtyTiles, subpixel);
    }

    @Deprecated
    public static void blit(TiledCanvas dst, int viewWidth, int viewHeight, TiledCanvas src,
                            float[] matrix2d, String blendMode, float opacity,
                            Set<Long> dirtyTiles, boolean subpixel) {
        blit(BlitterRequest.builder()
                .dst(dst)
                .viewSize(viewWidth, viewHeight)
                .src(src)
                .matrix2d(matrix2d)
                .blendMode(blendMode)
                .opacity(opacity)
                .dirtyTiles(dirtyTiles)
                .subpixel(subpixel)
                .build());
    }

    public static void blit(BlitterRequest request){
        Set<Long> dirtyTiles = request.getDirtyTiles();
        if (dirtyTiles == null) {
            throw new IllegalArgumentException("dirtyTiles should not be null");
        }

        if (dirtyTiles.isEmpty()) return;

        TiledCanvas dst = request.getDst();
        final int tileSize = dst.getTileSize();
        final int channels = dst.getChannels();
        assert channels == 4;

        // 收集所有需要处理的瓦片（若未指定则默认全图）
        float[] matrix2d = request.getMatrix2d();
        int viewWidth = request.getViewWidth();
        int viewHeight = request.getViewHeight();
        TiledCanvas src = request.getSrc();
        String blendMode = request.getBlendMode();
        float opacity = request.getOpacity();
        boolean subpixel = request.isSubpixel();
        boolean identity = KMath.mat2dIsIdentity(matrix2d);

        // 构建任务列表
        List<RecursiveAction> tasks = new ArrayList<>(dirtyTiles.size());
        if (identity) {
            for (long key : dirtyTiles) {
                tasks.add(new BlitTaskIdentity( request, key));
            }
        } else {
            float[] inv = KMath.mat2dInv(matrix2d);
            if (inv == null) return;
            for (long key : dirtyTiles) {
                tasks.add(new BlitTaskTransform(request, key));
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
        private final BlitterRequest request;
        private final long tileKey;

        BlitTaskIdentity(BlitterRequest request, long tileKey) {
            this.request = request;
            this.tileKey = tileKey;
        }

        @Override
        protected void compute() {
            float opacity = request.getOpacity();
            TiledCanvas dst = request.getDst();
            int tileSize = dst.getTileSize();
            int viewWidth = request.getViewWidth();
            int viewHeight = request.getViewHeight();
            TiledCanvas src = request.getSrc();
            String blendMode = request.getBlendMode();
            double minX = request.getImageMinX();
            double minY = request.getImageMinY();
            double maxX = request.getImageMaxX();
            double maxY = request.getImageMaxY();

            int tileX = TiledCanvas.unpackTx(tileKey);
            int tileY = TiledCanvas.unpackTy(tileKey);
            int x0 = tileX * tileSize;
            int y0 = tileY * tileSize;

            // ── 视口裁剪 ──
            int x1 = Math.min(x0 + tileSize, viewWidth);
            int y1 = Math.min(y0 + tileSize, viewHeight);

            // ── clip 矩形裁剪 ──
            // 有效区域 = [x0, x1) ∩ [minX, maxX)
            //           [y0, y1) ∩ [minY, maxY)
            int ex0 = Math.max(x0, (int) Math.ceil(minX));
            int ey0 = Math.max(y0, (int) Math.ceil(minY));
            int ex1 = Math.min(x1, (int) Math.ceil(maxX));
            int ey1 = Math.min(y1, (int) Math.ceil(maxY));

            int bw = ex1 - ex0;
            int bh = ey1 - ey0;
            if (bw <= 0 || bh <= 0) return;

            Tile srcTile = src.getTile(tileX, tileY);
            if (srcTile == null) return;

            Tile dstTile = dst.ensureTile(tileX, tileY);
            float[] srcData = srcTile.getPixelsSnapshot();
            float[] dstData = dstTile.getPixelsForWrite();
            int channels = dst.getChannels();


            // ── 快路径判定的有效区间 ──
            // 若有效区域是整个 tile，快路径判定也用整个 tile
            // 否则只判定有效区域

            float[] srcPixel = pool4f.acquire();
            float[] res = pool4f.acquire();
            try {
                if (opacity >= 1.0f - ALPHA_THRESHOLD
                        && Blends.NORMAL.equals(blendMode)
                        && isRegionOpaque(srcData, tileSize,
                        ex0, ey0, bw, bh)) {

                    // ── 有效区域是否覆盖整个 tile？ ──
                    boolean fullTile = (ex0 == x0 && ey0 == y0
                            && ex1 == x0 + tileSize
                            && ey1 == y0 + tileSize);


                    // ── 快路径：memcpy ──
                    if (fullTile) {
                        // 整 tile：一次 memcpy
                        System.arraycopy(srcData, 0, dstData, 0, srcData.length);
                    } else {
                        // 部分区域：逐行 memcpy
                        int localX0 = TiledCanvas.local(ex0, tileSize);
                        int bytesPerRow = bw * channels;
                        for (int row = 0; row < bh; row++) {
                            int localY = TiledCanvas.local(ey0 + row, tileSize);
                            int rowBase = (localY * tileSize + localX0) * channels;
                            System.arraycopy(srcData, rowBase, dstData, rowBase, bytesPerRow);
                        }
                    }
                } else {
                    // ── 慢路径：逐像素合成 ──
                    for (int row = 0; row < bh; row++) {
                        int worldY = ey0 + row;
                        int localY = TiledCanvas.local(worldY, tileSize);
                        int rowBase = localY * tileSize * channels;
                        for (int col = 0; col < bw; col++) {
                            int worldX = ex0 + col;
                            int localX = TiledCanvas.local(worldX, tileSize);
                            int idx = rowBase + localX * channels;

                            System.arraycopy(srcData, idx, srcPixel, 0, 4);
                            srcPixel[3] *= opacity;

                            if (srcPixel[3] < ALPHA_THRESHOLD) continue;

                            Blends.blendWithAlpha(blendMode,
                                    dstData, idx,
                                    dstData, idx,
                                    srcPixel, 0);
                        }
                    }
                }
            } finally {
                pool4f.release(srcPixel);
                pool4f.release(res);
            }
        }
    }


    // ──────────── 瓦片并行任务（一般变换） ────────────
    private static final class BlitTaskTransform extends RecursiveAction {
        private final BlitterRequest request;
        private final long tileKey;

        BlitTaskTransform(BlitterRequest request, long tileKey) {
            this.request = request;
            this.tileKey = tileKey;
        }

        @Override
        protected void compute() {
            TiledCanvas dst = request.getDst();
            int tileSize   = dst.getTileSize();
            float[] inv    = KMath.mat2dInv(request.getMatrix2d());
            if (inv == null) return;


            TiledCanvas src         = request.getSrc();
            int viewWidth           = request.getViewWidth();
            int viewHeight          = request.getViewHeight();
            String blendMode        = request.getBlendMode();
            float opacity           = request.getOpacity();
            boolean subpixel        = request.isSubpixel();
            double clipMinX         = request.getImageMinX();
            double clipMinY         = request.getImageMinY();
            double clipMaxX         = request.getImageMaxX();
            double clipMaxY         = request.getImageMaxY();

            int tileX = TiledCanvas.unpackTx(tileKey);
            int tileY = TiledCanvas.unpackTy(tileKey);
            int x0 = tileX * tileSize;
            int y0 = tileY * tileSize;

            // ── 视口裁剪 ──
            int x1 = Math.min(x0 + tileSize, viewWidth);
            int y1 = Math.min(y0 + tileSize, viewHeight);

            // ── clip 矩形裁剪 ──
            int ex0 = Math.max(x0, (int) Math.floor(clipMinX));
            int ey0 = Math.max(y0, (int) Math.floor(clipMinY));
            int ex1 = Math.min(x1, (int) Math.ceil(clipMaxX));
            int ey1 = Math.min(y1, (int) Math.ceil(clipMaxY));

            int bw = ex1 - ex0;
            int bh = ey1 - ey0;
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

                // 起点基于有效区域 (ex0, ey0)，而不是 tile 起点 (x0, y0)
                // 因为遍历时从有效区域开始，要先把 srcX/srcY 计算到 (ex0 - 0.5, ey0 + 0.5) 处
                for (int y = ey0; y < ey1; y++) {
                    int localY = TiledCanvas.local(y, tileSize);
                    int rowBase = localY * tileSize * channels;

                    // 当前行的起始 srcX/srcY（对应屏幕坐标 (ex0 + 0.5, y + 0.5) 反变换）
                    float srcX = a * (ex0 + 0.5f) + b * (y + 0.5f) + c;
                    float srcY = d * (ex0 + 0.5f) + e * (y + 0.5f) + f;
                    float stepX = a;
                    float stepY = d;

                    for (int x = ex0; x < ex1; x++) {
                        int localX = TiledCanvas.local(x, tileSize);
                        int dstIdx = rowBase + localX * channels;

                        if (subpixel) {
                            int srcX0 = (int) Math.floor(srcX);
                            int srcY0 = (int) Math.floor(srcY);
                            float fx = srcX - srcX0;
                            float fy = srcY - srcY0;

                            readPixelFast(src, srcX0,     srcY0,     s00);
                            readPixelFast(src, srcX0 + 1, srcY0,     s10);
                            readPixelFast(src, srcX0,     srcY0 + 1, s01);
                            readPixelFast(src, srcX0 + 1, srcY0 + 1, s11);

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
                                RGB.invPreMultiAlpha(srcColor);
                            }
                        } else {
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