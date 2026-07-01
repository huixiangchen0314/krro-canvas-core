(ns top.kzre.krro.canvas.core.canvas.core
  "画布统一接口。根据 :type 分派到具体实现。"
  (:require [top.kzre.krro.canvas.core.canvas.convert :as conv]
            [top.kzre.krro.canvas.core.canvas.spec]
            [top.kzre.krro.canvas.core.canvas.raster :as raster]
            [top.kzre.krro.canvas.core.canvas.runs :as runs]
            [top.kzre.krro.canvas.core.canvas.util :as util]))

(defn make-canvas
  "创建画布。type 为 :raster 或 :run-length。
   必填参数: :width, :height, :bits-per-channel
   可选: :channels (默认 4), :color (0.0-1.0 浮点序列，默认透明黑)"
  [type & {:keys [width height bits-per-channel channels color]
           :or {channels 4}}]
  (let [color (or color (repeat channels 0.0))]
    (case type
      :raster      (raster/make-raster-canvas width height bits-per-channel channels :color color)
      :run-length  (runs/make-run-length-canvas width height bits-per-channel channels :color color)
      (throw (IllegalArgumentException. (str "Unknown canvas type: " type))))))

(defn get-pixel
  [canvas x y]
  (case (:type canvas)
    :raster      (raster/get-pixel-raster canvas x y)
    :run-length  (runs/get-pixel-run-length canvas x y)
    (throw (ex-info "Unknown canvas type" {:type (:type canvas)}))))

(defn set-pixel!
  [canvas x y vals]
  (case (:type canvas)
    :raster      (do (raster/set-pixel-raster! canvas x y vals) canvas)
    :run-length  (runs/set-pixel-run-length! canvas x y vals)
    (throw (ex-info "Unknown canvas type" {:type (:type canvas)}))))


(def convert-canvas-type conv/convert-canvas-type)
(def convert-precision conv/convert-precision)
(def convert-channels conv/convert-channels)

(def canvas-width util/canvas-width)
(def canvas-height util/canvas-height)

(def raw-pixels raster/raw-pixels)