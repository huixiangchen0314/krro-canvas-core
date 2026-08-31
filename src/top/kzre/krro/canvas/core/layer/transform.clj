(ns top.kzre.krro.canvas.core.layer.transform
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.util :as util])
  (:import (top.kzre.krro.util.math KMath)))

(defn preprocess
  ([layer opts]
   (letfn [(process-layer [layer parent-transform]
             (let [local-transform (util/compose-local-transform layer)
                   world-transform (KMath/mat2dMul parent-transform local-transform)
                   processed (assoc layer :transform world-transform)]
               (if (group/group? layer)
                 (let [children (:layers layer)]
                   (assoc processed :layers (mapv #(process-layer % world-transform) children)))
                 processed)))]
     (if-let [viewport-transform (:viewport opts)]
       (process-layer layer (KMath/mat2dInv viewport-transform))
       (process-layer layer util/identity-matrix)))))