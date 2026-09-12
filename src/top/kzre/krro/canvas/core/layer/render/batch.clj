(ns top.kzre.krro.canvas.core.layer.render.batch
  "线性批次渲染。

   两类批次：
     - BackendBatch : 同一 backend 的连续图层组
     - GroupBatch   : 单个图层组（需递归处理）

   批次通过 reduce 折叠出状态 [layer-promises backdrop]：
     - BackendBatch 步骤：flush 已有图层到 backdrop → 在合并结果上渲染该批次 → 重置空白 backdrop
     - GroupBatch   步骤：用空白背景渲染该组 → 图层追加进列表

   画布所有权约定：
     - 被调用方（merge-fn / render-batch / render）不获取入参所有权，不清理入参；
       返回的画布所有权转移给调用方。
     - GroupBatch 拥有自己创建的和通过返回值获得的所有画布，负责清理它们，
       除非再次转移出去（如 merged-canvas 交给调用方）。

   清理策略：
     - 正常流：每个中间画布在链上被显式 .clear。
     - 异常流：所有 GroupBatch 拥有的画布登记到 canvas-tracker；
       失败时调用 fail! 统一清理。tracker 的 track! / fail! 通过 CAS 互斥，
       保证『fail! 之后才到达的 track!』也能立即自清，无泄漏窗口。
     - 前提：TiledCanvas.clear 幂等、可重入。
   "
  (:require
    [top.kzre.krro.canvas.core.layer.render.canvas-tracker :as canvas-tracker]
    [top.kzre.krro.canvas.core.layer.render.merge :as merge]
    [top.kzre.krro.canvas.core.layer.render.merged :as merged]
    [top.kzre.krro.core.util.promise :as promise])
  (:import
    (top.kzre.krro.util.tile TiledCanvas)))

;; ═══════════════════════════════════════════════
;; 协议 / 多方法
;; ═══════════════════════════════════════════════

(defmulti render-batch
          "渲染批次，返回 Promise<Canvas>"
          (fn [backend _backdrop _layers _opts] backend))

(defprotocol IBatch
  (render [_ backdrop-canvas opts] "渲染批次，返回 Promise<Layer>"))

;; ═══════════════════════════════════════════════
;; 辅助
;; ═══════════════════════════════════════════════

(defn- clear-layer-canvas!
  [layer]
  (when-let [^TiledCanvas canvas (:canvas layer)]
    (.clear canvas)))

(defn- clear-layers!
  [layers]
  (run! clear-layer-canvas! layers))

(defn- merge-into-backdrop
  "把已解析图层合并进 backdrop，随后清理这些图层的画布。
   merge-fn 返回的画布归调用方（GroupBatch）所有，登记到 track!。
   返回 Promise<merged-canvas>。"
  [layers ^TiledCanvas backdrop opts merge-fn track!]
  (-> (merge-fn layers backdrop opts)
      (promise/fmap
        (fn [merged]
          (track! merged)
          (clear-layers! layers)
          merged))))

;; ═══════════════════════════════════════════════
;; reduce 步骤
;; ═══════════════════════════════════════════════

(defn- render-backend-step
  "BackendBatch 步骤。
   状态 [layer-promises backdrop] → [new-layer-promises new-backdrop]。

   有累积图层时：合并到 backdrop，清理旧 backdrop，再用合并结果渲染；
   无累积图层时：直接在当前 backdrop 上渲染，再清理。"
  [batch [layer-promises ^TiledCanvas backdrop] opts merge-fn new-canvas-fn track!]
  (if (seq layer-promises)
    (let [rendered
          (-> (promise/all layer-promises)
              (promise/then
                (fn [layers]
                  (merge-into-backdrop layers backdrop opts merge-fn track!)))
              (promise/then
                (fn [new-backdrop]
                  (.clear backdrop)
                  (-> (render batch new-backdrop opts)
                      (promise/fmap
                        (fn [layer]
                          (.clear ^TiledCanvas new-backdrop)
                          (track! (:canvas layer))
                          layer))))))]
      [[rendered] (new-canvas-fn)])
    (let [rendered
          (-> (render batch backdrop opts)
              (promise/fmap
                (fn [layer]
                  (.clear ^TiledCanvas backdrop)
                  (track! (:canvas layer))
                  layer)))]
      [[rendered] (new-canvas-fn)])))

