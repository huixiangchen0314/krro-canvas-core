(ns top.kzre.krro.canvas.core.layer.pre
  (:require
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.canvas.core.layer.util :as util]))

(defn preprocess
  ([root-layer]
   (preprocess root-layer util/identity-matrix))
  ([layer parent-transform]
   (let [local-transform (util/compose-local-transform layer)
         world-transform (util/multiply-transform parent-transform local-transform)
         processed (assoc layer :transform world-transform)]
     (if (group/group? layer)
       (let [children (:layers layer)
             child-parent (if (util/pass-through? layer)
                            world-transform
                            util/identity-matrix)]
         (assoc processed :layers (mapv #(preprocess % child-parent) children)))
       processed))))