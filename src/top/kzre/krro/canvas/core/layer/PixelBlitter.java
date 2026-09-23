package top.kzre.krro.canvas.core.layer;

import top.kzre.colorutils.blend.Blends;
import top.kzre.colorutils.color.RGB;
import top.kzre.krro.canvas.core.Mask;
import top.kzre.krro.canvas.core.NoMask;
import top.kzre.krro.util.math.KMath;
import top.kzre.krro.util.pool.FloatsHolder;
import top.kzre.krro.util.pool.FloatsPool;
import top.kzre.krro.util.pool.PoolManagers;
import top.kzre.krro.util.tile.*;

import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

/**
 * 像素级混合器 —— 将源画布通过仿射变换混合到目标画布。
 * 使用 {@link java.util.concurrent.ForkJoinPool} 并行处理瓦片，工作窃取调度。
 *
 * <h3>蒙版支持</h3>
 * 通过 {@link BlitterRequest#getMask()} 传入 {@link Mask} 实例。
 * 蒙版因子在<b>源坐标系</b>下采样——跟随源变换：
 * <ul>
 *   <li>Identity 路径：源坐标 = 目标坐标，蒙版用世界坐标采样</li>
 *   <li>Transform 路径：逆变换得到源坐标，蒙版用源坐标采样</li>
 * </ul>
 * 快路径（memcpy / 全覆盖）仅在无蒙版时可用——蒙版使逐像素合成成为必须。
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
        private final Mask mask;

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
            this.imageMinX  = b.imageMinX;
            this.imageMinY  = b.imageMinY;
            this.imageMaxX  = b.imageMaxX;
            this.imageMaxY  = b.imageMaxY;
            this.mask       = b.mask;
        }

        public static BlitterRequestBuilder builder() {
            return new BlitterRequestBuilder();
        }

        public double getImageMinX() { return imageMinX; }
        public double getImageMinY() { return imageMinY; }
        public double getImageMaxX() { return imageMaxX; }
        public double getImageMaxY() { return imageMaxY; }
        public TiledCanvas getDst()  { return dst; }
        public int getViewWidth()    { return viewWidth; }
        public int getViewHeight()   { return viewHeight; }
        public TiledCanvas getSrc()  { return src; }
        public float[] getMatrix2d() { return matrix2d; }
        public String getBlendMode() { return blendMode; }
        public float getOpacity()    { return opacity; }
        public Set<Long> getDirtyTiles() { return dirtyTiles; }
        public boolean isSubpixel()  { return subpixel; }
        public Mask getMask()        { return mask; }
    }

    public static final class BlitterRequestBuilder {
        private static final float[] IDENTITY_MATRIX = KMath.mat2dIdentity();

        private TiledCanvas dst;
        private int viewWidth;
        private int viewHeight;
        private TiledCanvas src;
        private float[] matrix2d   = IDENTITY_MATRIX;
        private String blendMode   = Blends.NORMAL;
        private float opacity      = 1.0f;
        private Set<Long> dirtyTiles;
        private boolean subpixel   = false;
        private double imageMinX   = 0;
        private double imageMinY   = 0;
        private double imageMaxX   = Double.MAX_VALUE;
        private double imageMaxY   = Double.MAX_VALUE;
        private Mask mask          = NoMask.INSTANCE;

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

        public BlitterRequestBuilder mask(Mask mask) {
            this.mask = (mask == null) ? NoMask.INSTANCE : mask;
            return this;
        }

        public BlitterRequest build() {
            if (dst == null)        throw new IllegalStateException("dst is required");
            if (src == null)        throw new IllegalStateException("src is required");
            if (viewWidth <= 0)     throw new IllegalStateException("viewWidth must be positive");
            if (viewHeight <= 0)    throw new IllegalStateException("viewHeight must be positive");
            if (blendMode == null)  throw new IllegalStateException("blendMode must not be null");
            if (opacity < 0f || opacity > 1f)
                throw new IllegalStateException("opacity must be in [0,1]: " + opacity);
            if (matrix2d == null)   throw new IllegalStateException("matrix2d must not be null");
            if (dirtyTiles == null) throw new IllegalStateException("dirtyTiles must not be null");
            if (mask == null)       throw new IllegalStateException("mask must not be null");
            if (dst.getChannels() != 4){
                throw new IllegalStateException("dst.getChannels() != 4, PixelBlitter only works on RGBA channels");
            }
            if (src.getChannels() != 4){
                throw new IllegalStateException("src.getChannels() != 4, PixelBlitter only works on RGBA channels");
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

    // ═══════════════════════════════════════════════
    // 便捷入口（已弃用——保留兼容）
    // ═══════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════
    // 主入口
    // ═══════════════════════════════════════════════

    public static void blit(BlitterRequest request) {
        Set<Long> dirtyTiles = request.getDirtyTiles();
        if (dirtyTiles == null) {
            throw new IllegalArgumentException("dirtyTiles should not be null");
        }
        if (dirtyTiles.isEmpty()) return;

        TiledCanvas dst = request.getDst();
        int channels = dst.getChannels();
        assert channels == 4;

        float[] matrix2d = request.getMatrix2d();
        boolean identity = KMath.mat2dIsIdentity(matrix2d);

        List<RecursiveAction> tasks = new ArrayList<>(dirtyTiles.size());
        if (identity) {
            for (long key : dirtyTiles) {
                tasks.add(new BlitTaskIdentity(request, key));
            }
        } else {
            // ── 逆矩阵在任务分发前计算一次 ──
            float[] inv = KMath.mat2dInv(matrix2d);
            if (inv == null) return;
            for (long key : dirtyTiles) {
                tasks.add(new BlitTaskTransform(request, key, inv));
            }
        }

        try {
            ForkJoinTask.invokeAll(tasks);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════
    // 单位矩阵任务
    // ═══════════════════════════════════════════════

    private static final class BlitTaskIdentity extends RecursiveAction {
        private final BlitterRequest request;
        private final long tileKey;

        BlitTaskIdentity(BlitterRequest request, long tileKey) {
            this.request = request;
            this.tileKey = tileKey;
        }

        @Override
        protected void compute() {
            float opacity    = request.getOpacity();
            TiledCanvas dst  = request.getDst();
            int tileSize     = dst.getTileSize();
            int viewWidth    = request.getViewWidth();
            int viewHeight   = request.getViewHeight();
            TiledCanvas src  = request.getSrc();
            String blendMode = request.getBlendMode();
            double minX      = request.getImageMinX();
            double minY      = request.getImageMinY();
            double maxX      = request.getImageMaxX();
            double maxY      = request.getImageMaxY();
            Mask mask        = request.getMask();
            boolean noMask   = (mask == NoMask.INSTANCE);

            int tileX = TiledCanvas.unpackTx(tileKey);
            int tileY = TiledCanvas.unpackTy(tileKey);
            int x0 = tileX * tileSize;
            int y0 = tileY * tileSize;

            int x1 = Math.min(x0 + tileSize, viewWidth);
            int y1 = Math.min(y0 + tileSize, viewHeight);

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

            float[] srcPixel = pool4f.acquire();
            float[] res      = pool4f.acquire();
            try {
                // ═══════════════ 快路径（仅无蒙版） ═══════════════
                if (noMask
                        && opacity >= 1.0f - ALPHA_THRESHOLD
                        && Blends.NORMAL.equals(blendMode)
                        && isRegionOpaque(srcData, tileSize, ex0, ey0, bw, bh)) {

                    boolean fullTile = (ex0 == x0 && ey0 == y0
                            && ex1 == x0 + tileSize
                            && ey1 == y0 + tileSize);

                    if (fullTile) {
                        System.arraycopy(srcData, 0, dstData, 0, srcData.length);
                    } else {
                        int localX0 = TiledCanvas.local(ex0, tileSize);
                        int bytesPerRow = bw * channels;
                        for (int row = 0; row < bh; row++) {
                            int localY = TiledCanvas.local(ey0 + row, tileSize);
                            int rowBase = (localY * tileSize + localX0) * channels;
                            System.arraycopy(srcData, rowBase, dstData, rowBase, bytesPerRow);
                        }
                    }
                }
                // ═══════════════ 慢路径 ═══════════════
                else {
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

                            // 源本透明——提前退出，跳过蒙版采样
                            if (srcPixel[3] < ALPHA_THRESHOLD) continue;

                            // Identity：源坐标 = 世界坐标
                            if (!noMask) {
                                srcPixel[3] *= mask.getValue(worldX, worldY);
                                if (srcPixel[3] < ALPHA_THRESHOLD) continue;
                            }

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

    // ═══════════════════════════════════════════════
    // 一般变换任务
    // ═══════════════════════════════════════════════

    private static final class BlitTaskTransform extends RecursiveAction {
        private final BlitterRequest request;
        private final long tileKey;
        private final float[] inv;

        BlitTaskTransform(BlitterRequest request, long tileKey, float[] inv) {
            this.request = request;
            this.tileKey = tileKey;
            this.inv     = inv;
        }

        @Override
        protected void compute() {
            TiledCanvas dst = request.getDst();
            int tileSize    = dst.getTileSize();
            if (inv == null) return;

            TiledCanvas src  = request.getSrc();
            int viewWidth    = request.getViewWidth();
            int viewHeight   = request.getViewHeight();
            String blendMode = request.getBlendMode();
            float opacity    = request.getOpacity();
            boolean subpixel = request.isSubpixel();
            double clipMinX  = request.getImageMinX();
            double clipMinY  = request.getImageMinY();
            double clipMaxX  = request.getImageMaxX();
            double clipMaxY  = request.getImageMaxY();
            Mask mask        = request.getMask();
            boolean noMask   = (mask == NoMask.INSTANCE);

            int tileX = TiledCanvas.unpackTx(tileKey);
            int tileY = TiledCanvas.unpackTy(tileKey);
            int x0 = tileX * tileSize;
            int y0 = tileY * tileSize;

            int x1 = Math.min(x0 + tileSize, viewWidth);
            int y1 = Math.min(y0 + tileSize, viewHeight);

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

                for (int y = ey0; y < ey1; y++) {
                    int localY = TiledCanvas.local(y, tileSize);
                    int rowBase = localY * tileSize * channels;

                    float srcX = a * (ex0 + 0.5f) + b * (y + 0.5f) + c;
                    float srcY = d * (ex0 + 0.5f) + e * (y + 0.5f) + f;
                    float stepX = a;
                    float stepY = d;

                    for (int x = ex0; x < ex1; x++) {
                        int localX = TiledCanvas.local(x, tileSize);
                        int dstIdx = rowBase + localX * channels;

                        // ── 采样源像素 ──
                        if (subpixel) {
                            int srcX0 = (int) Math.floor(srcX);
                            int srcY0 = (int) Math.floor(srcY);
                            float fx = srcX - srcX0;
                            float fy = srcY - srcY0;

                            src.getPixel(srcX0,     srcY0,     s00);
                            src.getPixel(srcX0 + 1, srcY0,     s10);
                            src.getPixel( srcX0,     srcY0 + 1, s01);
                            src.getPixel(srcX0 + 1, srcY0 + 1, s11);

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
                            src.getPixel(srcXInt, srcYInt, srcColor);
                        }

                        float aSrc = srcColor[3] * opacity;

                        // 源本透明——提前退出，跳过蒙版采样
                        if (aSrc < ALPHA_THRESHOLD) {
                            srcX += stepX;
                            srcY += stepY;
                            continue;
                        }

                        // Transform：蒙版用源坐标采样
                        if (!noMask) {
                            aSrc *= mask.getValue(srcX, srcY);
                            if (aSrc < ALPHA_THRESHOLD) {
                                srcX += stepX;
                                srcY += stepY;
                                continue;
                            }
                        }
                        srcColor[3] = aSrc;

                        // ── 快路径：仅无蒙版且全覆盖 ──
                        if (noMask
                                && aSrc >= 1.0f - ALPHA_THRESHOLD
                                && Blends.NORMAL.equals(blendMode)) {
                            System.arraycopy(srcColor, 0, dstData, dstIdx, 3);
                            dstData[dstIdx + 3] = 1.0f;
                        } else {
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

    // ═══════════════════════════════════════════════
    // 辅助
    // ═══════════════════════════════════════════════

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