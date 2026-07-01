(ns top.kzre.krro.canvas.core.layer.core
  "图层操作：渲染多方法声明，以及通用的图层组构造函数（纯数据）。"
  (:require [top.kzre.krro.canvas.core.layer.spec]))

;; ── 渲染多方法 ─────────────────────────────────
(defmulti render-layer!
          "将图层数据 layer 渲染到 target-canvas 上，偏移 (dx,dy)。
           根据图层 :type 分派，默认抛出异常，需由下游模块实现具体类型。"
          (fn [layer target-canvas dx dy] (:type layer)))

(defmethod render-layer! :default
  [layer & _]
  (throw (ex-info (str "No render-layer! implementation for type: " (:type layer))
                  {:layer layer})))

;; ── 图层组构造（纯数据结构，递归容器） ─────────
(defn make-layer-group
  "创建一个图层组（图层容器）。图层组本身也是图层，其 :type 为 :group，
   额外包含 :layers 向量。具体渲染应由 :group 对应的 render-layer! 方法实现。"
  [& {:keys [id name opacity blend-mode visible? locked? layers]
      :or {id (keyword (str "group-" (gensym)))
           name "Group"
           opacity 1.0
           blend-mode :pass-through
           visible? true
           locked? false
           layers []}}]
  {:id id :type :group
   :name name :opacity opacity :blend-mode blend-mode
   :visible? visible? :locked? locked?
   :layers layers})