(ns top.kzre.krro.canvas.core.layer.render
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.merged :as merged]
    [top.kzre.krro.canvas.core.layer.util :as util]))

(def ^:dynamic *merge-layer!*
  (fn [^floats data w h source]
    (throw (UnsupportedOperationException. "*merge-layers!* not bound"))))

(defmulti render-batch!
          (fn [backend ^floats data w h layers] backend))

(defmulti render-layer!
          (fn [layer ^floats data w h] (:type layer)))

(defmethod render-batch! :default
  [_ data w h sources]
  (doseq [layer sources]
    (render-layer! layer data w h)))

(defmethod render-layer! :default
  [layer _ _ _]
  (throw (ex-info (str "No render-layer! implementation for type: " (:type layer))
                  {:layer layer})))

;; ── 辅助函数 ────────────────────────────────────

(defn- allocate-data [w h]
  (float-array (* w h 4)))

(defn- group-node? [x]
  (and (map? x) (= :group-node (:type x))))

;; ── 图层树展开 ─────────────────────────────────
(defn expand-layers [layers w h]
  (mapcat (fn [layer]
            (when (:visible? layer true)
              (if (group/group? layer)
                (if (util/pass-through? layer)
                  (expand-layers (:layers layer) w h)
                  [{:type :group-node
                    :group layer
                    :children (expand-layers (:layers layer) w h)}])
                [layer])))
          layers))

;; ── 组渲染（保留组属性）─────────────────────────
(declare render-children!)
(defn- render-group-node! [node data w h]
  (let [temp-data (allocate-data w h)]
    ;; 先渲染子栈到临时缓冲区
    (render-children! (:children node) temp-data w h)
    ;; 将临时数据与原始组的属性（opacity, blend-mode 等）绑定
    (let [src-merged (merged/make-merged-layer (:group node) temp-data)]
      ;; 使用 *merge-layers!* 将组结果混合到主画布，此时 src-merged 携带了组的混合属性
      (*merge-layer!* data w h src-merged))))

;; ── 批处理调度 ─────────────────────────────────
(defn render-children! [stack data w h]
  (let [batch (atom [])
        cur-be (atom nil)]
    (doseq [item stack]
      (if (group-node? item)
        (do
          (when (seq @batch)
            (render-batch! @cur-be data w h @batch)
            (reset! batch [])
            (reset! cur-be nil))
          (render-group-node! item data w h))
        (let [be (or (:backend item) :default)]
          (if (= @cur-be be)
            (swap! batch conj item)
            (do
              (when (seq @batch)
                (render-batch! @cur-be data w h @batch))
              (reset! batch [item])
              (reset! cur-be be))))))
    (when (seq @batch)
      (render-batch! @cur-be data w h @batch))))

;; ── 顶层入口 ────────────────────────────────────
(defn render-layers! [root-layers ^floats data w h]
  (let [stack (expand-layers root-layers w h)]
    (render-children! stack data w h)))