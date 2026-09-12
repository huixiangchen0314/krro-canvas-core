(ns top.kzre.krro.canvas.core.layer.render.merged
  (:import (java.util UUID))
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]))

(defonce ^:private default-layer-attrs*
         {:opacity 1.0
          :visible true
          :backend :default
          :blend-mode :normal
          :transform util/identity-matrix})

(defn default-layer-attrs [] default-layer-attrs*)


(defn make-merged-layer
  "创建 merged 描述层。
   ([canvas]) 纯数据；([layer canvas]) 基于已有图层继承属性。"
  ([canvas]
   (merge (default-layer-attrs)
          {:id (keyword (str (UUID/randomUUID)))
           :type :merged
           :data canvas
           :canvas canvas
           :transform util/identity-matrix}))
  ([layer canvas]
   (if layer
     (assoc layer :type :merged
                  :data canvas
                  :canvas canvas)
     (make-merged-layer canvas))))
