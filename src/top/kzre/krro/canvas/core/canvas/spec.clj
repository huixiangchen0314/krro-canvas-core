(ns top.kzre.krro.canvas.core.canvas.spec
  "画布数据规格。光栅画布检查像素数组类型与长度；游程画布仅检查 runs 存在且非空。"
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

(s/def ::pixels some?)

(s/def ::raster-canvas
  (s/and (s/merge ::canvas-common (s/keys :req-un [::pixels]))
         valid-pixel-array?))

;; ── 游程编码画布 ─────────────────────────────
(defn- valid-runs-array?
  [{:keys [runs]}]
  (and (some? runs) (pos? (alength runs))))

(s/def ::runs some?)

(s/def ::run-length-canvas
  (s/and (s/merge ::canvas-common (s/keys :req-un [::runs]))
         valid-runs-array?))

;; ── 画布联合规格 ─────────────────────────────
(s/def ::canvas (s/or :raster ::raster-canvas
                      :run-length ::run-length-canvas))