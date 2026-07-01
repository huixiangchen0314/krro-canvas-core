(ns top.kzre.krro.canvas.core.canvas.spec
  "画布数据规格。根据 :bits-per-channel 确定内部存储类型，:channels 指定通道数。
   支持任意通道配置。"
  (:require [clojure.spec.alpha :as s]))

(s/def ::type #{:raster :run-length})
(s/def ::width  (s/and int? pos?))
(s/def ::height (s/and int? pos?))
(s/def ::bits-per-channel (s/and int? #{8 16 32}))
(s/def ::channels (s/and int? pos?))

(s/def ::canvas-common
  (s/keys :req-un [::type ::width ::height ::bits-per-channel ::channels]))

;; ── 光栅画布 ─────────────────────────────────
(defn- expected-array-class [bits]
  (case bits
    8  (Class/forName "[B")
    16 (Class/forName "[S")
    32 (Class/forName "[F")))

(defn- valid-pixel-array? [{:keys [bits-per-channel width height channels pixels]}]
  (and (instance? (expected-array-class bits-per-channel) pixels)
       (= (alength pixels) (* width height channels))))

(s/def ::pixels some?)
(s/def ::raster-canvas
  (s/and (s/merge ::canvas-common (s/keys :req-un [::pixels]))
         valid-pixel-array?))

;; ── 游程画布 ─────────────────────────────────
(defn- valid-runs-array? [{:keys [runs]}]
  (and (some? runs) (pos? (alength runs))))

(s/def ::runs some?)
(s/def ::run-length-canvas
  (s/and (s/merge ::canvas-common (s/keys :req-un [::runs]))
         valid-runs-array?))

;; ── 多方法分派 ──────────────────────────────
(defmulti canvas-type :type)
(defmethod canvas-type :raster [_] ::raster-canvas)
(defmethod canvas-type :run-length [_] ::run-length-canvas)

;; ── 画布联合（支持任意画布类型） ────────────
(s/def ::canvas (s/multi-spec canvas-type :type))