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

(defn- convert-precision-raster
  "改变光栅画布的 bits-per-channel，返回新光栅画布。"
  [canvas new-bits]
  (let [{:keys [width height channels bits-per-channel pixels]} canvas
        total (* width height channels)
        new-pixels (case new-bits
                     8  (byte-array total)
                     16 (short-array total)
                     32 (float-array total))
        convert-fn (fn [old-val]
                     (let [native-int (util/float->native (util/native->float old-val bits-per-channel) new-bits)]
                       (case new-bits
                         8  (util/int->byte native-int)
                         16 (util/int->short native-int)
                         32 (float native-int))))]
    (dotimes [i total]
      (aset new-pixels i (convert-fn (aget pixels i))))
    (assoc canvas :bits-per-channel new-bits :pixels new-pixels)))

(defn- convert-channels-raster
  "改变光栅画布的通道数，返回新光栅画布。"
  [canvas new-channels pad-val]
  (let [{:keys [width height channels bits-per-channel pixels]} canvas
        size (* width height new-channels)
        new-pixels (case bits-per-channel
                     8  (byte-array size)
                     16 (short-array size)
                     32 (float-array size))
        pad-native (util/float->native pad-val bits-per-channel)
        pad-val-for-array (case bits-per-channel
                            8  (util/int->byte pad-native)
                            16 (util/int->short pad-native)
                            32 (float pad-native))
        copy-channels (min channels new-channels)]
    (dotimes [i (* width height)]
      (let [old-idx (* i channels)
            new-idx (* i new-channels)]
        (dotimes [c copy-channels]
          (aset new-pixels (+ new-idx c) (aget pixels (+ old-idx c))))
        (dotimes [c (- new-channels copy-channels)]
          (aset new-pixels (+ new-idx copy-channels c) pad-val-for-array))))
    (assoc canvas :channels new-channels :pixels new-pixels)))

(defn convert-precision
  "改变画布的 bits-per-channel，返回新画布。游程画布会先转为光栅再处理，然后转回游程。"
  [canvas new-bits]
  (if (= (:bits-per-channel canvas) new-bits)
    canvas
    (let [orig-type (:type canvas)
          raster-canvas (if (= :run-length orig-type)
                          (convert-canvas-type canvas :raster)
                          canvas)
          new-raster (convert-precision-raster raster-canvas new-bits)]
      (if (= orig-type :run-length)
        (convert-canvas-type new-raster :run-length)
        new-raster))))

(defn convert-channels
  "改变画布的通道数，返回新画布。游程画布会先转为光栅再处理，然后转回游程。"
  [canvas new-channels & {:keys [pad-val] :or {pad-val 0.0}}]
  (if (= (:channels canvas) new-channels)
    canvas
    (let [orig-type (:type canvas)
          raster-canvas (if (= :run-length orig-type)
                          (convert-canvas-type canvas :raster)
                          canvas)
          new-raster (convert-channels-raster raster-canvas new-channels pad-val)]
      (if (= orig-type :run-length)
        (convert-canvas-type new-raster :run-length)
        new-raster))))