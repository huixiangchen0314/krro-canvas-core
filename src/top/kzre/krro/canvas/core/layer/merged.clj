(ns top.kzre.krro.canvas.core.layer.merged
  (:import (top.kzre.krro.util.tile Canvas)))

(defn make-merged-layer
  "创建 merged 描述层。
   ([data]) 纯数据；([layer data]) 基于已有图层继承属性。"
  ([^Canvas canvas]
   {:type :merged
    :data canvas
    :canvas canvas
    :visible true
    :id nil
    :name nil
    :opacity 1.0
    :blend-mode :normal})
  ([layer data]
   (assoc layer :type :merged
                :data data
                :canvas data)))



