(ns top.kzre.krro.canvas.core.layer.merged)

(defn make-merged-layer
  "创建 merged 描述层。
   ([data]) 纯数据；([layer data]) 基于已有图层继承属性。"
  ([data]
   {:type :merged
    :data data
    :visible? true
    :id nil
    :name nil
    :opacity 1.0
    :blend-mode nil})
  ([layer data]
   (assoc layer :type :merged
                :data data)))



