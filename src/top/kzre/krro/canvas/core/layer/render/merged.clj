(ns top.kzre.krro.canvas.core.layer.render.merged
  (:import (java.util UUID))
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]))


(defn make-merged-layer
  "创建 merged 描述层。
   ([canvas]) 纯数据；([layer canvas]) 基于已有图层继承属性。"
  ([canvas]
   {:id (keyword (str (UUID/randomUUID)))
    :type :merged
    :data canvas
    :canvas canvas
    :opacity 1
    :visible true
    :backend :default
    :blend-mode :normal
    :transform util/identity-matrix})
  ([layer canvas]
   (assoc layer :type :merged
                :data canvas
                :canvas canvas)))
