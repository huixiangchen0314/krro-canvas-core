(ns top.kzre.krro.canvas.core.layer.cache
  "图层缓存模块：将标记 :cached? 的图层光栅化为 :cached 图层。"
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.merged :as merged]
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.canvas.core.layer.render :as render]))

(defn invalidate-cache!
  "清除图层的缓存数据，使下次 prepare-cache 重新渲染。"
  [layer]
  (dissoc layer :cache-data :cached?))

(defn- render-to-temp
  "渲染一个图层（可以是组）到临时浮点缓冲区。"
  [layer w h]
  (if (group/group? layer)
    (let [stack (render/expand-layers [layer] w h)
          temp (util/allocate-data w h)]
      (render/render-children! stack temp w h)
      temp)
    (let [temp (util/allocate-data w h)]
      (render/render-layer! layer temp w h)
      temp)))

(defn prepare-cache
  "递归遍历图层树，将 :cached? true 的图层替换为 :cached 图层（含 :cache-data）。
   返回新的图层树，可直接送入 render-layers!。"
  [layer w h]
  (if (:cached? layer)
    ;; 需要缓存：光栅化并封装为 :cached 图层
    (let [cache-data (render-to-temp layer w h)]
      (-> layer
          (assoc :type :cached
                 :cache-data cache-data)
          (dissoc :layers :cached?)))
    ;; 不需要缓存
    (if (group/group? layer)
      (assoc layer :layers (mapv #(prepare-cache % w h) (:layers layer)))
      layer)))


(defmethod render/render-layer! :cached
  [layer data w h]
  (let [src-merged (merged/make-merged-layer layer (:cache-data layer))]
    (render/*merge-layer!* data w h src-merged)))