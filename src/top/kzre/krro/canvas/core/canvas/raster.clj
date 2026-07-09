(ns top.kzre.krro.canvas.core.canvas.raster
  "光栅画布创建与像素级读写。"
  (:require [top.kzre.krro.canvas.core.canvas.protocol :as p]
            [top.kzre.krro.canvas.core.rect :as rect]))

;; 从色彩空间推断通道数
(defn- channels-from-color-space [color-space]
  (case color-space
    :gray 1
    :rgba 4
    (throw (IllegalArgumentException. (str "Unknown color space: " color-space)))))

(defrecord RasterCanvas [^long w ^long h
                         ^floats data
                         color-space   ;; :gray 或 :rgba
                         dirty]        ;; nil 或 [x y w h]
  p/ICanvas
  (width [_] w)
  (height [_] h)
  (data [_] data)
  (color-space [_] color-space)
  (get-pixel [_ x y]
    (let [ch (channels-from-color-space color-space)
          idx (* ch (+ x (* y w)))
          res (float-array ch)]
      (dotimes [c ch]
        (aset res c (aget data (+ idx c))))
      res))
  (set-pixel! [this x y color]
    (let [ch (channels-from-color-space color-space)
          idx (* ch (+ x (* y w)))]
      (dotimes [c ch]
        (aset data (+ idx c) (aget color c)))
      (let [new-dirty (if dirty
                        (rect/rect-union dirty [x y 1 1])
                        [x y 1 1])]
        (assoc this :dirty new-dirty))))
  (dirty-rect [_] dirty)
  (clear-dirty! [this] (assoc this :dirty nil)))

(defn make-raster-canvas
  "创建光栅画布。
   必填参数：
     width, height - 画布尺寸（整数）
   可选关键字参数：
     :color-space (默认 :rgba) - 色彩空间，决定通道数
     :data        (可选)       - 已有的 float-array，长度必须为 width*height*channels
     :color       (默认透明黑) - 初始填充色（仅在未提供 :data 时生效）"
  [width height & {:keys [color-space data color]
                   :or   {color-space :rgba}}]
  (let [width  (int width)
        height (int height)
        ch     (int (channels-from-color-space color-space))
        size   (int (* width height ch))]
    (when data
      (when (not= (count data) size)
        (throw (IllegalArgumentException.
                 (str "Data size mismatch: expected " size " but got " (count data))))))
    (let [^floats pixels (or data (float-array size))]
      (when-not data
        (let [^floats default-color (float-array (or color (repeat ch 0.0)))
              pixel-count (int (* width height))]
          (dotimes [i pixel-count]
            (let [idx (int (* i ch))]
              (dotimes [c ch]
                (aset pixels (+ idx c) (aget default-color c)))))))
      (->RasterCanvas width height pixels color-space nil))))