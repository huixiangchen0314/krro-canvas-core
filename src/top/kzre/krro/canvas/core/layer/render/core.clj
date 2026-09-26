(ns top.kzre.krro.canvas.core.layer.render.core
  "新的异步渲染入口。

   与旧同步 render 的差异：
     - 旧：(render f layers viewport-w viewport-h tile-size opts)
     - 新：(render layers opts) → Promise<Canvas>
       opts 包含 :viewport-w :viewport-h :tile-size :backend 等

   内部流程：
     1. build-batch 把图层列表切成批次（BackendBatch / GroupBatch）
     2. 顶层包成一个 GroupBatch，用空白画布作为 backdrop 渲染
     3. 返回 Promise<Canvas>，调用方拥有结果画布

   中间画布的清理由 batch/render 内部处理（tracker + 正常流显式 .clear），
   本层只负责初始画布的生命周期。"
  (:require
   [top.kzre.krro.canvas.core.layer.group :as group]
   [top.kzre.krro.canvas.core.layer.render.batch :as batch]
   [top.kzre.krro.canvas.core.layer.render.composite :as composite]
   [top.kzre.krro.canvas.core.layer.render.download :as download]
   [top.kzre.krro.canvas.core.layer.render.merged :as merged]
   [top.kzre.krro.core.util.promise :as promise])
  (:import
    (java.util Set)
    (top.kzre.krro.util.tile TiledCanvas)))


(defmethod batch/render-batch :default
  [_ ^TiledCanvas backdrop layers opts]
  (-> (download/download! backdrop)
      (promise/then
        (fn [^TiledCanvas c]
          (reduce
            (fn [p layer]
              (promise/then p
                            (fn [^TiledCanvas c]
                              (composite/composite-layer layer c opts))))
            (promise/resolved c)
            layers)))))



;; ═══════════════════════════════════════════════
;; 批次构建
;; ═══════════════════════════════════════════════

(defn- build-batches
  "递归构建批次向量。
   连续同后端图层 → BackendBatch
   :group 类型图层 → GroupBatch（递归构建其子批次）"
  [layers default-backend opts]
  (if-not (seq layers)
    []
    (loop [remaining layers
           result    []
           cur-be    nil
           cur-batch []]
      (if-not (seq remaining)
        (if (seq cur-batch)
          (cond-> result
                  (seq cur-batch) (conj (batch/backend-batch cur-be cur-batch)))
          result)
        (let [l (first remaining)]
          (if (group/group? l)
            ;; 组图层：先 flush 当前批次，再递归构建子批次
            (recur (rest remaining)
                   (cond-> result
                           (seq cur-batch) (conj (batch/backend-batch cur-be cur-batch))
                           true            (conj (batch/group-batch
                                                   l
                                                   (build-batches (:layers l) default-backend opts))))
                   nil
                   [])
            ;; 普通图层：按后端分组
            (let [be (:backend l default-backend)]
              (if (= be cur-be)
                (recur (rest remaining) result cur-be (conj cur-batch l))
                (recur (rest remaining)
                       (if (seq cur-batch)
                         (cond-> result
                                 (seq cur-batch) (conj (batch/backend-batch cur-be cur-batch)))
                         result)
                       be
                       [l])))))))))

(defn build-batch
  "从图层列表构建顶层批次向量。
   opts 用于获取默认后端 :backend（缺省 :default）。"
  [layers opts]
  (build-batches layers (:backend opts :default) opts))

;; ═══════════════════════════════════════════════
;; 渲染
;; ═══════════════════════════════════════════════

(defn render
  "执行渲染，返回 Promise<Canvas>。

   opts 键：
     :tile-size   TiledCanvas 瓦片大小（必需）
     :view-width  视口宽度（像素）
     :view-height  视口高度（像素）
     其他键透传给 render-batch / render-layer!

   返回的 Promise 解析为渲染完成的 Canvas，所有权转移给调用方。
   所有中间画布在成功/失败路径下均由内部清理。"
  [layers {:keys [dirty-tiles tile-size
                  view-width view-height]
           :as opts}]
  {:pre [(vector? layers)
         (or (set? dirty-tiles) (instance? Set dirty-tiles))
         (int? tile-size)
         (number? view-width)
         (number? view-height)]}
  (let [batches (build-batch layers opts)
        top     (batch/group-batch (merged/default-layer-attrs) batches)
        ;; TODO 颜色空间
        canvas  (TiledCanvas. tile-size)]
    (try
      (-> (batch/render top canvas opts)
          (promise/handle
            (fn [layer e]
              ;; 初始画布只作为 backdrop 模板，内部已 .copy；
              ;; 原始引用在这里统一释放
              (.clear canvas)
              (if e
                (throw e)                                   ;; 失败：清理后重抛
                (.keepTiles ^TiledCanvas (:canvas layer) dirty-tiles)))))  ; 成功：返回结果画布
      (catch Throwable e
        ;; batch/render 同步抛（罕见）时，初始画布也要释放
        (.clear canvas)
        (throw e)))))