(ns top.kzre.krro.canvas.core.canvas.spec
  "画布数据规格。根据 :bits-per-channel 确定内部存储类型，:channels 指定通道数。
   支持任意通道配置，例如 1=灰度，3=RGB，4=RGBA，更多通道用于特殊数据。"
  (:require [clojure.spec.alpha :as s]))

;; ── 公共属性 ─────────────────────────────────
(s/def ::type #{:raster :run-length})
(s/def ::width  (s/and int? pos?))
(s/def ::height (s/and int? pos?))
(s/def ::bits-per-channel (s/and int? #{8 16 32}))
(s/def ::channels (s/and int? pos?))

(s/def ::canvas-common
  (s/keys :req-un [::type ::width ::height ::bits-per-channel ::channels]))

;; ── 辅助：根据位深度返回存储数组类型 ──────────
(defn- expected-array-class
  [bits]
  (case bits
    8  (Class/forName "[B")   ;; byte-array
    16 (Class/forName "[S")   ;; short-array
    32 (Class/forName "[F"))) ;; float-array

;; ── 光栅画布 ─────────────────────────────────
(defn- valid-pixel-array?
  [{:keys [bits-per-channel width height channels pixels]}]
  (and (instance? (expected-array-class bits-per-channel) pixels)
       (= (alength pixels) (* width height channels))))

(s/def ::pixels
  (s/with-gen
    (s/and some? valid-pixel-array?)
    (fn [] (s/gen #(float-array 0)))))

(s/def ::raster-canvas
  (s/merge ::canvas-common (s/keys :req-un [::pixels])))

;; ── 游程编码画布 ─────────────────────────────
(defn- valid-runs-array?
  [{:keys [bits-per-channel channels runs]}]
  (and (instance? (expected-array-class bits-per-channel) runs)
       (zero? (mod (alength runs) channels))))

(s/def ::runs
  (s/with-gen
    (s/and some? valid-runs-array?)
    (fn [] (s/gen #(float-array 0)))))

(s/def ::run-length-canvas
  (s/merge ::canvas-common (s/keys :req-un [::runs])))

;; ── 画布联合规格 ─────────────────────────────
(s/def ::canvas (s/or :raster ::raster-canvas
                      :run-length ::run-length-canvas))