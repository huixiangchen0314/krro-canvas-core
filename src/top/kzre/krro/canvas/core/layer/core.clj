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
  [root-layers ^floats data w h & {:as opts}]
  (let [cached-layers (mapv #(cache/prepare-cache % w h opts) root-layers)
        preprocessed  (mapv trans/preprocess cached-layers)
        stack         (render/expand-layers preprocessed)]
    (render/render-children! stack data w h opts)))