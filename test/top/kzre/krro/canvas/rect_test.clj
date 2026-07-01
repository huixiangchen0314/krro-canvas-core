(ns top.kzre.krro.canvas.rect-test
  "矩形工具函数测试。"
  (:require [clojure.test :refer :all]
            [top.kzre.krro.canvas.core.rect :as rect]))

(deftest make-rect-and-accessors-test
  (is (= [0 0 0 0] (rect/make-rect)))
  (is (= [10 20 30 40] (rect/make-rect 10 20 30 40)))
  (let [r [10 20 30 40]]
    (is (= 10 (rect/rect-x r)))
    (is (= 20 (rect/rect-y r)))
    (is (= 30 (rect/rect-w r)))
    (is (= 40 (rect/rect-h r)))))

(deftest area-perimeter-test
  (let [r [5 5 10 20]]
    (is (= 200 (rect/area r)))
    (is (= 60 (rect/perimeter r)))))

(deftest rect-contains-point?-test
  (let [r [5 5 10 10]]  ;; x 5..15, y 5..15
    (is (rect/rect-contains-point? r 5 5))
    (is (rect/rect-contains-point? r 14 14))
    (is (not (rect/rect-contains-point? r 15 14)))  ;; 右边界不包含
    (is (not (rect/rect-contains-point? r 14 15)))  ;; 下边界不包含
    (is (not (rect/rect-contains-point? r 4 5)))
    (is (not (rect/rect-contains-point? r 5 4)))))

(deftest rect-intersects?-test
  (let [r1 [0 0 10 10]
        r2 [5 5 10 10]
        r3 [10 10 10 10]  ;; 与 r1 仅边界相邻
        r4 [20 20 10 10]]
    (is (rect/rect-intersects? r1 r2))
    (is (not (rect/rect-intersects? r1 r3)))  ;; 相邻不算相交
    (is (not (rect/rect-intersects? r1 r4)))))

(deftest rect-intersect-test
  (let [r1 [0 0 10 10]
        r2 [5 5 10 10]]
    (is (= [5 5 5 5] (rect/rect-intersect r1 r2)))
    (let [r3 [10 10 10 10]]
      (is (nil? (rect/rect-intersect r1 r3))))))

(deftest rect-union-test
  (let [r1 [0 0 5 5]
        r2 [5 5 5 5]]
    (is (= [0 0 10 10] (rect/rect-union r1 r2)))
    (is (= [0 0 5 5] (rect/rect-union [0 0 0 0] r1)))
    (is (= [0 0 5 5] (rect/rect-union r1 [0 0 0 0])))))

(deftest merge-rects-test
  (let [rects [[0 0 5 5] [5 5 5 5] [10 10 5 5]]]
    (is (= [0 0 15 15] (rect/merge-rects rects))))
  (is (= [] (rect/merge-rects []))))

(deftest clip-rect-test
  (let [r [5 5 10 10]]  ;; x 5..15, y 5..15
    ;; 完全在画布内
    (is (= [5 5 10 10] (rect/clip-rect r 20 20)))
    ;; 部分超出右边界
    (is (= [5 5 10 10] (rect/clip-rect r 15 20)))  ;; 画布宽15，右边界15，x+10=15，clip后宽=10
    ;; 完全在画布外（左边界）
    (is (nil? (rect/clip-rect [20 0 10 10] 10 10)))
    ;; 部分超出下边界
    (is (= [5 5 5 5] (rect/clip-rect [5 5 5 5] 10 10)))  ;; 完全在内部
    (is (= [5 5 5 5] (rect/clip-rect [5 5 10 10] 10 10))) ;; 画布10x10，超出部分被裁剪
    ))