(ns top.kzre.krro.canvas.core.layer.render
  (:require
    [top.kzre.krro.canvas.core.layer.merged :as merged]
    [top.kzre.krro.canvas.core.layer.util :as util])
  (:import
    (top.kzre.krro.canvas.core.layer PixelBlitter)
    (top.kzre.krro.util.tile TiledCanvas)))

(defonce default-merge-layer-fn
         (fn [^TiledCanvas canvas layer viewport-w viewport-h {:keys [dirty-tiles]}]
           (let [out-canvas (.copy canvas)
                 src-canvas (:canvas layer) ;; layer 现在是 Canvas
                 blend-mode (util/blend-mode-str (:blend-mode layer) :normal)
                 opacity    (float (get layer :opacity 1.0))]
             (PixelBlitter/blit out-canvas viewport-w viewport-h src-canvas (:transform layer) blend-mode opacity dirty-tiles false)
             out-canvas)))

(def ^:dynamic *merge-layer* default-merge-layer-fn)

;; ── 批渲染分发 ──────────────────────────────────
(defmulti render-batch
          (fn [backend _canvas _w _h _layers _opts] backend))

;; ── cpu渲染分发 ────────────────────────────────
(defmulti render-layer!
          (fn [layer _canvas _w _h _opts] (:type layer)))

(defmethod render-batch :default
  [_ ^TiledCanvas  canvas w h layers opts]
  (let [out-canvas (.copy canvas)]
    (doseq [layer layers]
      (render-layer! layer out-canvas w h opts))
    out-canvas))

(defmethod render-layer! :default
  [layer _ _ _ _]
  (throw (ex-info (str "No render-layer! implementation for type: " (:type layer))
                  {:layer layer})))


;; ── 组渲染 ──────────────────────────────────────

(defn render
  "渲染图层列表到目标画布，并应用回调 f 进行最终处理。

  设计目标：
  1. 纯函数式 + COW (Copy-on-Write)：所有画布操作通过交换引用实现，避免数据拷贝。
  2. 所有权转移：画布在传递过程中明确转移所有权，通过 .clear 释放不再使用的画布。
  3. 透明后端：内部可使用任何 Canvas 实现（CPU/GPU），外部通过 f 接收结果，无需知道内部类型。
  4. 批次处理：相同后端 (backend) 的图层合并渲染，提高效率。
  5. 递归组处理：图层组递归渲染，子组先合成到临时画布，再合并到父画布。

  参数：
  - f      : 回调函数，接收最终渲染完成的 Canvas，可用于上传、显示或导出。
  - layers : 待渲染的图层列表（扁平或嵌套）。
  - viewport-w/viewport-h    : 画布宽度/高度（像素）。
  - tile-size : 瓦片大小（用于 TiledCanvas 等）。
  - opts   : 选项 map，至少包含 :backend 映射（图层后端标识）和 :viewport 等。

  使用方法：
  1. 调用 (render (fn [canvas] ...) layers viewport-w viewport-h tile-size opts)
  2. 在 f 中处理 canvas（如上传到 JavaFX、保存文件），完成后 canvas 会被自动清理。
  3. 内部递归调用自身，确保所有图层组被正确处理。

  注意：此函数假定输入参数（layers, opts）是不可变的，且画布对象通过 COW 共享。
  "
  [f layers viewport-w viewport-h tile-size opts]
  ;; 初始目标画布（透明）
  (let [canvas-atom (atom (TiledCanvas. tile-size))
        default-backend (:backend opts :default)
        batch (atom [])           ; 当前批次中的图层
        cur-be (atom nil)         ; 当前批次的后端类型
        ;; 渲染当前批次，返回新画布并释放旧画布
        render-batch*
        (fn [c]
          (let [c' (render-batch @cur-be c viewport-w viewport-h @batch opts)] ; 实际渲染批次
            (when c (.clear c))   ; 释放旧画布所有权
            c'))
        ]
    ;; 遍历图层列表
    (doseq [l layers]
      (if (= :group (:type l))
        ;; 遇到图层组：先刷新当前批次，再递归处理组
        (do
          (when (seq @batch)
            (swap! canvas-atom render-batch*) ; 提交批次
            (reset! batch [])
            (reset! cur-be nil))
          ;; 递归渲染组，子组结果合并到当前画布
          (render
            (fn [child-canvas]
              ;; 将子组渲染结果合并到目标画布
              (let [merged (merged/make-merged-layer l child-canvas)
                    new-canvas (*merge-layer* @canvas-atom merged viewport-w viewport-h opts)]
                (swap! canvas-atom
                       (fn [c]
                         (when c (.clear c))
                         new-canvas))))
            (:layers l) viewport-w viewport-h tile-size opts))              ; 递归
        ;; 普通图层：按后端分组批次
        (let [be (:backend l default-backend)]
          (if (= @cur-be be)
            (swap! batch conj l)          ; 追加到当前批次
            (do
              (when (seq @batch)
                (swap! canvas-atom render-batch*)) ; 刷新旧批次
              (reset! batch [l])                   ; 开启新批次
              (reset! cur-be be))))))
    ;; 处理最后剩余批次
    (when (seq @batch)
      (swap! canvas-atom render-batch*))
    ;; 调用回调，传递最终画布
    (let [canvas @canvas-atom]
      (f canvas)
      (.clear canvas))))     ; 清理最终画布（回调应已使用完毕）

