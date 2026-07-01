(ns top.kzre.krro.canvas.canvas.runs-test
  "游程编码画布创建与像素读写测试。"
  (:require [clojure.test :refer :all]
            [top.kzre.krro.canvas.canvas.test-utils :refer [approx?]]
            [top.kzre.krro.canvas.core.canvas.runs :as runs]))

(defn- expected-runs-class [bits]
  (case bits
    8  (Class/forName "[B")    ;; byte-array
    16 (Class/forName "[S")    ;; short-array
    32 (Class/forName "[I")))  ;; int-array (通道值用 int 位模式存储)

(deftest make-run-length-canvas-test
  (testing "默认创建透明黑画布"
    (let [c (runs/make-run-length-canvas 10 10 8 4)]
      (is (= :run-length (:type c)))
      (is (= 10 (:width c)))
      (is (= 10 (:height c)))
      (is (= 8 (:bits-per-channel c)))
      (is (= 4 (:channels c)))
      (is (some? (:runs c)))
      (is (instance? (expected-runs-class 8) (:runs c)))
      (is (pos? (alength (:runs c))))
      ;; 透明黑（全零）应能正确读取
      (is (= [0.0 0.0 0.0 0.0] (runs/get-pixel-run-length c 0 0)))))

  (testing "16位画布"
    (let [c (runs/make-run-length-canvas 5 5 16 4)]
      (is (= 16 (:bits-per-channel c)))
      (is (instance? (expected-runs-class 16) (:runs c)))
      (is (pos? (alength (:runs c))))))

  (testing "32位浮点画布"
    (let [c (runs/make-run-length-canvas 3 3 32 3)]
      (is (= 32 (:bits-per-channel c)))
      (is (= 3 (:channels c)))
      (is (instance? (expected-runs-class 32) (:runs c)))
      (is (pos? (alength (:runs c))))))

  (testing "创建时可指定初始颜色"
    (let [color [1.0 0.0 0.0 1.0]
          c (runs/make-run-length-canvas 2 2 8 4 :color color)]
      (doseq [y [0 1] x [0 1]]
        (let [px (runs/get-pixel-run-length c x y)]
          (is (approx? 1.0 (first px) 0.01))
          (is (approx? 0.0 (second px) 0.01))
          (is (approx? 0.0 (nth px 2) 0.01))
          (is (approx? 1.0 (nth px 3) 0.01)))))))

(deftest get-pixel-run-length-test
  (let [c (runs/make-run-length-canvas 4 4 8 4)]
    (is (= [0.0 0.0 0.0 0.0] (runs/get-pixel-run-length c 0 0)))
    (is (= [0.0 0.0 0.0 0.0] (runs/get-pixel-run-length c 3 3)))))

(deftest set-pixel-run-length-test
  (let [c (runs/make-run-length-canvas 4 4 8 4)
        c' (runs/set-pixel-run-length! c 1 2 [1.0 0.5 0.25 1.0])
        pixel (runs/get-pixel-run-length c' 1 2)]
    (is (approx? 1.0 (first pixel) 0.01))
    (is (approx? 0.5 (second pixel) 0.01))
    (is (approx? 0.25 (nth pixel 2) 0.01))
    (is (approx? 1.0 (nth pixel 3) 0.01))
    (is (= [0.0 0.0 0.0 0.0] (runs/get-pixel-run-length c' 0 0)))))

(deftest multiple-set-pixel-test
  (let [c (runs/make-run-length-canvas 8 8 8 4)
        c' (-> c
               (runs/set-pixel-run-length! 0 0 [1.0 0.0 0.0 1.0])
               (runs/set-pixel-run-length! 1 0 [0.0 1.0 0.0 1.0])
               (runs/set-pixel-run-length! 2 0 [0.0 0.0 1.0 1.0]))]
    (is (approx? 1.0 (first (runs/get-pixel-run-length c' 0 0)) 0.01))
    (is (approx? 0.0 (second (runs/get-pixel-run-length c' 0 0)) 0.01))
    (is (approx? 0.0 (nth (runs/get-pixel-run-length c' 0 0) 2) 0.01))
    (is (approx? 1.0 (nth (runs/get-pixel-run-length c' 0 0) 3) 0.01))

    (is (approx? 0.0 (first (runs/get-pixel-run-length c' 1 0)) 0.01))
    (is (approx? 1.0 (second (runs/get-pixel-run-length c' 1 0)) 0.01))
    (is (approx? 0.0 (nth (runs/get-pixel-run-length c' 1 0) 2) 0.01))
    (is (approx? 1.0 (nth (runs/get-pixel-run-length c' 1 0) 3) 0.01))

    (is (approx? 0.0 (first (runs/get-pixel-run-length c' 2 0)) 0.01))
    (is (approx? 0.0 (second (runs/get-pixel-run-length c' 2 0)) 0.01))
    (is (approx? 1.0 (nth (runs/get-pixel-run-length c' 2 0) 2) 0.01))
    (is (approx? 1.0 (nth (runs/get-pixel-run-length c' 2 0) 3) 0.01))

    (is (= [0.0 0.0 0.0 0.0] (runs/get-pixel-run-length c' 3 0)))
    (is (= [0.0 0.0 0.0 0.0] (runs/get-pixel-run-length c' 0 1)))))