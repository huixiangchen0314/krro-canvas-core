(ns top.kzre.krro.canvas.core.layer.core
  "图层操作：统一渲染入口与图层组管理。
   内部使用 :batch-item 与 :merged 两种中间表示，
   批次渲染由 flush-batch! 多方法根据 :backend 分派。"
  (:require
   [taoensso.tufte :refer [p profile]]
   [top.kzre.krro.canvas.core.layer.group :as group]
   [top.kzre.krro.canvas.core.layer.render.core :as render]
   [top.kzre.krro.canvas.core.layer.spec]
   [top.kzre.krro.canvas.core.layer.transform :as trans]
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.util.promise :as promise])
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
  "渲染图层树到目标画布（预乘视口变换）。

   layers      : 根图层列表（已预处理）
   view-canvas      : 目标画布 (TiledCanvas)
   opts        : 关键字参数
     :view-width, :view-height      画布宽度、高度（像素）
     :view-matrix                   视口变换仿射矩阵
     :dirty-tiles                   世界空间脏瓦片
     :viewport-dirty-tiles          视口空间脏瓦片（若提供则优先于 :dirty-tiles）
     :image-width, :image-height    图像矩形范围
     :transform-composed?           图层变换是否已经合成到屏幕空间（默认 false）
     :tile-size                     可覆盖 view-canvas 自带的 tile-size

   返回 Promise<Canvas>：渲染完成时解析为目标 view-canvas。
   中间画布由 core/render 内部处理（含失败清理）；
   本函数只额外负责把结果合并回目标 view-canvas 并释放结果画布。"
  [layers ^TiledCanvas view-canvas
   & {:keys [view-width view-height
             view-matrix
             dirty-tiles
             view-dirty-tiles
             image-width image-height
             transform-composed?
             tile-size]
      :or   {transform-composed? false}
      :as   opts}]
  (let [tile-size (or tile-size (.getTileSize view-canvas))

        ;; 1) 预处理：把图层变换合成到视口坐标
        composed  (if transform-composed?
                    layers
                    (mapv #(trans/compose-transforms % :viewport view-matrix) layers))

        ;; 2) 穿透组（如 passthrough 组，把子层提升到当前层）
        throughed (mapv group/pass-through composed)

        ;; 3) 脏瓦片归一化：世界空间 → 视口空间
        view-dirty-tiles'
        (or view-dirty-tiles
            (if (and dirty-tiles view-matrix)
              (LayerUtils/transformTiles dirty-tiles tile-size view-matrix)
              dirty-tiles))

        ;; 4) 裁剪脏瓦片到视口
        view-clipped-dirty-tiles
        (or (when view-dirty-tiles'
              (LayerUtils/clipTiles view-dirty-tiles' tile-size view-width view-height))
            (LayerUtils/canvasTiles tile-size view-width view-height))

        ;; 5) 裁剪到图像范围（若提供）
        image-clipped-dirty-tiles
        (if (and image-width image-height view-matrix)
          (let [pmin (util/transform-point view-matrix 0 0)
                pmax (util/transform-point view-matrix image-width image-height)]
            (LayerUtils/clipTilesAABB view-clipped-dirty-tiles tile-size
                                      (:x pmin) (:y pmin)
                                      (:x pmax) (:y pmax)))
          view-clipped-dirty-tiles)

        ;; 6) 组装 core/render 的 opts
        opts' (assoc opts
                :tile-size     tile-size
                :viewport-w    view-width
                :viewport-h    view-height
                :dirty-tiles   image-clipped-dirty-tiles)]

    (-> (profile
          {:id :render-layers-pass}
          (p :render-layers-pass
             (render/render throughed opts')))
        (promise/handle
          (fn [result-canvas e]
            (if e
              (throw e)
              (do
                ;; 结果落回目标画布
                (.deleteTiles view-canvas ^Set view-clipped-dirty-tiles)
                (.mergeCanvas view-canvas result-canvas)
                ;; result-view-canvas 所有权在本函数，合并后不再需要，释放
                (.clear result-canvas)
                view-canvas)))))))