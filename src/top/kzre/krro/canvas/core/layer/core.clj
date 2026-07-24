(ns top.kzre.krro.canvas.core.layer.core
  "图层操作：统一渲染入口与图层组管理。
   内部使用 :batch-item 与 :merged 两种中间表示，
   批次渲染由 flush-batch! 多方法根据 :backend 分派。"
  (:require
    [top.kzre.krro.canvas.core.layer.render :as render]
    [top.kzre.krro.canvas.core.layer.spec]
    [top.kzre.krro.canvas.core.layer.transform :as trans]
    [top.kzre.krro.canvas.core.layer.util :as util])
  (:import (top.kzre.krro.util.tile Canvas)))

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
  "渲染图层树到目标画布。
   root-layers : 根图层列表（已预处理）
   canvas      : 目标画布 (TiledCanvas)
   w, h        : 画布宽度、高度（像素）
   opts        : 透传选项（如 :dirty-tiles, :tile-size）"
  [root-layers ^Canvas canvas w h & {:as opts}]
  (let [preprocessed  (mapv trans/preprocess root-layers)
        stack         (render/expand-layers preprocessed)]
    (render/render-children! stack canvas w h opts)))