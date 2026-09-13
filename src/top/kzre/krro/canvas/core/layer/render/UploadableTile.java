package top.kzre.krro.canvas.core.layer.render;

/**
 * 可上传瓦片：能把权威数据上传到渲染后端的快速访问副本。
 *
 * <p>典型的「权威数据」是 CPU 侧像素（{@code HeapTileData} /
 * {@code DirectTileData} 等），「快速访问副本」是 GPU 纹理。上传后
 * 渲染后端可以直接使用，无需每次从 CPU 拉数据。
 *
 * <p><b>能力语义</b>：实现此接口的瓦片支持被上传。不实现的后端
 * （如纯 CPU raster）不需要——上层通过能力查询判断。
 *
 * <p><b>线程契约</b>：必须在<b>渲染后端线程</b>上调用——具体哪个
 * 线程由后端实现决定（GL 是 GL 线程，Vulkan 是 queue 线程，等）。
 */
public interface UploadableTile {

    /**
     * 确保权威数据已上传到渲染后端。
     *
     * <p>幂等——已同步时 no-op。由脏标记驱动，只上传变化过的内容。
     *
     * <p><b>线程契约</b>：必须在渲染后端线程上调用。
     */
    void ensureUploaded();
}