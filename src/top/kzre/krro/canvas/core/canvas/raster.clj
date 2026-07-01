(ns top.kzre.krro.canvas.core.canvas.raster
  "光栅画布创建与像素级读写。"
  (:require [top.kzre.krro.canvas.core.canvas.util :as util]))

(defn make-raster-canvas
  "创建一个光栅画布 map。"
  [width height bits-per-channel channels & {:keys [color]}]
  (let [color (or color (repeat channels 0.0))
        pixels (* width height)
        size (* pixels channels)
        arr (case bits-per-channel
              8  (byte-array size)
              16 (short-array size)
              32 (float-array size))
        native-colors (mapv #(util/float->native % bits-per-channel) color)]
    (when (seq color)
      (dotimes [i pixels]
        (let [idx (* i channels)]
          (dotimes [c channels]
            (let  [native (nth native-colors c)
                  b (case bits-per-channel
                      8  (util/int->byte native)
                      16 (util/int->short native)
                      32 native)]
              (aset arr (+ idx c) b))))))
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
      (let [native (util/float->native (nth vals c) bits)
            b (case bits
                8  (util/int->byte native)
                16 (util/int->short native)
                32 native)]
        (aset pixels (+ idx c) b)))))

(defn raw-pixels
  "返回底层原生数组。"
  [canvas]
  (:pixels canvas))