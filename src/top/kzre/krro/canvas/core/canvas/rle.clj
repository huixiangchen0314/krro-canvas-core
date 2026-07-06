(ns top.kzre.krro.canvas.core.canvas.rle
  "游程编码画布：仅用于内存压缩存储，不支持任何像素读写。"
  (:require [top.kzre.krro.canvas.core.canvas.protocol :as p]
            [top.kzre.krro.canvas.core.canvas.raster :as raster])
  (:import [top.kzre.krro.canvas.core.canvas RLE]))

(defrecord RleCanvas [^long w ^long h
                      runs           ;; int[] 压缩数据
                      color-space    ;; :gray 或 :rgba
                      dirty]         ;; 无用，仅为协议兼容
  p/ICanvas
  (width [_] w)
  (height [_] h)
  (data [_] (throw (UnsupportedOperationException. "RleCanvas does not support direct data access.")))
  (color-space [_] color-space)
  (get-pixel [_ _ _] (throw (UnsupportedOperationException. "RleCanvas is compressed. Convert to raster first.")))
  (set-pixel! [_ _ _ _] (throw (UnsupportedOperationException. "RleCanvas is immutable. Convert to raster to modify.")))
  (dirty-rect [_] dirty)
  (clear-dirty! [this] this))

(defn raster->rle
  "将 RasterCanvas 压缩为 RleCanvas。所有访问通过协议函数。"
  [canvas]
  (let [w (p/width canvas)
        h (p/height canvas)
        cs (p/color-space canvas)
        ch (case cs :gray 1 :rgba 4)
        pixels (p/data canvas)
        runs (RLE/compress pixels w h ch 32)]  ;; 固定 32 位
    (->RleCanvas w h runs cs nil)))

(defn rle->raster
  "将 RleCanvas 解压为 RasterCanvas。"
  [canvas]
  (let [w (p/width canvas)
        h (p/height canvas)
        cs (p/color-space canvas)
        ch (case cs :gray 1 :rgba 4)
        runs (:runs canvas)
        pixels (RLE/decompress runs w h ch 32)]
    (raster/make-raster-canvas w h :color-space cs :data pixels)))