(ns top.kzre.krro.canvas.core.layer.util
  "图层相关的通用工具，包括变换矩阵构造、组合，图层性质判断，
   以及临时缓冲区分配和 Alpha 提取。"
  (:require [top.kzre.krro.canvas.core.layer.group :as group])
  (:import (top.kzre.krro.canvas.core.layer MathUtils)))

;; ── 变换矩阵常量 ──────────────────────────────────
(def identity-matrix MathUtils/IDENTITY)

(defn multiply-transform
  [parent local]
  (MathUtils/multiply (float-array parent) (float-array local)))

(defn compose-local-transform
  "从图层 map 提取变换参数，委托 Java 生成矩阵。"
  [{:keys [x y scale-x scale-y rotation]
    :or {x 0.0 y 0.0 scale-x 1.0 scale-y 1.0 rotation 0.0}}]
  (MathUtils/composeLocalTransform
    (float x) (float y)
    (float scale-x) (float scale-y)
    (float rotation)))

;; ── 直通组判断 ────────────────────────────────────
(defn pass-through?
  "判断图层是否为直通组（其子图层直接穿透到父级）。
   条件：:blend-mode 为 nil 或 :pass-through。"
  [layer]
  (let [bm (:blend-mode layer)]
    (or (nil? bm) (= :pass-through bm))))

;; ── 缓冲区分配 ────────────────────────────────────
(defn allocate-data
  "分配一个 w*h*4 的浮点数组，用于 RGBA 画布。"
  [w h]
  (float-array (* w h 4)))

;; ── Alpha 提取 ────────────────────────────────────
(defn extract-alpha
  "从 RGBA 浮点数组中提取 alpha 通道，返回单独的 float 数组，长度 w*h。"
  [^floats rgba w h]
  (let [alpha (float-array (* w h))]
    (dotimes [i (* w h)]
      (aset alpha i (aget rgba (+ (* 4 i) 3))))
    alpha))

(defn find-layer
  "在图层列表（包括嵌套的图层组）中查找指定 ID 的图层。
   返回图层 map，若未找到返回 nil。"
  [layer-id layers]
  (some (fn [layer]
          (if (= (:id layer) layer-id)
            layer
            (when (group/group? layer)
              (find-layer layer-id (:layers layer)))))
        layers))