(defn- render-group-step
  "GroupBatch 步骤。用空白背景渲染该组，图层追加进累积列表。"
  [batch [layer-promises backdrop] opts _merge-fn new-canvas-fn track!]
  (let [empty-backdrop (new-canvas-fn)
        layer-p        (-> (render batch empty-backdrop opts)
                           (promise/fmap
                             (fn [layer]
                               (.clear ^TiledCanvas empty-backdrop)
                               (track! (:canvas layer))
                               layer)))]
    [(conj layer-promises layer-p) backdrop]))

;; ═══════════════════════════════════════════════
;; 批次记录
;; ═══════════════════════════════════════════════

(defrecord BackendBatch
  [backend    ;; keyword  后端标识（:cpu / :gl / :default）
   layers]    ;; vector   同 backend 的图层列表
  IBatch
  (render [_ ^TiledCanvas backdrop-canvas opts]
    (-> (render-batch backend backdrop-canvas layers opts)
        (promise/fmap merged/make-merged-layer))))

(defrecord GroupBatch
  [group batches]
  IBatch
  (render [_ ^TiledCanvas backdrop-canvas opts]
    (let [merge-fn  merge/*merge-layers*
          tile-size (.getTileSize backdrop-canvas)

          ;; 画布追踪器：登记 GroupBatch 拥有的全部画布。
          ;; 正常流下每个中间画布已在链上被 .clear，此表仅服务于失败路径。
          tracker   (canvas-tracker/tracker)
          track!    #(canvas-tracker/track! tracker %)
          new-canvas-fn #(track! (TiledCanvas. tile-size))

          step (fn [pacc batch]
                 (promise/fmap
                   pacc
                   (fn [state]
                     ((if (instance? BackendBatch batch)
                        render-backend-step
                        render-group-step)
                      batch state opts merge-fn new-canvas-fn track!))))

          finalize
          (fn [[layer-promises ^TiledCanvas backdrop]]
            ;; 先把累积的 Promise 解析成实际图层再 merge，
            ;; 否则 merge-fn 收到的是 Promise 向量，clear-layers! 也会拿不到 :canvas
            (-> (promise/all layer-promises)
                (promise/then
                  (fn [layers]
                    (-> (merge-fn layers backdrop opts)
                        (promise/fmap
                          (fn [merged-canvas]
                            (try
                              (clear-layers! layers)
                              (.clear backdrop)
                              (merged/make-merged-layer group merged-canvas)
                              (catch Throwable e
                                ;; 失败：清理输出画布，重新抛出异常
                                (.clear merged-canvas)
                                (throw e))))))))))]

      (-> (reduce step
                  (promise/resolved [[] (track! (.copy backdrop-canvas))])
                  batches)
          (promise/then finalize)
          (promise/handle
            (fn [v e]
              (if e
                (do
                  ;; 原子失败：清掉此刻已登记的画布，
                  ;; 并置失败态，使之后到达的 track! 立即自清
                  (canvas-tracker/fail! tracker)
                  (throw e))
                v)))))))

;; ═══════════════════════════════════════════════
;; 构造辅助
;; ═══════════════════════════════════════════════

(defn backend-batch [backend layers]
  (->BackendBatch backend (vec layers)))

(defn group-batch [group batches]
  (->GroupBatch group batches))

;; ═══════════════════════════════════════════════
;; 谓词
;; ═══════════════════════════════════════════════

(defn backend-batch? [x]
  (instance? BackendBatch x))

(defn group-batch? [x]
  (instance? GroupBatch x))