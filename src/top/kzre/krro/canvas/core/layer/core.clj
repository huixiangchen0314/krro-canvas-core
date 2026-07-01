(ns top.kzre.krro.canvas.core.layer.core
  "图层操作：渲染多方法声明，以及通用的图层组构造函数（纯数据）。"
  (:require
   [top.kzre.krro.canvas.core.layer.group :as group]
   [top.kzre.krro.canvas.core.layer.spec]))

;; ── 渲染多方法 ─────────────────────────────────
(defmulti render-layer!
          "将图层数据 layer 渲染到 target-canvas 上（target-canvas 始终为 sRGB）。
           可选参数：
             :dx, :dy  - 图层在目标画布上的偏移，默认 0
           根据图层 :type 分派，默认抛出异常。"
          (fn [layer target-canvas & {:keys [dx dy]}] (:type layer)))

(defmethod render-layer! :default
  [layer & _]
  (throw (ex-info (str "No render-layer! implementation for type: " (:type layer))
                  {:layer layer})))


(def make-layer-group group/make-layer-group)
(def add-layer-to-group group/add-layer)
(def add-layer-to-group-at group/add-layer-at)
(def remove-layer-from-group group/remove-layer)
(def get-layers-from-group group/get-layers)
(def update-layer-in-group group/update-layer)
(def layer-group-count group/layer-count)