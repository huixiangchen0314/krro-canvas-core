package top.kzre.krro.canvas.core.layer.render;

import top.kzre.krro.util.tile.TiledCanvas;

/**
 * 可下载瓦片：能把渲染后端的内容回写到目标画布。
 *
 * <p>典型的「下载」是从 GPU 纹理读回 CPU 像素。瓦片本身不知道
 * 「自己在画布的哪个位置」——位置由调用方通过 {@code (tx, ty)}
 * 提供（遍历 canvas 时天然已知）。
 *
 * <p><b>能力语义</b>：实现此接口的瓦片支持被下载。不实现的后端
 * 不需要——上层通过能力查询判断。
 *
 * <p><b>线程契约</b>：必须在<b>渲染后端线程</b>上调用。
 *
 * <pre>{@code
 * for (long key : canvas.getTiles()) {
 *     int tx = TiledCanvas.unpackTx(key);
 *     int ty = TiledCanvas.unpackTy(key);
 *     Tile tile = canvas.getTile(tx, ty);
 *     DownloadableTile dl = tile.queryData(DownloadableTile.class);
 *     if (dl != null) {
 *         dl.downloadTo(target, tx, ty);
 *     }
 * }
 * }</pre>
 */
public interface DownloadableTile {

    /**
     * 下载本瓦片到目标画布的 {@code (tx, ty)} 格。
     *
     * <p>坐标由调用方提供——瓦片内部的位置信息决定从后端资源的
     * 哪个位置读取数据。
     *
     * @param target 目标画布（CPU 侧）
     * @param tx     目标格 x 坐标
     * @param ty     目标格 y 坐标
     */
    void downloadTo(TiledCanvas target, int tx, int ty);
}