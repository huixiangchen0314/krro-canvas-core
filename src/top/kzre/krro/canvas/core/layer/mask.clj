(ns top.kzre.krro.canvas.core.layer.mask
  "蒙板引用解析。依赖 render 模块进行蒙板图层的渲染。"
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.canvas.core.layer.render :as render]))

(defn- render-and-extract-alpha
  "渲染单个图层（可组）并提取其 alpha 蒙板。使用 cache 避免重复渲染。"
  [layer w h cache]
  (if-let [id (:id layer)]
    (if-let [cached (@cache id)]
      cached
      (let [temp (util/allocate-data w h)
            stack (render/expand-layers [layer] w h)]
        (render/render-children! stack temp w h)
        (let [alpha (util/extract-alpha temp w h)]
          (swap! cache assoc id alpha)
          alpha)))
    (let [temp (util/allocate-data w h)
          stack (render/expand-layers [layer] w h)]
      (render/render-children! stack temp w h)
      (util/extract-alpha temp w h))))

(declare prepare-masks)

(defn- prepare-mask
  "递归处理单个图层蒙板引用，visited 为已访问 id 的集合，防止循环。"
  [layer w h cache visited]
  (if-let [mask (:mask layer)]
    (if (and (vector? mask) (= :layer (first mask)))
      (let [ref-layer (second mask)
            ref-id (:id ref-layer)]
        (if (contains? visited ref-id)
          (throw (ex-info "Circular mask reference detected" {:layer-id (:id layer) :ref-id ref-id}))
          (let [new-visited (conj visited ref-id)
                resolved-ref (prepare-mask ref-layer w h cache new-visited)
                alpha (render-and-extract-alpha resolved-ref w h cache)]
            (assoc layer :mask [:data alpha]))))
      layer) ; 已经是像素蒙板
    layer))

(defn prepare-masks
  "遍历图层列表，解析所有蒙板引用。可传递初始 visited 集合。"
  ([layers w h cache]
   (prepare-masks layers w h cache #{}))
  ([layers w h cache visited]
   (mapv (fn [layer]
           (let [processed (prepare-mask layer w h cache visited)]
             (if (group/group? processed)
               (assoc processed :layers
                                (prepare-masks (:layers processed) w h cache visited))
               processed)))
         layers)))