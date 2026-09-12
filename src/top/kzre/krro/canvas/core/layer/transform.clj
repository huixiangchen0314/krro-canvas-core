(ns top.kzre.krro.canvas.core.layer.transform
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.util :as util])
  (:import (top.kzre.krro.util.math KMath)))

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
             (let [local-transform (util/compose-local-transform layer)
                   world-transform (KMath/mat2dMul parent-transform local-transform)
                   processed (assoc layer :transform world-transform)]
               (if (group/group? layer)
                 (let [children (:layers layer)]
                   (assoc processed :layers (mapv #(transform-layer % world-transform) children)))
                 processed)))]
     (if-let [viewport-transform viewport]
       (transform-layer layer viewport-transform)
       (transform-layer layer util/identity-matrix)))))