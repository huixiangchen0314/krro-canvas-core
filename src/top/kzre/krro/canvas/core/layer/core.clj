(ns top.kzre.krro.canvas.core.layer.core
  "图层操作：统一渲染入口与图层组管理。
   内部使用 :batch-item 与 :merged 两种中间表示，
   批次渲染由 flush-batch! 多方法根据 :backend 分派。"
  (:require
    [top.kzre.krro.canvas.core.layer.spec]
    [top.kzre.krro.canvas.core.layer.mask :as mask]
    [top.kzre.krro.canvas.core.layer.render :as render]
    [top.kzre.krro.canvas.core.layer.transform :as trans]
    [top.kzre.krro.canvas.core.layer.util :as util]))


(def flatten-layers util/flatten-layers)
(def find-layer util/find-layer)
(def insert-layer util/insert-layer)
(def remove-layer util/remove-layer)
(def find-layer-path util/find-layer-path)
(def find-layer-by-path util/find-layer-by-path)
(def move-layer util/move-layer)
(def parent-container util/parent-container)
(defn render-layers!
  "渲染根图层列表到目标画布 data。
   自动预处理变换、解析蒙板引用、执行批量绘制。"
  [root-layers ^floats data w h]
  (let [;; 1. 变换预处理
        preprocessed (mapv trans/preprocess root-layers)
        ;; 2. 蒙板引用解析（缓存避免重复渲染）
        cache (atom {})
        with-masks (mask/prepare-masks preprocessed w h cache)
        ;; 3. 最终渲染
        stack (render/expand-layers with-masks w h)]
    (render/render-children! stack data w h)))


