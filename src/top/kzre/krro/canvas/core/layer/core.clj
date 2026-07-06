(ns top.kzre.krro.canvas.core.layer.core
  "图层操作：统一渲染入口与图层组管理。
   内部使用 :batch-item 与 :merged 两种中间表示，
   批次渲染由 flush-batch! 多方法根据 :backend 分派。"
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.spec]))


;; ── 图层组操作（不变） ─────────────────────────────
(def make-layer-group group/make-layer-group)
(def add-layer-to-group group/add-layer)
(def add-layer-to-group-at group/add-layer-at)
(def remove-layer-from-group group/remove-layer)
(def get-layers-from-group group/get-layers)
(def update-layer-in-group group/update-layer)
(def layer-group-count group/layer-count)