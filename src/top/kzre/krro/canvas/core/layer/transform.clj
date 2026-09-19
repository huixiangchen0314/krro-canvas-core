(ns top.kzre.krro.canvas.core.layer.transform
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.path :as path])
  (:import (top.kzre.krro.util.math KMath)))

(defonce ^:private identity-matrix (KMath/mat2dIdentity))

(defn local-transform
  "从图层 map 提取变换参数，委托 Java 生成矩阵。"
  [{:keys [x y scale-x scale-y rotation]
    :or {x 0.0 y 0.0 scale-x 1.0 scale-y 1.0 rotation 0.0}}]
  (KMath/mat2dCompose
    (float x) (float y)
    (float scale-x) (float scale-y)
    (float rotation)))

(defn layer-transform
  "计算图层局部坐标系 → 世界坐标系的仿射变换矩阵。
   layer-path - 图层在层级中的索引路径（如 [0 1]）
   layers     - 顶层图层列表
   返回 float-array 长度 6，若路径无效则返回单位矩阵。"
  [layer-path layers]
  (loop [remaining-path layer-path
         current-matrix identity-matrix
         current-layers layers]
    (if-let [idx (first remaining-path)]
      (let [current-layer (nth current-layers idx)
            local-matrix  (local-transform current-layer)
            world-matrix  (KMath/mat2dMul current-matrix local-matrix)]
        (if-let [rest-path (seq (rest remaining-path))]
          (recur rest-path world-matrix (:layers current-layer))
          world-matrix))
      current-matrix)))

(defn layer-transform-inverse
  "计算图层局部坐标系 → 世界坐标系的仿射变换矩阵的逆矩阵。
   path - 图层在层级中的索引路径（如 [0 1]）
   layers     - 顶层图层列表
   返回 float-array 长度 6，若矩阵不可逆则返回 nil。"
  [layers path]
  (KMath/mat2dInv (layer-transform path layers)))

(defn parent-transform
  "计算当前图层的父级世界变换矩阵。
   path - 当前图层在层级中的索引路径
   layers     - 顶层图层列表
   返回 float-array 长度 6，若当前图层为根级图层（无父级）则返回 nil。"
  [layers path]
  (let [parent-path (path/parent path)]
    (if (seq parent-path)
      (layer-transform parent-path layers)
      identity-matrix)))


(defn compose-transforms
  "递归预计算图层树的变换矩阵，把每个图层的局部变换合成为绝对变换。

  每个图层节点携带 :transform 字段（局部变换矩阵）。本函数自顶向下遍历：
    1. 从当前图层的局部变换与父级绝对变换相乘，得到当前图层的绝对变换
    2. 把绝对变换写回该图层的 :transform 字段
    3. 若是图层组，递归处理其子图层（以当前图层的绝对变换作为子图层的父级变换）

  参数：
    - layer      : 图层或图层组（根节点），携带 :transform 局部变换
    - :viewport  : (可选) 视口变换矩阵，作为根节点的初始父级变换。
                   未提供时使用单位矩阵，即图层的绝对变换等价于从世界坐标系算起。

  返回值：
    与输入结构相同的新图层树，其中每个节点的 :transform 字段
    已被替换为该节点的绝对变换（世界空间 → 视口空间）。

  示例：
    ;; 单位视口：绝对变换即世界坐标
    (transform layer)
    ;; 带视口：把世界空间进一步投影到屏幕空间
    (transform layer :viewport viewport-matrix)"
  ([layer & {:keys [viewport]}]
   (letfn [(transform-layer [layer parent-transform]
             (let [local-transform (local-transform layer)
                   world-transform (KMath/mat2dMul parent-transform local-transform)
                   processed (assoc layer :transform world-transform)]
               (if (group/group? layer)
                 (let [children (:layers layer)]
                   (assoc processed :layers (mapv #(transform-layer % world-transform) children)))
                 processed)))]
     (if-let [viewport-transform viewport]
       (transform-layer layer viewport-transform)
       (transform-layer layer identity-matrix)))))