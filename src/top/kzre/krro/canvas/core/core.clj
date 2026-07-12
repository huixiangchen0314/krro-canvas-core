(ns top.kzre.krro.canvas.core.core
  "krro-canvas-core 公共 API：画布与图层模块的总入口。"
  (:require
    [top.kzre.krro.canvas.core.layer.cache :as cache]
    [top.kzre.krro.canvas.core.layer.core :as l]
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.merged :as merged]
    [top.kzre.krro.canvas.core.layer.render :as render]

    [top.kzre.krro.canvas.core.layer.transform :as trans]
    [top.kzre.krro.canvas.core.obb]))

;; ── 图层操作 ────────────────────────────────────
(def ^:dynamic *merge-layer!* render/*merge-layer!*)
(def render-layer!            render/render-layer!)   ;; 用户 defmethod 扩展图层类型
(def render-batch!            render/render-batch!)   ;; 用户 defmethod 优化批量提交

(def make-merged-layer merged/make-merged-layer)
(def render-layers!    l/render-layers!)
(def preprocess-transform trans/preprocess)


(def expand-layers render/expand-layers)

(def render-children! render/render-children!)

(def invalidate-cache! cache/invalidate-cache!)
(def prepare-cache cache/prepare-cache)

;; ── 图层组操作（不变） ─────────────────────────────
(def group? group/group?)
(def make-layer-group group/make-layer-group)
(def add-layer-to-group group/add-layer)
(def add-layer-to-group-at group/add-layer-at)
(def remove-layer-from-group group/remove-layer)
(def get-layers-from-group group/get-layers)
(def update-layer-in-group group/update-layer)
(def layer-group-count group/layer-count)