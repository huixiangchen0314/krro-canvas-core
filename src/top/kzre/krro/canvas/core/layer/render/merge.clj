(ns top.kzre.krro.canvas.core.layer.render.merge
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.core.util.promise :as promise])
  (:import
    (top.kzre.krro.canvas.core.layer PixelBlitter)
    (top.kzre.krro.canvas.core.layer PixelBlitter$BlitterRequest)
    (top.kzre.krro.util.tile TiledCanvas)))

;; ═══════════════════════════════════════════════
;; 单图层 blit：构造 BlitterRequest
;; ═══════════════════════════════════════════════

(defn- blit-layer!
  "把一个图层合成到目标画布。使用 BlitterRequest builder。

   参数：
     layer          图层数据（含 :canvas / :transform / :blend-mode / :opacity）
     dst            目标画布（就地修改，调用方持有所有权）
     view-width     视口宽度
     view-height    视口高度
     dirty-tiles    脏瓦片集合（视口坐标）
     image-aabb     图像在视口空间的 AABB（可选）
                    {:min-x :min-y :max-x :max-y}
     subpixel?      是否启用亚像素精度

   无返回值（副作用：修改 dst）"
  [layer ^TiledCanvas dst
   view-width view-height
   dirty-tiles image-aabb subpixel?]
  (let [src-canvas (:canvas layer)
        blend-mode (util/blend-mode-str (:blend-mode layer) :normal)
        opacity    (float (:opacity layer 1.0))
        transform  (:transform layer)

        builder (-> (PixelBlitter$BlitterRequest/builder)
                    (.dst dst)
                    (.viewSize (int view-width) (int view-height))
                    (.src src-canvas)
                    (.matrix2d transform)
                    (.blendMode blend-mode)
                    (.opacity opacity)
                    (.dirtyTiles dirty-tiles)
                    (.subpixel (boolean subpixel?)))

        ;; 图像 AABB：若提供则设置 clip
        builder (if image-aabb
                  (.imageSize builder
                         (double (:min-x image-aabb))
                         (double (:min-y image-aabb))
                         (double (:max-x image-aabb))
                         (double (:max-y image-aabb)))
                  builder)

        req (.build builder)]
    (PixelBlitter/blit req)))

;; ═══════════════════════════════════════════════
;; 默认合并实现
;; ═══════════════════════════════════════════════

(defonce ^:private default-merge-layers-fn*
         (fn [layers ^TiledCanvas canvas
              {:keys [view-width view-height dirty-tiles
                      image-aabb subpixel?]
               :or   {subpixel? false}}]
           (let [out-canvas (.copy canvas)]
             (doseq [layer layers]
               (blit-layer! layer out-canvas
                            view-width view-height
                            dirty-tiles image-aabb subpixel?))
             (promise/resolved out-canvas))))

(defn default-merge-layers-fn [] default-merge-layers-fn*)

(def ^:dynamic *merge-layers* default-merge-layers-fn*)