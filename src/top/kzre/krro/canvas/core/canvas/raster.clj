(ns top.kzre.krro.canvas.core.canvas.raster
  "光栅画布创建与像素级读写。"
  (:require [top.kzre.krro.canvas.core.canvas.util :as util]))

(defn make-raster-canvas
  "创建一个光栅画布 map。"
  [width height bits-per-channel channels & {:keys [color]}]
  (let [color (or color (repeat channels 0.0))
        size (* width height channels)
        arr (case bits-per-channel
              8  (byte-array size)
              16 (short-array size)
              32 (float-array size))
        native-color (mapv #(util/float->native % bits-per-channel) color)]
    (when (seq color)
      (dotimes [i (int (/ size channels))]
        (let [idx (* i channels)]
          (dotimes [c channels]
            (aset arr (+ idx c) (native-color c))))))
    {:type :raster :width width :height height
     :bits-per-channel bits-per-channel :channels channels
     :pixels arr}))

(defn get-pixel-raster
  [canvas x y]
  (let [^int w (:width canvas)
        ^int channels (:channels canvas)
        ^int bits (:bits-per-channel canvas)
        idx (* channels (+ x (* y w)))
        pixels (:pixels canvas)]
    (mapv #(util/native->float (aget pixels (+ idx %)) bits)
          (range channels))))

(defn set-pixel-raster!
  [canvas x y vals]
  (let [^int w (:width canvas)
        ^int channels (:channels canvas)
        ^int bits (:bits-per-channel canvas)
        idx (* channels (+ x (* y w)))
        pixels (:pixels canvas)]
    (doseq [c (range channels)]
      (aset pixels (+ idx c) (util/float->native (nth vals c) bits)))))

(defn raw-pixels
  "返回底层原生数组。"
  [canvas]
  (:pixels canvas))