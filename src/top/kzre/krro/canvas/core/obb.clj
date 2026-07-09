(ns top.kzre.krro.canvas.core.obb
  (:import (java.io InputStream OutputStream)
           [top.kzre.krro.canvas.core Arrays OBB]
           [java.util ArrayList]))

;; 对外暴露的 OBB 记录
(defrecord ObbDescriptor [center-x center-y
                          half-width half-height
                          angle axis-u axis-v])

(defn- obb->descriptor [^OBB obb]
  (->ObbDescriptor (.centerX obb) (.centerY obb)
                   (.halfWidth obb) (.halfHeight obb)
                   (.angle obb)
                   (Arrays/toVec (.axisU obb))
                   (Arrays/toVec (.axisV obb))))

(defn- descriptor->obb [^ObbDescriptor d]
  (OBB. (float (:center-x d))
        (float (:center-y d))
        (float (:half-width d))
        (float (:half-height d))
        (float (:angle d))
        (Arrays/toFloats (:axis-u d))
        (Arrays/toFloats (:axis-v d))))

(defn rects->obb
  "从脏矩形序列计算 OBB，返回 ObbDescriptor。"
  [rects]
  (let [list (ArrayList. (mapv (fn [r] (int-array r)) rects))
        ^OBB obb (OBB/computeOBB list)]
    (obb->descriptor obb)))

(defn obb-contains?
  [^ObbDescriptor obb px py]
  (let [^OBB j-obb (descriptor->obb obb)]
    (.contains j-obb (float px) (float py))))

(defn obb-aabb
  "返回 OBB 的轴对齐包围盒 [x y width height]。"
  [^ObbDescriptor obb]
  (let [^OBB j-obb (descriptor->obb obb)]
    (seq (.getAABB j-obb))))


(defn save-obb-snapshot
  "从源数组 src 中提取 OBB 快照，返回 {:data float-array :width w :height h}。"
  [^floats src canvas-w canvas-h ^ObbDescriptor obb]
  (let [^OBB j-obb (descriptor->obb obb)
        out-size (int-array 2)
        data (OBB/saveSnapshot src (int canvas-w) (int canvas-h) j-obb out-size)]
    {:data data :width (aget out-size 0) :height (aget out-size 1)}))

(defn restore-obb-snapshot
  "将快照恢复到目标数组 dest。"
  [^floats dest canvas-w canvas-h ^ObbDescriptor obb snapshot]
  (let [^OBB j-obb (descriptor->obb obb)
        {:keys [data width height]} snapshot]
    (OBB/restoreSnapshot dest (int canvas-w) (int canvas-h) data (int width) (int height) j-obb)))



(defn write-snapshot!
  "将快照 map 序列化写入 OutputStream。"
  [snapshot ^OutputStream out]
  (let [{:keys [data width height]} snapshot]
    (OBB/writeSnapshot data (int width) (int height) out)))

(defn read-snapshot!
  "从 InputStream 读取快照，返回 {:data float-array :width w :height h}。"
  [^InputStream in]
  (let [result (OBB/readSnapshot in)]
    {:data (.data result) :width (.width result) :height (.height result)}))