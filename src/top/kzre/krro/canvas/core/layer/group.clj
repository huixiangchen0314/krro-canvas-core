(ns top.kzre.krro.canvas.core.layer.group
  "图层组操作：创建、修改子图层，封装内部向量。"
  (:require [top.kzre.krro.canvas.core.layer.spec :as spec]))

(defn make-layer-group
  "创建一个图层组。ID 默认为 UUID 字符串。"
  [& {:keys [id name opacity blend-mode visible? locked? layers]
      :or   {id          (keyword (str "group-" (java.util.UUID/randomUUID)))
             name       "Group"
             opacity    1.0
             blend-mode :pass-through
             visible?   true
             locked?    false
             layers     []}}]
  {:id         id
   :type       :group
   :name       name
   :opacity    opacity
   :blend-mode blend-mode
   :visible?   visible?
   :locked?    locked?
   :layers     layers})

(defn add-layer
  "在图层组末尾添加一个子图层，返回新图层组。"
  [group layer]
  (update group :layers conj layer))

(defn add-layer-at
  "在指定索引处插入子图层，返回新图层组。"
  [group index layer]
  (update group :layers #(vec (concat (take index %) [layer] (drop index %)))))

(defn remove-layer
  "根据子图层 ID 移除一个子图层，返回新图层组。若未找到，返回原图层组。"
  [group layer-id]
  (update group :layers (fn [ls] (vec (remove #(= (:id %) layer-id) ls)))))

(defn get-layers
  "返回子图层列表（只读）。"
  [group]
  (:layers group))

(defn update-layer
  "更新指定 ID 的子图层。f 为 (fn [layer] -> new-layer)。若未找到，返回原图层组。"
  [group layer-id f]
  (update group :layers
          (fn [ls]
            (mapv (fn [l] (if (= (:id l) layer-id) (f l) l)) ls))))

(defn layer-count
  "返回子图层数量。"
  [group]
  (count (:layers group)))