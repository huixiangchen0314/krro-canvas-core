(ns top.kzre.krro.canvas.core.layer.render
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.merged :as merged]
    [top.kzre.krro.canvas.core.layer.util :as util]))

(def ^:dynamic *merge-layer!*
  (fn [^floats data w h source]
    (throw (UnsupportedOperationException. "*merge-layer!* not bound"))))

(defmulti render-batch!
          (fn [backend ^floats _data _w _h _layers _opts] backend))

;; CPU 渲染分发.
(defmulti render-layer!
          (fn [layer ^floats _data _w _h _opts] (:type layer)))


(defmethod render-batch! :default
  [_ data w h sources opts]
  (doseq [layer sources]
    (render-layer! layer data w h opts)))

(defmethod render-layer! :default
  [layer _ _ _ _]
  (throw (ex-info (str "No render-layer! implementation for type: " (:type layer))
                  {:layer layer})))

;; ── 辅助 ─────────────────────────────────────────
(defn- group-node? [x]
  (and (map? x) (= :group-node (:type x))))

;; ── 展开 ─────────────────────────────────────────
(defn expand-layers
  "将图层树展开为扁平栈（含组节点）。传入图层应已完成预处理和蒙板解析。"
  [layers]
  (mapcat (fn [layer]
            (when (:visible? layer true)
              (if (group/group? layer)
                (if (util/pass-through? layer)
                  (expand-layers (:layers layer))
                  [{:type :group-node
                    :group layer
                    :children (expand-layers (:layers layer))}])
                [layer])))
          layers))

;; ── 组渲染 ──────────────────────────────────────
(declare render-children!)
(defn- render-group-node! [node data w h opts]
  (let [temp-data (util/allocate-data w h)]
    (render-children! (:children node) temp-data w h opts)
    (let [src-merged (merged/make-merged-layer (:group node) temp-data)]
      (*merge-layer!* data w h src-merged))))

;; ── 批处理 ──────────────────────────────────────
(defn render-children!
  "遍历渲染栈，执行批处理和组渲染。opts 透传给各渲染函数。"
  [stack ^floats data w h opts]
  (let [batch (atom [])
        cur-be (atom nil)]
    (doseq [item stack]
      (if (group-node? item)
        (do
          (when (seq @batch)
            (render-batch! @cur-be data w h @batch opts)
            (reset! batch [])
            (reset! cur-be nil))
          (render-group-node! item data w h opts))
        (let [be (or (:backend item) :default)]
          (if (= @cur-be be)
            (swap! batch conj item)
            (do
              (when (seq @batch)
                (render-batch! @cur-be data w h @batch opts))
              (reset! batch [item])
              (reset! cur-be be))))))
    (when (seq @batch)
      (render-batch! @cur-be data w h @batch opts))))
