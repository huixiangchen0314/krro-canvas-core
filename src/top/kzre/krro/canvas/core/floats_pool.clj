(ns top.kzre.krro.canvas.core.floats-pool
  "浮点数组对象池，用于画布像素缓冲区的复用。
   内部固定每个尺寸最多缓存 10 个，全局最多 100 个。
   使用零反射的 fill 操作。"
  (:import [top.kzre.krro.canvas.core Arrays]))

(def ^:private max-per-size 10)
(def ^:private max-total 100)

(defonce ^:private pools (atom {}))
(defonce ^:private total-count (atom 0))

(defn borrow
  "从池中获取一个长度恰好为 size 的 float-array。
   若池中无则分配新数组。可选 :fill true 借用时清零。"
  [size & {:keys [fill] :or {fill false}}]
  (let [size (long size)]
    (if-let [arr (first (get @pools size))]
      (do
        (swap! pools update size rest)
        (swap! total-count dec)
        (when fill (Arrays/zero arr))
        arr)
      (float-array size))))

(defn return
  "将数组归还池中。若池满则丢弃。可选 :zero true 归还前清零。"
  [arr & {:keys [zero] :or {zero false}}]
  (let [size (alength arr)]
    (when zero (Arrays/zero arr))
    (swap! pools update size
           (fn [coll]
             (if (< (count coll) max-per-size)
               (conj coll arr)
               coll)))
    (swap! total-count inc)
    (when (> @total-count max-total)
      ;; 淘汰最大尺寸的一个数组
      (when-let [largest (first (apply max-key (fn [[k v]] k) (filter #(seq (val %)) @pools)))]
        (swap! pools update largest rest)
        (swap! total-count dec)))
    nil))

(defn clear!
  "清空所有缓存。"
  []
  (reset! pools {})
  (reset! total-count 0))

(defn stats
  "返回统计信息。"
  []
  {:total @total-count
   :by-size (into {} (map (fn [[k v]] [k (count v)]) @pools))})