(ns top.kzre.krro.canvas.core.layer.core
  "图层操作：统一渲染入口与图层组管理。
   内部使用 :batch-item 与 :merged 两种中间表示，
   批次渲染由 flush-batch! 多方法根据 :backend 分派。"
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.mask :as mask]
    [top.kzre.krro.canvas.core.layer.pre :as pre]
    [top.kzre.krro.canvas.core.layer.render :as render]
    [top.kzre.krro.canvas.core.layer.spec]))

(defn render-layers!
  "渲染根图层列表到目标画布 data。
   自动预处理变换、解析蒙板引用、执行批量绘制。"
  [root-layers ^floats data w h]
  (let [;; 1. 变换预处理
        preprocessed (pre/preprocess root-layers)
        ;; 2. 蒙板引用解析（缓存避免重复渲染）
        cache (atom {})
        with-masks (mask/prepare-masks preprocessed w h cache)
        ;; 3. 最终渲染
        stack (render/expand-layers with-masks w h)]
    (render/render-children! stack data w h)))


;; ── 图层组操作（不变） ─────────────────────────────
(def make-layer-group group/make-layer-group)
(def add-layer-to-group group/add-layer)
(def add-layer-to-group-at group/add-layer-at)
(def remove-layer-from-group group/remove-layer)
(def get-layers-from-group group/get-layers)
(def update-layer-in-group group/update-layer)
(def layer-group-count group/layer-count)