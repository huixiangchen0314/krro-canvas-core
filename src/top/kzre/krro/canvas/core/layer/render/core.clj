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
   [top.kzre.krro.canvas.core.layer.render.merged :as merged]
   [top.kzre.krro.core.util.promise :as promise])
  (:import
   (top.kzre.krro.util.tile TiledCanvas)))

;; 默认逐个合成图层
(defmethod batch/render-batch :default
  [_ backdrop layers opts]
  (reduce
    (fn [canvas-promise layer]
      (promise/then
        canvas-promise
        (fn [^TiledCanvas canvas]
          (-> (composite/composite-layer layer canvas opts)
              (promise/handle
                (fn [new-canvas e]
                  ;; canvas 归我们所有（首轮是 .copy 的副本，后续是上一轮合成结果），
                  ;; 无论成败都不再需要，合成结束后释放
                  (try
                    (.clear canvas)
                    (catch Throwable _ nil))   ; 清理失败不掩盖合成异常
                  (if e (throw e) new-canvas)))))))
    (promise/resolved (.copy backdrop))   ; 首轮背景是 backdrop 的副本，归我们所有
    layers))



;; ═══════════════════════════════════════════════
;; 批次构建
;; ═══════════════════════════════════════════════

(defn- build-batches
  "递归构建批次向量。
   连续同后端图层 → BackendBatch
   :group 类型图层 → GroupBatch（递归构建其子批次）"
  [layers default-backend opts]
  (loop [remaining (seq layers)
         result    []
         cur-be    nil
         cur-batch []]
    (if-not remaining
      (cond-> result
              (seq cur-batch) (conj (batch/backend-batch cur-be cur-batch)))
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
                     (cond-> result
                             (seq cur-batch) (conj (batch/backend-batch cur-be cur-batch)))
                     be
                     [l]))))))))

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
     :view-matrix 视口仿射变换矩阵
     :backend     默认后端（:cpu / :gl / :default），缺省 :default
     其他键透传给 render-batch / render-layer!

   返回的 Promise 解析为渲染完成的 Canvas，所有权转移给调用方。
   所有中间画布在成功/失败路径下均由内部清理。

   注意：顶层 GroupBatch 的 group 为 nil；merged/make-merged-layer
   必须能处理 nil 组，否则需要在调用处传一个占位组记录。"
  [layers opts]
  (let [{:keys [tile-size]} opts
        _       (when-not tile-size
                  (throw (ex-info "render: opts must contain :tile-size"
                                  {:opts opts})))
        batches (build-batch layers opts)
        top     (batch/group-batch (merged/default-layer-attrs) batches)
        canvas  (TiledCanvas. tile-size)]
    (try
      (-> (batch/render top canvas opts)
          (promise/handle
            (fn [layer e]
              ;; 初始画布只作为 backdrop 模板，内部已 .copy；
              ;; 原始引用在这里统一释放
              (.clear canvas)
              (if e
                (throw e)                    ; 失败：清理后重抛
                (:canvas layer)))))          ; 成功：返回结果画布
      (catch Throwable e
        ;; batch/render 同步抛（罕见）时，初始画布也要释放
        (.clear canvas)
        (throw e)))))