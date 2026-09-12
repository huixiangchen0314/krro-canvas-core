(ns top.kzre.krro.canvas.core.layer.path
  "图层路径：图层或图层组在图层树中的位置标识。

   路径是索引向量，如 [2 0] 表示根列表第 2 个图层的第 0 个子图层。
   空向量 [] 表示根容器本身。

   本命名空间只处理\"路径\"——构造、查找、修改、变换。
   图层数据的检索与更新属于其它命名空间。

   参数顺序遵循 Clojure 惯例：集合（layers）优先。
   检索函数统一使用 get- 前缀。")

;; ═══════════════════════════════════════════════
;; 路径构造（纯路径操作）
;; ═══════════════════════════════════════════════

(defn root
  "从索引构造根级路径。"
  [index]
  [index])

(defn child
  "在父路径后追加索引，形成子路径。"
  [path index]
  (conj path index))

(defn parent
  "获取路径的父路径。根级路径返回空向量。"
  [path]
  (if (<= (count path) 1)
    []
    (vec (butlast path))))

(defn path?
  "判断是否为合法路径（非负整数向量，空向量表示根）。"
  [x]
  (and (vector? x)
       (every? #(and (integer? %) (not (neg? %))) x)))


;; ═══════════════════════════════════════════════
;; 按路径访问
;; ═══════════════════════════════════════════════

(defn get-path
  "查找指定 layer-id 的索引路径。未找到返回 nil。
   返回向量（不是 seq）。"
  [layers layer-id]
  (some (fn [[idx layer]]
          (if (= (:id layer) layer-id)
            [idx]
            (when (map? layer)
              (when-let [sub (get-path (:layers layer) layer-id)]
                (into [idx] sub)))))
        (map-indexed vector layers)))


(defn get-layer
  "按索引路径查找图层。路径无效返回 nil。"
  [layers path]
  (reduce (fn [node idx]
            (let [children (if (map? node) (:layers node) node)]
              (nth children idx nil)))
          layers
          path))


(defn get-siblings
  "路径所指向的兄弟图层，包括自己。
   空路径返回 layers 本身。"
  [layers path]
  (if (seq path)
    (let [pp (parent path)
          parent-layer (when (seq pp)
                         (get-layer layers pp))]
      (if parent-layer
        (:layers parent-layer)
        layers))
    layers))


;; ═══════════════════════════════════════════════
;; 树导航（layers 优先，返回路径）
;; ═══════════════════════════════════════════════

(defn top-path
  "顶层（末尾）路径。列表为空返回 nil。"
  [layers]
  (when (seq layers)
    [(dec (count layers))]))

(defn bottom-path
  "底层（索引 0）路径。列表为空返回 nil。"
  [layers]
  (when (seq layers)
    [0]))

(defn next-sibling-path
  "同一父容器中当前路径的下一个兄弟路径。已是最后一个返回 nil。"
  [layers path]
  (let [siblings (get-siblings layers path)
        idx      (last path)
        next-idx (inc idx)]
    (when (< next-idx (count siblings))
      (conj (parent path) next-idx))))

(defn prev-sibling-path
  "同一父容器中当前路径的前一个兄弟路径。已是第一个返回 nil。"
  [layers path]
  (let [idx      (last path)
        prev-idx (dec idx)]
    (when (not (neg? prev-idx))
      (conj (parent path) prev-idx))))
