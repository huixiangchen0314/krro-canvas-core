(ns top.kzre.krro.canvas.core.layer.core
  "图层操作：统一渲染入口与图层组管理。
   内部使用 :batch-item 与 :merged 两种中间表示，
   批次渲染由 flush-batch! 多方法根据 :backend 分派。"
  (:require
   [taoensso.timbre :as log]
   [taoensso.tufte :refer [profile p]]
   [top.kzre.krro.canvas.core.layer.render :as render]
   [top.kzre.krro.canvas.core.layer.spec]
   [top.kzre.krro.canvas.core.layer.transform :as trans]
   [top.kzre.krro.canvas.core.layer.util :as util])
  (:import
   [java.util Set]
   (top.kzre.krro.canvas.core.layer LayerUtils)
   (top.kzre.krro.util.tile TiledCanvas)))

(def parent-inverse-transform util/parent-inverse-transform)
(def compose-inverse-transform util/compose-inverse-transform)
(def transform-point util/transform-point)
(def flatten-layers util/flatten-layers)
(def find-layer util/find-layer)
(def insert-layer util/insert-layer)
(def remove-layer util/remove-layer)
(def find-layer-path util/find-layer-path)
(def find-layer-by-path util/find-layer-by-path)
(def move-layer util/move-layer)
(def parent-container util/parent-container)

(defn render-layers!
  "渲染图层树到目标画布(预乘视口变换)。
   root-layers : 根图层列表（已预处理）
   canvas      : 目标画布 (TiledCanvas)
   viewport-w, viewport-h        : 画布宽度、高度（像素）
   opts        : 透传选项（如 :dirty-tiles, :tile-size）"
  [root-layers ^TiledCanvas canvas viewport-w viewport-h
   & {:keys [dirty-tiles
             ;; 图像矩形范围，
             image-x image-y image-w image-h
             ;; 视口变换仿射矩阵
             viewport
             ]
      :as opts}]
  (let [tile-size (.getTileSize canvas)
        preprocessed  (mapv #(trans/preprocess % opts) root-layers)
        layers         (render/expand-layers preprocessed)
        ;; 补全脏矩形，裁剪脏矩形到视口
        dirty-tiles' (or (when dirty-tiles (LayerUtils/clipTiles dirty-tiles tile-size viewport-w viewport-h))
                         (LayerUtils/canvasTiles tile-size viewport-w viewport-h))
        ;; 渲染的脏矩形裁剪到图像，如果提供了图像范围
        render-dirties (if (and image-x image-y image-w image-h)
                         (LayerUtils/clipTiles image-x image-y image-w image-h)
                         dirty-tiles')
        opts' (assoc opts :dirty-tiles render-dirties)]
    (profile
      {:id :render-layers-pass}
      (p :render-layers-pass
        (render/render
          (fn [c]
            (log/debug  "rendered tiles")
            (.deleteTiles canvas ^Set  dirty-tiles')
            (.mergeCanvas canvas c))
          layers viewport-w viewport-h tile-size opts')))))