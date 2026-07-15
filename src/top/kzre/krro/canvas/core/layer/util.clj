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

(defn compose-inverse-transform
  [{:keys [x y scale-x scale-y rotation]
    :or {x 0.0 y 0.0 scale-x 1.0 scale-y 1.0 rotation 0.0}}]
  (MathUtils/composeInverseTransform
    (float x) (float y)
    (float scale-x) (float scale-y)
    (float rotation)))

(defn transform-point
  "使用 6 元素仿射矩阵 [a b c d tx ty] 变换点 (px, py)。"
  [^floats matrix px py]
  (let [a (aget matrix 0), b (aget matrix 1), c (aget matrix 2)
        d (aget matrix 3), tx (aget matrix 4), ty (aget matrix 5)]
    {:x (+ (* a (double px)) (* c (double py)) (double tx))
     :y (+ (* b (double px)) (* d (double py)) (double ty))}))

(defn parent-inverse-transform
  "计算当前图层的父级世界矩阵的逆矩阵。
   layers 为原始图层列表（不含预处理），layer-path 为当前图层在列表中的路径。
   返回逆矩阵 (float-array) 或 nil（表示无父变换，即根级图层）。"
  [layers layer-path]
  (let [parent-path (butlast layer-path)]
    (when (seq parent-path)
      (loop [remaining-path parent-path
             current-matrix MathUtils/IDENTITY
             current-layers layers]
        (if (seq remaining-path)
          (let [idx (first remaining-path)
                layer (nth current-layers idx)
                local-matrix (MathUtils/composeLocalTransform
                               (float (get layer :x 0.0))
                               (float (get layer :y 0.0))
                               (float (get layer :scale-x 1.0))
                               (float (get layer :scale-y 1.0))
                               (float (get layer :rotation 0.0)))
                parent-matrix (MathUtils/multiply current-matrix local-matrix)]
            (recur (rest remaining-path)
                   parent-matrix
                   (:layers layer)))
          ;; 所有祖先处理完毕，求逆
          (MathUtils/invert current-matrix))))))


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

(defn root-path
  "构造一个根级路径。"
  [index]
  [index])

(defn sub-path
  "在父路径后追加一个索引，形成子路径。"
  [parent-path index]
  (conj parent-path index))

(defn parent-path
  "获取路径的父路径，如果是根级路径则返回空向量。"
  [path]
  (if (<= (count path) 1)
    []
    (butlast path)))



(defn find-layer-by-path
  "根据索引路径在图层列表中定位图层。
   path 为索引向量，如 [2 0] 表示根列表的第 2 个图层的第 0 个子图层。
   返回找到的图层 map，若路径无效则返回 nil。"
  [path layers]
  (reduce (fn [current-layer idx]
            (let [children (if (map? current-layer)
                             (:layers current-layer)
                             current-layer)]
              (nth children idx nil)))
          layers
          path))

(defn parent-container
  "返回路径所指向的父容器（子图层列表）。
   若 path 为空，返回 layers；否则返回父图层的 :layers。"
  [path layers]
  (if (seq path)
    (let [parent-path (parent-path path)
          parent-layer (when (seq parent-path)
                         (find-layer-by-path parent-path layers))]
      (if parent-layer
        (:layers parent-layer)
        layers))
    layers))

(defn top-path
  "返回图层列表最顶层（末尾）的路径，若列表为空则返回 nil。"
  [layers]
  (when (seq layers)
    [(dec (count layers))]))

(defn bottom-path
  "返回图层列表最底层（索引 0）的路径，若列表为空则返回 nil。"
  [layers]
  (when (seq layers)
    [0]))

(defn next-sibling-path
  "返回同一父容器中当前路径的下一个兄弟的路径，若已是最后一个则返回 nil。"
  [path layers]
  (let [parent-layers (parent-container path layers)
        idx (last path)
        next-idx (inc idx)]
    (when (< next-idx (count parent-layers))
      (conj (parent-path path) next-idx))))

(defn prev-sibling-path
  "返回同一父容器中当前路径的前一个兄弟的路径，若已是第一个则返回 nil。"
  [path layers]
  (let [idx (last path)
        prev-idx (dec idx)]
    (when (>= prev-idx 0)
      (conj (parent-path path) prev-idx))))


(defn find-layer-path
  "在图层列表中查找指定 layer-id 的索引路径。
   返回索引向量，如 [2 0] 表示根列表的第 2 个图层的第 0 个子图层。
   若未找到则返回 nil。"
  [layer-id layers]
  (some (fn [[ idx layer]]
          (if (= (:id layer) layer-id)
            [idx]
            (when (group/group? layer)
              (when-let [sub (find-layer-path layer-id (:layers layer))]
                (cons idx sub)))))
        (map-indexed vector layers)))

(defn insert-layer
  "在图层列表 layers 的指定路径处插入 layer。返回新的图层向量。
   path 为索引向量，如 [2] 表示根索引 2，[2 0] 表示组内索引。
   空路径表示插入到末尾。"
  [path layer layers]
  (if (seq path)
    (let [idx (last path)
          parent-path (butlast path)]
      (if (seq parent-path)
        ;; 有父路径：找到父组，递归更新其内部
        (let [parent-idx (first parent-path)
              parent (nth layers parent-idx)
              new-parent (assoc parent :layers (insert-layer (rest path) layer (:layers parent)))]
          (assoc layers parent-idx new-parent))
        ;; 直接根级插入
        (vec (concat (subvec layers 0 idx) [layer] (subvec layers idx)))))
    ;; 空路径：插入到末尾
    (conj (vec layers) layer)))

(defn remove-layer
  "从图层列表 layers 的指定路径移除图层。返回 [new-layers removed-layer]。
   path 为索引向量。"
  [path layers]
  (when (seq path)
    (let [idx (last path)
          parent-path (butlast path)]
      (if (seq parent-path)
        ;; 嵌套：找到父组，递归删除
        (let [parent-idx (first parent-path)
              parent (nth layers parent-idx)
              [new-children removed] (remove-layer (rest path) (:layers parent))
              new-parent (assoc parent :layers new-children)
              new-layers (assoc layers parent-idx new-parent)]
          [new-layers removed])
        ;; 根级删除
        (let [removed (nth layers idx)
              new-layers (vec (concat (subvec layers 0 idx) (subvec layers (inc idx))))]
          [new-layers removed])))))


(defn move-layer
  "将图层从 old-path 移动到 new-path，返回新的图层列表。
   old-path 和 new-path 均为索引向量。"
  [old-path new-path layers]
  (when (and (seq old-path) (seq new-path))
    (let [[temp-layers removed] (remove-layer old-path layers)
          same-parent? (= (butlast old-path) (butlast new-path))
          old-idx (last old-path)
          new-idx (last new-path)
          adjusted-new-path (if (and same-parent? (< old-idx new-idx))
                              (conj (butlast new-path) (dec new-idx))
                              new-path)]
      (insert-layer adjusted-new-path removed temp-layers))))


(defn flatten-layers
  "返回一个包含所有图层（包括嵌套图层组中的图层）的序列，
   每一项为 {:layer <图层map> :path <索引路径> :depth <深度>}。
   图层组本身也会出现在结果中。"
  ([layers] (flatten-layers layers [] 0))
  ([layers parent-path depth]
   (mapcat (fn [idx layer]
             (let [current-path (conj parent-path idx)]
               (cons {:layer layer :path current-path :depth depth}
                     (when (group/group? layer)
                       (flatten-layers (:layers layer) current-path (inc depth))))))
           (range)
           layers)))