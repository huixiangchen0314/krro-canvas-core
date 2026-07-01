(ns top.kzre.krro.canvas.core.core
  "krro-canvas-core 公共 API：画布与图层模块的总入口。"
  (:require [top.kzre.krro.canvas.core.canvas.core :as c]
            [top.kzre.krro.canvas.core.layer.core :as l]))

;; ── 画布创建与基本操作 ─────────────────────────
(def make-canvas      c/make-canvas)
(def canvas-width     c/canvas-width)
(def canvas-height    c/canvas-height)
(def get-pixel        c/get-pixel)
(def set-pixel!       c/set-pixel!)
(def raw-pixels       c/raw-pixels)

;; ── 画布转换（类型、精度、通道） ──────────────
(def convert-canvas-type  c/convert-canvas-type)
(def convert-precision    c/convert-precision)
(def convert-channels     c/convert-channels)

;; ── 图层操作 ────────────────────────────────────
(def render-layer!    l/render-layer!)
(def make-layer-group l/make-layer-group)