(ns top.kzre.krro.canvas.core.canvas.convert
  "画布转换：类型、精度、通道数。"
  (:require [top.kzre.krro.canvas.core.canvas.runs :as runs]
            [top.kzre.krro.canvas.core.canvas.util :as util]))

(defn convert-canvas-type
  "在光栅和游程之间转换画布类型。返回新画布 map。"
  [canvas target-type]
  (if (= (:type canvas) target-type)
    canvas
    (case [(:type canvas) target-type]
      [:raster :run-length]
      (let [{:keys [width height channels bits-per-channel pixels]} canvas
            new-runs (runs/raster->runs pixels width height channels bits-per-channel)]
        (assoc canvas :type :run-length :runs new-runs :pixels nil))
      [:run-length :raster]
      (let [{:keys [width height channels bits-per-channel runs]} canvas
            new-pixels (runs/runs->raster runs width height channels bits-per-channel)]
        (assoc canvas :type :raster :pixels new-pixels :runs nil))
      (throw (IllegalArgumentException.
               (str "Unsupported conversion: " (:type canvas) " -> " target-type))))))

(defn convert-precision
  "改变画布的 bits-per-channel，返回新画布。"
  [canvas new-bits]
  (if (= (:bits-per-channel canvas) new-bits)
    canvas
    (let [{:keys [width height channels bits-per-channel]} canvas
          total (* width height channels)
          old-arr (or (:pixels canvas) (:runs canvas))
          new-arr (case new-bits
                    8  (byte-array total)
                    16 (short-array total)
                    32 (float-array total))
          f #(util/float->native (util/native->float % bits-per-channel) new-bits)]
      (dotimes [i total]
        (aset new-arr i (f (aget old-arr i))))
      (assoc canvas
        :bits-per-channel new-bits
        :pixels (when (= :raster (:type canvas)) new-arr)
        :runs   (when (= :run-length (:type canvas)) new-arr)))))

(defn convert-channels
  "改变画布的通道数。原通道不足时填充 :pad-val（默认 0.0），多余时截断。"
  [canvas new-channels & {:keys [pad-val] :or {pad-val 0.0}}]
  (if (= (:channels canvas) new-channels)
    canvas
    (let [{:keys [width height channels bits-per-channel]} canvas
          old-arr (or (:pixels canvas) (:runs canvas))
          size (* width height new-channels)
          new-arr (case bits-per-channel
                    8  (byte-array size)
                    16 (short-array size)
                    32 (float-array size))
          pad-native (util/float->native pad-val bits-per-channel)
          copy-channels (min channels new-channels)]
      (dotimes [i (* width height)]
        (let [old-idx (* i channels)
              new-idx (* i new-channels)]
          (dotimes [c copy-channels]
            (aset new-arr (+ new-idx c) (aget old-arr (+ old-idx c))))
          (dotimes [c (- new-channels copy-channels)]
            (aset new-arr (+ new-idx copy-channels c) pad-native))))
      (assoc canvas
        :channels new-channels
        :pixels (when (= :raster (:type canvas)) new-arr)
        :runs   (when (= :run-length (:type canvas)) new-arr)))))