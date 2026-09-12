(ns top.kzre.krro.canvas.core.layer.render.batch
  "线性批次渲染。

   两类批次：
     - BackendBatch : 同一 backend 的连续图层组
     - GroupBatch   : 单个图层组（需递归处理）

   批次通过 reduce 折叠出状态 [layer-promises backdrop]：
     - BackendBatch 步骤：flush 已有图层到 backdrop → 在合并结果上原地渲染 → 重置空白 backdrop
     - GroupBatch   步骤：用空白背景原地渲染该组 → 图层追加进列表

   ═══════════════════════════════════════════════
   画布所有权契约（原地写模型）
   ═══════════════════════════════════════════════

   被调用方（render-batch / render / merge-fn）：
     - 原地写入参 canvas，返回 Layer 的 :canvas 通常等于入参 canvas。
     - 入参 canvas 的所有权随调用转移给被调用方；调用方若要保留原画布，
       必须在传入前自行 .copy。
     - merge-fn 例外：允许返回新画布（新画布所有权转移给调用方），
       也允许原地写并返回 backdrop。调用方不能假设是哪种，两种都兼容。

   GroupBatch：
     - 拥有以下画布，全部登记到 canvas-tracker：
         1. 入参 backdrop-canvas（所有权随调用转移）
         2. 所有 new-canvas-fn 创建的空白画布
         3. merge-fn 返回的画布（可能在 merge-into-backdrop 里新建）
     - 成功：accumulated 的 layer.canvas 在 finalize 的 clear-layers! 中清理；
             最终 merged-canvas 返回给调用方，不清理。
     - 失败：canvas-tracker/fail! 统一清理所有已登记画布。
             track! / fail! 通过 CAS 互斥，保证『fail! 之后才到达的 track!』
             也能立即自清，无泄漏窗口。

   ═══════════════════════════════════════════════
   正常流下的画布清理
   ═══════════════════════════════════════════════

     - clear-layers!           清理 accumulated 的中间图层画布
     - 最终 backdrop            作为 merged-canvas 返回，不清理
     - finalize catch 内 .clear 清理 make-merged-layer 失败时的输出画布
                                （可能等于 backdrop，幂等清理无害）

   ═══════════════════════════════════════════════
   前提
   ═══════════════════════════════════════════════

     - TiledCanvas.clear 幂等、可重入
     - merge-fn 不修改入参 layers 的内部结构
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
          "渲染批次，原地写入参 backdrop，返回 Promise<Canvas>。
           backdrop 的 canvas 所有权随调用转移给 render-batch。"
          (fn [backend _backdrop _layers _opts] backend))

(defprotocol IBatch
  (render [_ backdrop-canvas opts]
    "渲染批次，原地写 backdrop-canvas，返回 Promise<Layer>。
     backdrop-canvas 的所有权随调用转移给本函数。"))

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

   merge-fn 契约：通常原地写 backdrop 并返回它（此时 merged == backdrop，
   已在 tracker 中，本次 track! 是冗余但幂等的）；也允许返回新画布
   （新画布所有权转移给调用方，本次 track! 完成登记）。

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

   有累积图层时：合并到 backdrop，再用合并结果原地渲染；
   无累积图层时：直接在 backdrop 上原地渲染。

   两种情况下 layer.canvas 都是状态里的 backdrop 本身
   （已由 new-canvas-fn 或 merge-into-backdrop 登记）。
   新状态里的 backdrop 由 new-canvas-fn 创建并登记。"
  [batch [layer-promises ^TiledCanvas backdrop] opts merge-fn new-canvas-fn track!]
  (if (seq layer-promises)
    (let [rendered
          (-> (promise/all layer-promises)
              (promise/then
                (fn [layers]
                  (merge-into-backdrop layers backdrop opts merge-fn track!)))
              (promise/then
                (fn [new-backdrop]
                  ;; render 原地写 new-backdrop，layer.canvas == new-backdrop
                  (render batch new-backdrop opts))))]
      [[rendered] (new-canvas-fn)])
    (let [rendered (render batch backdrop opts)]
      [[rendered] (new-canvas-fn)])))

(defn- render-group-step
  "GroupBatch 步骤。用空白背景原地渲染该组，图层追加进累积列表。

   empty-backdrop 由 new-canvas-fn 创建（已登记），
   render 原地写它并作为 layer.canvas 返回；
   它会在后续 merge-into-backdrop 或 finalize 的 clear-layers! 中清理。
   此处不 track——因为 layer.canvas 就是 empty-backdrop，已经在 tracker 里。"
  [batch [layer-promises backdrop] opts _merge-fn new-canvas-fn _track!]
  (let [empty-backdrop (new-canvas-fn)
        layer-p        (render batch empty-backdrop opts)]
    [(conj layer-promises layer-p) backdrop]))

;; ═══════════════════════════════════════════════
;; 批次记录
;; ═══════════════════════════════════════════════

(defrecord BackendBatch
  [backend    ;; keyword  后端标识（:cpu / :gl / :default）
   layers]    ;; vector   同 backend 的图层列表
  IBatch
  (render [_ backdrop-canvas opts]
    (-> (render-batch backend backdrop-canvas layers opts)
        (promise/fmap merged/make-merged-layer))))

(defrecord GroupBatch
  [group batches]
  IBatch
  (render [_ backdrop-canvas opts]
    (let [merge-fn  merge/*merge-layers*
          tile-size (.getTileSize backdrop-canvas)

          ;; canvas-tracker 登记 GroupBatch 拥有的全部画布：
          ;;   1. 入参 backdrop-canvas（所有权随调用转移）
          ;;   2. 所有 new-canvas-fn 创建的空白画布
          ;;   3. merge-fn 返回的画布（merge-into-backdrop 内登记）
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
                              (merged/make-merged-layer group merged-canvas)
                              (catch Throwable e
                                ;; 失败：清理输出画布。若 merged-canvas == backdrop，
                                ;; 它已在 tracker 里；下面的 fail! 会再清一次（幂等无害）。
                                (.clear merged-canvas)
                                (throw e))))))))))]

      (-> (reduce step
                  ;; 入参 backdrop-canvas 所有权随调用转移给 GroupBatch，
                  ;; 登记后作为状态 backdrop 原地写，最终作为 merged-canvas 返回。
                  (promise/resolved [[] (track! backdrop-canvas)])
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