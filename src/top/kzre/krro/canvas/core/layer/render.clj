(ns top.kzre.krro.canvas.core.layer.render
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.merged :as merged]
    [top.kzre.krro.canvas.core.layer.util :as util])
  (:import
    (top.kzre.krro.canvas.core.layer PixelBlitter)
    (top.kzre.krro.util.tile Canvas TiledCanvas)))

;; ── 动态混合函数 ──────────────────────────────────
(def ^:dynamic *merge-layer!*
  (fn [^Canvas _dest _source _w _h]
    (throw (UnsupportedOperationException. "*merge-layer!* not bound"))))

(defn- merge-layer-impl
  [^Canvas dest source w h]
  (let [src-canvas (:canvas source)                          ;; source 现在是 Canvas
        blend-mode (util/blend-mode-str (:blend-mode source) :normal)
        opacity    (float (get source :opacity 1.0))
        transform  (get source :transform util/identity-matrix)]
    (PixelBlitter/blit dest w h src-canvas transform blend-mode opacity)))

(defn use-raster-merge-layer!
  []
  (alter-var-root #'*merge-layer!* (constantly merge-layer-impl)))
(use-raster-merge-layer!)

;; ── 批渲染分发 ──────────────────────────────────
(defmulti render-batch!
          (fn [backend ^Canvas _canvas _w _h _layers _opts] backend))

;; ── 图层渲染分发 ────────────────────────────────
(defmulti render-layer!
          (fn [layer ^Canvas _canvas _w _h _opts] (:type layer)))

(defmethod render-batch! :default
  [_ canvas w h layers opts]
  (doseq [layer layers]
    (render-layer! layer canvas w h opts)))

(defmethod render-layer! :default
  [layer _ _ _ _ _]
  (throw (ex-info (str "No render-layer! implementation for type: " (:type layer))
                  {:layer layer})))

;; ── 辅助 ─────────────────────────────────────────
(defn- group-node? [x]
  (and (map? x) (= :group-node (:type x))))

;; ── 展开图层树 ──────────────────────────────────
(defn expand-layers
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
(defn- render-group-node! [node ^Canvas dest-canvas w h opts]
  ;; 创建临时画布用于子图层渲染
  (let [temp-canvas (TiledCanvas. (.getTileSize dest-canvas))]
    (render-children! (:children node) temp-canvas w h opts)
    (let [src-merged (merged/make-merged-layer (:group node) temp-canvas)]
      (*merge-layer!* dest-canvas src-merged w h))))

;; ── 遍历渲染栈 ──────────────────────────────────
(defn render-children!
  "遍历渲染栈，执行批处理和组渲染。opts 透传给各渲染函数。"
  [stack ^Canvas canvas w h opts]
  (let [batch (atom [])
        cur-be (atom nil)]
    (doseq [item stack]
      (if (group-node? item)
        (do
          (when (seq @batch)
            (render-batch! @cur-be canvas w h @batch opts)
            (reset! batch [])
            (reset! cur-be nil))
          (render-group-node! item canvas w h opts))
        (let [be (or (:backend item) :default)]
          (if (= @cur-be be)
            (swap! batch conj item)
            (do
              (when (seq @batch)
                (render-batch! @cur-be canvas w h @batch opts))
              (reset! batch [item])
              (reset! cur-be be))))))
    (when (seq @batch)
      (render-batch! @cur-be canvas w h @batch opts))))