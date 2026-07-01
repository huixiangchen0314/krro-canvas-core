(ns top.kzre.krro.canvas.core.canvas.runs
  "游程编码画布创建与读写。简化实现，保证功能完整。"
  (:require [top.kzre.krro.canvas.core.canvas.raster :as raster]))

(defn- raster->runs
  "将光栅像素数组压缩为游程数组。返回对应位深度的数组。"
  [pixels width height channels bits]
  (let [total (* width height channels)
        runs (java.util.ArrayList.)]
    (loop [i 0]
      (when (< i total)
        (let [start i
              chs (mapv #(aget pixels (+ i %)) (range channels))]
          (loop [j (+ i channels)]
            (if (and (< j total)
                     (every? (fn [c] (= (aget pixels (+ j c)) (chs c)))
                             (range channels)))
              (recur (+ j channels))
              (let [count (/ (- j i) channels)]
                (.add runs (int count))
                (doseq [c (range channels)]
                  (.add runs (chs c)))
                (recur j)))))))
    (let [arr (case bits
                8  (byte-array (.size runs))
                16 (short-array (.size runs))
                32 (float-array (.size runs)))]
      (dotimes [i (.size runs)]
        (aset arr i (.get runs i)))
      arr)))

(defn- runs->raster
  "将游程数组解码为光栅像素数组。"
  [runs width height channels bits]
  (let [total (* width height channels)
        pixels (case bits
                 8  (byte-array total)
                 16 (short-array total)
                 32 (float-array total))]
    (loop [ri 0 pi 0]
      (when (< pi total)
        (let [count (aget runs ri)
              ri' (+ ri 1)]
          (dotimes [_ count]
            (doseq [c (range channels)]
              (aset pixels (+ pi c) (aget runs (+ ri' c))))
            (set! pi (+ pi channels)))
          (recur (+ ri' channels) pi))))
    pixels))

(defn make-run-length-canvas
  "创建一个游程画布。"
  [width height bits-per-channel channels & {:keys [color]}]
  (let [color (or color (repeat channels 0.0))
        raster (raster/make-raster-canvas width height bits-per-channel channels :color color)
        runs (raster->runs (:pixels raster) width height channels bits-per-channel)]
    {:type :run-length :width width :height height
     :bits-per-channel bits-per-channel :channels channels
     :runs runs}))


(defn get-pixel-run-length
  [canvas x y]
  (let [{:keys [width height channels bits-per-channel runs]} canvas
        pixels (runs->raster runs width height channels bits-per-channel)]
    (raster/get-pixel-raster {:width width :height height :channels channels
                              :bits-per-channel bits-per-channel :pixels pixels}
                             x y)))

(defn set-pixel-run-length!
  [canvas x y vals]
  (let [{:keys [width height channels bits-per-channel runs]} canvas
        pixels (runs->raster runs width height channels bits-per-channel)
        temp {:width width :height height :channels channels
              :bits-per-channel bits-per-channel :pixels pixels}]
    (raster/set-pixel-raster! temp x y vals)
    (let [new-runs (raster->runs pixels width height channels bits-per-channel)]
      (assoc canvas :runs new-runs))))