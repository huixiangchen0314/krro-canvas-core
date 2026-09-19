(ns top.kzre.krro.canvas.core.layer.path
  "图层路径：图层或图层组在图层树中的位置标识。

   路径是索引向量，如 [2 0] 表示根列表第 2 个图层的第 0 个子图层。
   空向量 [] 表示根容器本身。

   ═══════════════════════════════════════════════
   设计原则
   ═══════════════════════════════════════════════

   本命名空间把\"图层操作\"分解为两段：

     第一段：id ↔ path
       用户持有 layer-id——通过 get-path 得到路径
       或反向：路径 → 图层 → 取 id

     第二段：path → path（导航）+ path → layer（访问）
       路径之间可以导航（上/下兄弟、父子、遍历）
       路径可以访问图层、兄弟列表、子路径

   外部操作只针对路径：
     - 不直接操作图层树
     - 不关心嵌套结构
     - 通过 path 完成所有定位

   这样图层操作的复杂度从\"树操作\"降为\"向量操作 + 树查询\"——
   路径是稳定的中间抽象。

   ═══════════════════════════════════════════════
   使用模式
   ═══════════════════════════════════════════════

     ;; id → path
     (def p (get-path layers :layer-42))

     ;; path → layer
     (get-layer layers p)

     ;; path → path（导航）
     (above-sibling-path layers p)
     (parent p)

     ;; path → 其他图层
     (get-layer layers (above-sibling-path layers p))

   参数顺序遵循 Clojure 惯例：layers 优先。
   检索函数统一使用 get- 前缀。

   ═══════════════════════════════════════════════
   职责边界
   ═══════════════════════════════════════════════

   本命名空间：
     ✓ 路径构造、变换、比较
     ✓ 按 id 查路径、按路径查图层
     ✓ 导航（父、子、兄弟、遍历）

   不属于本命名空间：
     ✗ 图层数据更新——由外部组合 (update-in layers ...)
     ✗ 图层属性的读取/修改——属于图层操作命名空间
     ✗ 图层的语义判断（可见性、类型）——属于图层定义"

  )

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

(defn append
  "在路径后追加一个索引。等于 child。"
  [path idx]
  (conj path idx))

(defn parent
  "获取路径的父路径。根级路径返回空向量。"
  [path]
  (if (<= (count path) 1)
    []
    (vec (butlast path))))

(defn with-index
  "替换路径的最后一段索引。"
  [path new-idx]
  (conj (parent path) new-idx))

(defn truncate
  "截断到指定深度。"
  [path n]
  (subvec path 0 (min n (count path))))

