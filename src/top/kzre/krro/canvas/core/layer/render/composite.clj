(ns top.kzre.krro.canvas.core.layer.render.composite
  "默认的线性合成分发"
  (:import (top.kzre.krro.util.tile TiledCanvas))
  (:require
    [top.kzre.krro.core.util.promise :as promise]))

(defmulti composite-layer
          "合成图层，_canvas 是背景图层，返回 Promise<TiledCanvas>,
          该合成是纯函数， 背景图层的 _canvas 不该改变，不发生所有权转移"
          (fn [layer ^TiledCanvas _canvas _opts]
            (:type layer)))

(defmethod composite-layer :default
  [layer & _]
  (promise/rejected
    (ex-info (str "No composite-layer implementation for type: " (:type layer))
             {:layer-id (:id layer)
              :layer-type (:type layer)
              :layer layer})))