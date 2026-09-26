(ns top.kzre.krro.canvas.core.layer.render.merge
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.canvas.core.layer.render.download :as download]
    [top.kzre.krro.core.util.promise :as promise])
  (:import
    (top.kzre.krro.canvas.core.layer PixelBlitter)
    (top.kzre.krro.canvas.core.layer PixelBlitter$BlitterRequest)
    (top.kzre.krro.util.tile TiledCanvas)))

;; ═══════════════════════════════════════════════
;; 单图层 blit
;; ═══════════════════════════════════════════════

(defn- blit-layer!
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
;; CPU 合成——下载所有 GPU 画布
;; ═══════════════════════════════════════════════

(defn- download-all!
  "把 dst 和所有 layer 的 canvas 下载到 CPU。

   原地下载——GLTileData 换成 CPU TileData，canvas 对象不变。
   纯 CPU 画布是 no-op。

   返回 Promise<Void>，全部下载完成后解析。"
  [layers ^TiledCanvas dst]
  (let [canvases (into [dst]
                       (comp (keep :canvas)
                             (distinct))
                       layers)]
    (-> (promise/all (mapv download/download! canvases))
        (promise/fmap (fn [_] nil)))))

;; ═══════════════════════════════════════════════
;; 默认合并实现
;; ═══════════════════════════════════════════════

(defonce ^:private default-merge-layers-fn*
         (fn [layers ^TiledCanvas canvas
              {:keys [view-width view-height dirty-tiles
                      image-aabb subpixel?]
               :or   {subpixel? false}}]
           ;; CPU blit 要求所有画布的瓦片都是 CPU 侧的——
           ;; 上游 GL 合成产出的画布携带 GLTileData，必须先下载。
           (-> (download-all! layers canvas)
               (promise/fmap
                 (fn [_]
                   (doseq [layer layers]
                     (blit-layer! layer canvas
                                  view-width view-height
                                  dirty-tiles image-aabb subpixel?))
                   canvas)))))

(defn default-merge-layers-fn [] default-merge-layers-fn*)

(def ^:dynamic *merge-layers* default-merge-layers-fn*)