(defn path?
  "判断是否为合法路径（非负整数向量，空向量表示根）。"
  [x]
  (and (vector? x)
       (every? #(and (integer? %) (not (neg? %))) x)))

(defn root?
  "路径是否是根级路径（深度 ≤ 1）。
   注意：[0] 也是根级路径。"
  [path]
  (empty? (parent path)))

;; ═══════════════════════════════════════════════
;; 层级关系（纯路径操作）
;; ═══════════════════════════════════════════════

(defn ancestor?
  "a 是否是 b 的祖先路径。
   [1] 是 [1 2] [1 2 3] 的祖先。
   相等不算祖先。"
  [a b]
  (and (< (count a) (count b))
       (= a (subvec b 0 (count a)))))

(defn descendant?
  "a 是否是 b 的后代路径。"
  [a b]
  (ancestor? b a))

(defn sibling?
  "同一父下的不同路径。"
  [a b]
  (and (not= a b)
       (= (parent a) (parent b))))

(defn within?
  "a 是否在 b 的子树内（含 b 自身）。"
  [a b]
  (or (= a b) (ancestor? b a)))

(defn common-ancestor
  "两个路径的最深公共祖先路径。"
  [a b]
  (let [n (min (count a) (count b))]
    (loop [i 0]
      (if (and (< i n) (= (nth a i) (nth b i)))
        (recur (inc i))
        (subvec a 0 i)))))

(defn depth
  "路径深度。根级 = 1，空路径 = 0。"
  [path]
  (count path))

(defn index-in-parent
  "当前路径在父容器中的索引。空路径返回 nil。"
  [path]
  (when (seq path) (last path)))

(defn path<?
  "按 DFS 前序遍历，a 是否严格在 b 之前。"
  [a b]
  (cond
    (= a b)         false
    (ancestor? a b) true
    (ancestor? b a) false
    :else
    (let [ca (common-ancestor a b)
          ia (nth a (count ca))
          ib (nth b (count ca))]
      (< ia ib))))

;; ═══════════════════════════════════════════════
;; 第一段：id ↔ path
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

;; ═══════════════════════════════════════════════
;; 第二段：path → layer
;; ═══════════════════════════════════════════════

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
    (let [pp           (parent path)
          parent-layer (when (seq pp)
                         (get-layer layers pp))]
      (if parent-layer
        (:layers parent-layer)
        layers))
    layers))

(defn child-paths
  "父路径的所有子路径——升序。"
  [layers parent-path]
  (let [parent-layer (get-layer layers parent-path)]
    (mapv #(conj parent-path %) (range (count (:layers parent-layer))))))

(defn first-child-path
  "父路径的第一个子路径。无子返回 nil。"
  [layers parent-path]
  (let [parent-layer (get-layer layers parent-path)]
    (when (seq (:layers parent-layer))
      (conj parent-path 0))))

(defn last-child-path
  "父路径的最后一个子路径。无子返回 nil。"
  [layers parent-path]
  (let [parent-layer (get-layer layers parent-path)]
    (when (seq (:layers parent-layer))
      (conj parent-path (dec (count (:layers parent-layer)))))))

;; ═══════════════════════════════════════════════
;; 第二段：path → path（导航）
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

(defn above-sibling-path
  "同一父容器中当前路径的下一个兄弟路径。已是最后一个返回 nil。"
  [layers path]
  (let [siblings (get-siblings layers path)
        idx      (last path)
        next-idx (inc idx)]
    (when (< next-idx (count siblings))
      (conj (parent path) next-idx))))

(defn above-sibling-paths
  "同一父容器中，当前路径上方（索引更大）的所有兄弟路径。
   按从下到上的顺序返回（索引升序）。
   空路径或当前已是最后一个时返回 []。

   用于分段——取 [current, top] 中的上方部分。"
  [layers path]
  (if (empty? path)
    []
    (let [siblings (get-siblings layers path)
          n        (count siblings)
          idx      (last path)
          parent-p (parent path)]
      (mapv #(conj parent-p %) (range (inc idx) n)))))

(defn below-sibling-path
  "同一父容器中当前路径的前一个兄弟路径。已是第一个返回 nil。"
  [_layers path]
  (let [idx      (last path)
        prev-idx (dec idx)]
    (when (not (neg? prev-idx))
      (conj (parent path) prev-idx))))

(defn below-sibling-paths
  "同一父容器中，当前路径下方（索引更小）的所有兄弟路径。
   按从底到上的顺序返回（索引升序）。
   空路径或当前已是第一个时返回 []。

   用于分段——取 [0, current) 中的下方部分。"
  [layers path]
  (if (empty? path)
    []
    (let [siblings (get-siblings layers path)
          n        (count siblings)
          idx      (min (last path) n)
          parent-p (parent path)]
      (mapv #(conj parent-p %) (range idx)))))

(defn sibling-range-paths
  "同一父下，从 from-idx 到 to-idx（不含）的所有路径。"
  [parent-path from-idx to-idx]
  (mapv #(conj parent-path %) (range from-idx to-idx)))

(defn paths-between
  "同一父下，从 a 到 b（不含 b）的所有路径。
   a、b 必须同父。"
  [a b]
  (let [pa (parent a)
        pb (parent b)]
    (when (= pa pb)
      (sibling-range-paths pa (last a) (last b)))))

(defn next-path
  "按 DFS 前序遍历，下一个路径。
   优先进入子节点——若无子则找下一个兄弟——若无兄弟则回溯到祖先的兄弟。
   遍历结束返回 nil。"
  [layers path]
  (or (first-child-path layers path)
      (loop [p path]
        (when (seq p)
          (or (above-sibling-path layers p)
              (recur (parent p)))))))

(defn prev-path
  "按 DFS 前序遍历，前一个路径。
   若有兄弟——取兄弟的最深最后一个后代。"
  [layers path]
  (when-let [prev-sibling (below-sibling-path layers path)]
    (loop [p prev-sibling]
      (if-let [last-child (last-child-path layers p)]
        (recur last-child)
        p))))



(defn insert-layer
  "在图层列表 layers 的指定路径处插入 layer。返回新的图层向量。
   path 为索引向量，如 [2] 表示根索引 2，[2 0] 表示组内索引。
   空路径表示插入到末尾。"
  [layers path layer]
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
