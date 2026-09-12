(ns top.kzre.krro.canvas.core.layer.render.canvas-tracker
  "线程安全的画布追踪器。

   用途：在可能失败且可能并发的场景中，追踪一组拥有所有权的画布，
   失败时统一清理，并保证『失败后新登记的画布』也能被立即清理，
   不存在『handle 已清理 → 后续 track! 塞入失效列表』的泄漏窗口。

   语义：
     - track! : 登记画布；若已处于失败态，则立即清理并返回原画布
     - fail!  : 标记失败并清理所有已登记画布，幂等
     - failed?: 查询当前是否已进入失败态

   并发不变量：
     track! 与 fail! 通过 CAS 互斥，任一画布要么在 canvases 里被 fail! 清，
     要么在 track! 时读到 failed 已置位而立即自清。无泄漏窗口。

   前提：TiledCanvas.clear 幂等、可重入（正常流可能已清过的画布会被 fail! 再清一次）。"
  (:import
    (top.kzre.krro.util.tile TiledCanvas)))

;; ═══════════════════════════════════════════════
;; 状态
;; ═══════════════════════════════════════════════

(defrecord TrackerState [canvases failed])

;; ═══════════════════════════════════════════════
;; 构造 / 查询
;; ═══════════════════════════════════════════════

(defn tracker
  "创建一个画布追踪器，返回 atom<TrackerState>。"
  []
  (atom (->TrackerState [] false)))

(defn failed?
  "当前是否已进入失败态。"
  [t]
  (:failed @t))

;; ═══════════════════════════════════════════════
;; 登记 / 失败
;; ═══════════════════════════════════════════════

(defn track!
  "登记画布。
   - 未失败：加入 canvases
   - 已失败：立即清理该画布
   返回 canvas 本身，便于链式调用；canvas 为 nil 时不做任何事，返回 nil。"
  [t canvas]
  (when canvas
    (loop []
      (let [s @t]
        (cond
          (:failed s)
          (.clear ^TiledCanvas canvas)

          (compare-and-set! t s (update s :canvases conj canvas))
          nil

          :else
          (recur)))))
  canvas)

(defn fail!
  "标记失败并清理所有已登记画布。幂等。
   单个画布 clear 抛异常被吞掉，不掩盖调用方的原始异常。"
  [t]
  (loop []
    (let [s @t]
      (when-not (:failed s)
        (if (compare-and-set! t s (assoc s :failed true :canvases []))
          (run! (fn [^TiledCanvas c]
                  (try (.clear c)
                       (catch Throwable _ nil)))
                (:canvases s))
          (recur))))))