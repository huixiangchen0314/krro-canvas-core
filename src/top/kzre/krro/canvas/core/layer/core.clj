(ns top.kzre.krro.canvas.core.layer.core
  "图层操作：统一渲染入口与图层组管理。
   内部使用 :batch-item 与 :merged 两种中间表示，
   批次渲染由 flush-batch! 多方法根据 :backend 分派。"
  (:require
   [top.kzre.krro.canvas.core.layer.cache :as cache]
   [top.kzre.krro.canvas.core.layer.render :as render]
   [top.kzre.krro.canvas.core.layer.spec]
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
   先处理缓存（:cached?），再变换预处理，最终渲染。"
  [root-layers ^floats data w h]
  (let [;; 0. 缓存处理：将标记 :cached? 的图层/组预先光栅化为 :cached 图层
        cached-layers (mapv #(cache/prepare-cache % w h) root-layers)
        ;; 1. 变换预处理
        preprocessed (mapv trans/preprocess cached-layers)
        ;; 2. 展开并渲染（无蒙板）
        stack (render/expand-layers preprocessed w h)]
    (render/render-children! stack data w h)))


