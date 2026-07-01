(ns top.kzre.krro.canvas.core.canvas.runs
  "游程编码画布创建与读写。
   8位/16位画布使用对应原生数组存储游程，count 自动分段避免溢出。
   32位浮点画布使用 int-array 存储游程（通道值用 Float/floatToIntBits 编码）。"
  (:require [top.kzre.krro.canvas.core.canvas.raster :as raster]
            [top.kzre.krro.canvas.core.canvas.util :as util])
  (:import (java.util ArrayList)))

;; ── 每通道位数对应的最大游程计数 ──────────────────
(def ^:private max-run-count
  {8  127      ;; byte 有符号最大值
   16 32767    ;; short 有符号最大值
   32 Integer/MAX_VALUE})

;; ── 压缩：光栅 → 游程 ─────────────────────────────
(defn raster->runs
  [pixels width height channels bits]
  (let [total (* width height channels)
        max-count (max-run-count bits)
        runs (ArrayList.)
        pixel-indices (range 0 total channels)
        ;; 提交一个游程（可能分段）
        commit-run (fn [state chs cnt]
                     (loop [remaining cnt
                            state state]
                       (if (pos? remaining)
                         (let [n (min remaining max-count)]
                           (.add runs (int n))
                           ;; 通道值：32位需编码为int位模式
                           (doseq [c chs]
                             (.add runs (case bits
                                          32 (Float/floatToIntBits (float c))
                                          (int c))))
                           (recur (- remaining n) state))
                         state)))
        ;; 逐个像素累积
        final-state
        (reduce (fn [state i]
                  (let [chs (mapv #(aget pixels (+ i %)) (range channels))
                        {:keys [prev-chs current-count]} state]
                    (if (and prev-chs (= chs prev-chs))
                      ;; 相同像素，增加计数（超出限制由commit-run处理）
                      (if (>= current-count max-count)
                        (-> (commit-run state prev-chs current-count)
                            (assoc :prev-chs chs :current-count 1))
                        (assoc state :current-count (inc current-count)))
                      ;; 像素变化，提交前一游程
                      (-> (commit-run state prev-chs current-count)
                          (assoc :prev-chs chs :current-count 1)))))
                {:prev-chs nil :current-count 0}
                pixel-indices)]
    ;; 提交最后一个游程
    (commit-run final-state (:prev-chs final-state) (:current-count final-state))
    ;; 转换为相应原生数组
    (let [size (.size runs)]
      (case bits
        8  (let [arr (byte-array size)]
             (dotimes [k size] (aset arr k (byte (.get runs k))))
             arr)
        16 (let [arr (short-array size)]
             (dotimes [k size] (aset arr k (short (.get runs k))))
             arr)
        32 (int-array (.size runs) (.toArray runs))))))

;; ── 解压：游程 → 光栅 ─────────────────────────────
(defn runs->raster
  [runs width height channels bits]
  (let [total (* width height channels)
        pixels (case bits
                 8  (byte-array total)
                 16 (short-array total)
                 32 (float-array total))]
    (loop [ri 0   ;; 游程数组索引
           pi 0   ;; 像素数组索引
           remaining 0   ;; 当前游程剩余像素数
           chs nil]       ;; 当前游程的通道值序列
      (if (< pi total)
        (if (pos? remaining)
          ;; 写入当前游程的一个像素
          (do
            (doseq [c (range channels)]
              (let [v (nth chs c)]
                (aset pixels (+ pi c)
                      (case bits
                        8  (util/int->byte (int v))      ;; 安全转换无符号 int->byte
                        16 (util/int->short (int v))     ;; 安全转换无符号 int->short
                        32 (Float/intBitsToFloat (int v))))))
            (recur ri (+ pi channels) (dec remaining) chs))
          ;; 读取下一个游程
          (let [count (case bits
                        8  (bit-and (aget runs ri) 0xFF)
                        16 (bit-and (aget runs ri) 0xFFFF)
                        32 (aget runs ri))
                ri' (+ ri 1)
                next-chs (vec (for [c (range channels)]
                                (case bits
                                  8  (bit-and (aget runs (+ ri' c)) 0xFF)
                                  16 (bit-and (aget runs (+ ri' c)) 0xFFFF)
                                  32 (aget runs (+ ri' c)))))]
            (recur (+ ri' channels) pi count next-chs)))
        pixels))))

;; ── 公开接口 ──────────────────────────────────────
(defn make-run-length-canvas
  [width height bits-per-channel channels & {:keys [color]}]
  (let [color (or color (repeat channels 0.0))
        raster-canvas (raster/make-raster-canvas width height bits-per-channel channels :color color)
        runs (raster->runs (:pixels raster-canvas) width height channels bits-per-channel)]
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