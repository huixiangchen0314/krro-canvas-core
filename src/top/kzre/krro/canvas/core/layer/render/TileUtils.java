package top.kzre.krro.canvas.core.layer.render;

import top.kzre.krro.util.tile.Tile;
import top.kzre.krro.util.tile.TiledCanvas;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class TileUtils {

    private TileUtils() {}

    public static CompletableFuture<Void> downloadInPlace(TiledCanvas canvas) {
        List<CompletableFuture<Void>> futures = collectDownloads(canvas, canvas);
        if (futures.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 下载画布到 CPU，返回新画布。纯函数——{@code src} 不被修改。
     *
     * <p>先用 {@link TiledCanvas#copy()} 建立共享视图，CPU 侧瓦片零拷贝
     * 共享，GPU 侧瓦片逐个下载并替换。
     *
     * <p><b>线程契约</b>：可在任意线程调用，不阻塞。返回的 future
     * 在所有下载完成后解析为新画布。下载动作由各瓦片的
     * {@link DownloadableTile#downloadTo} 投递到各自的后端线程。
     *
     * <p><b>失败语义</b>：任一瓦片下载失败，future 以原始异常完成。
     * 失败时新画布被 {@code close()} 释放，不返回给调用方。
     *
     * <p><b>参数校验</b>：尺寸、通道不匹配等契约违反由
     * {@code downloadTo} 同步抛出，本方法会向上传播。
     */
    public static CompletableFuture<TiledCanvas> download(TiledCanvas src) {
        TiledCanvas dst = src.copy();

        List<CompletableFuture<Void>> futures;
        try {
            futures = collectDownloads(src, dst);
        } catch (Throwable t) {
            closeQuietly(dst);
            return failedFuture(unwrap(t));
        }

        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(dst);
        }

        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .handle((v, e) -> {
                    if (e != null) {
                        closeQuietly(dst);
                        throw new CompletionException(unwrap(e));
                    }
                    return dst;
                });
    }

    private static List<CompletableFuture<Void>> collectDownloads(
            TiledCanvas src, TiledCanvas dst) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (long key : src.getTiles()) {
            int tx = TiledCanvas.unpackTx(key);
            int ty = TiledCanvas.unpackTy(key);
            Tile tile = src.getTile(tx, ty);
            if (tile == null) continue;

            DownloadableTile dl = tile.queryData(DownloadableTile.class);
            if (dl != null) {
                futures.add(dl.downloadTo(dst, tx, ty));
            }
        }
        return futures;
    }

    /**
     * 释放画布，吞掉清理过程中的异常，不遮蔽原始异常。
     *
     * <p>用 {@code close()} 而非 {@code clear()}——close 标记画布不可写，
     * 防止后台仍在运行的 downloadTo 把数据写进已释放的画布造成泄漏。
     */
    private static void closeQuietly(TiledCanvas canvas) {
        try {
            canvas.close();
        } catch (Throwable ignored) {
            // 清理失败不遮蔽原始异常
        }
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable t) {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(t);
        return f;
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof CompletionException && t.getCause() != null) {
            return t.getCause();
        }
        if (t instanceof ExecutionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }
}