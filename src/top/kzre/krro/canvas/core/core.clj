(ns top.kzre.krro.canvas.core.core
  "krro-canvas-core 公共 API：画布与图层模块的总入口。"
  (:require [top.kzre.krro.canvas.core.layer.core :as l]))


;; ── 图层操作 ────────────────────────────────────
(def render-layer!    l/render-layer!)
(def make-layer-group l/make-layer-group)