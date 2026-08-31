(ns top.kzre.krro.canvas.core.layer.merged)

(defn make-merged-layer
  "创建 merged 描述层。
   ([canvas]) 纯数据；([layer canvas]) 基于已有图层继承属性。"
  [layer canvas]
  (assoc layer :type :merged
               :data canvas
               :canvas canvas))



