(ns top.kzre.krro.canvas.canvas.raster-test
  "光栅画布创建与像素读写测试。"
  (:require [clojure.test :refer :all]
            [top.kzre.krro.canvas.canvas.test-utils :refer [approx?]]
            [top.kzre.krro.canvas.core.canvas.raster :as raster]))

(deftest make-raster-canvas-test
  (testing "默认创建透明黑画布"
    (let [c (raster/make-raster-canvas 10 10 8 4)]
      (is (= :raster (:type c)))
      (is (= 10 (:width c)))
      (is (= 10 (:height c)))
      (is (= 8 (:bits-per-channel c)))
      (is (= 4 (:channels c)))
      (is (instance? (Class/forName "[B") (:pixels c)))  ;; 8位应该用byte数组
      (is (= (* 10 10 4) (alength (:pixels c))))))

  (testing "16位画布"
    (let [c (raster/make-raster-canvas 5 5 16 4)]
      (is (= 16 (:bits-per-channel c)))
      (is (instance? (Class/forName "[S") (:pixels c)))))

  (testing "32位浮点画布"
    (let [c (raster/make-raster-canvas 3 3 32 3)]  ;; RGB通道
      (is (= 32 (:bits-per-channel c)))
      (is (= 3 (:channels c)))
      (is (instance? (Class/forName "[F") (:pixels c)))))

  (testing "创建时可指定初始颜色"
    (let [color [1.0 0.0 0.0 1.0]  ;; 不透明红色
          c (raster/make-raster-canvas 2 2 8 4 :color color)]
      (is (approx? 1.0 (first (raster/get-pixel-raster c 0 0)) 0.01))
      (is (approx? 0.0 (second (raster/get-pixel-raster c 0 0)) 0.01))
      (is (approx? 0.0 (nth (raster/get-pixel-raster c 0 0) 2) 0.01))
      (is (approx? 1.0 (nth (raster/get-pixel-raster c 0 0) 3) 0.01)))))

(deftest get-pixel-raster-test
  (let [c (raster/make-raster-canvas 4 4 8 4)]
    ;; 未写入前应为透明黑
    (is (= [0.0 0.0 0.0 0.0] (raster/get-pixel-raster c 0 0)))
    (is (= [0.0 0.0 0.0 0.0] (raster/get-pixel-raster c 3 3)))))

(deftest set-pixel-raster-test
  (let [c (raster/make-raster-canvas 4 4 8 4)
        _ (raster/set-pixel-raster! c 1 2 [1.0 0.5 0.25 1.0])
        pixel (raster/get-pixel-raster c 1 2)]
    (is (approx? 1.0 (first pixel) 0.01))
    (is (approx? 0.5 (second pixel) 0.01))
    (is (approx? 0.25 (nth pixel 2) 0.01))
    (is (approx? 1.0 (nth pixel 3) 0.01))
    ;; 相邻像素不应被改变
    (is (= [0.0 0.0 0.0 0.0] (raster/get-pixel-raster c 0 0)))))

(deftest raw-pixels-test
  (let [c (raster/make-raster-canvas 2 2 8 4)]
    (is (some? (raster/raw-pixels c)))
    (is (instance? (Class/forName "[B") (raster/raw-pixels c)))))

(deftest different-bit-depth-roundtrip
  (testing "8位往返精度"
    (let [c (raster/make-raster-canvas 2 2 8 4)
          color [0.3 0.7 0.1 0.9]]
      (raster/set-pixel-raster! c 0 1 color)
      (let [read (raster/get-pixel-raster c 0 1)]
        (is (approx? 0.3 (first read) 0.01))
        (is (approx? 0.7 (second read) 0.01))
        (is (approx? 0.1 (nth read 2) 0.01))
        (is (approx? 0.9 (nth read 3) 0.01)))))

  (testing "16位往返精度"
    (let [c (raster/make-raster-canvas 2 2 16 4)
          color [0.333 0.667 0.111 0.889]]
      (raster/set-pixel-raster! c 0 1 color)
      (let [read (raster/get-pixel-raster c 0 1)]
        (is (approx? 0.333 (first read) 0.001))
        (is (approx? 0.667 (second read) 0.001))
        (is (approx? 0.111 (nth read 2) 0.001))
        (is (approx? 0.889 (nth read 3) 0.001)))))

  (testing "32位浮点往返"
    (let [c (raster/make-raster-canvas 2 2 32 4)
          color [0.12345 0.6789 0.54321 0.98765]]
      (raster/set-pixel-raster! c 0 1 color)
      (let [read (raster/get-pixel-raster c 0 1)]
        (is (approx? 0.12345 (first read) 0.0001))
        (is (approx? 0.6789 (second read) 0.0001))
        (is (approx? 0.54321 (nth read 2) 0.0001))
        (is (approx? 0.98765 (nth read 3) 0.0001))))))