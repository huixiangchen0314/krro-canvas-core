(ns top.kzre.krro.canvas.canvas.test-utils
  "测试辅助函数，例如浮点数近似比较。"
  (:require [clojure.test :refer :all]))

(defn approx?
  "检查两个双精度浮点数是否在给定的 epsilon 范围内相等。"
  ([expected actual]
   (approx? expected actual 0.0001))
  ([expected actual epsilon]
   (<= (Math/abs (- expected actual)) epsilon)))