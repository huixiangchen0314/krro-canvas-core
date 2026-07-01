(ns top.kzre.krro.canvas.canvas.convert-test
  "画布转换函数测试。"
  (:require [clojure.test :refer :all]
            [top.kzre.krro.canvas.canvas.test-utils :refer [approx?]]
            [top.kzre.krro.canvas.core.canvas.convert :as convert]
            [top.kzre.krro.canvas.core.canvas.raster :as raster]
            [top.kzre.krro.canvas.core.canvas.runs :as runs]
            [top.kzre.krro.canvas.core.canvas.util :as util]))

;; 测试辅助：创建一个4x4 8位RGBA画布，像素(0,0)为红色，其余为透明黑
(defn- sample-raster []
  (let [c (raster/make-raster-canvas 4 4 8 4)
        _ (raster/set-pixel-raster! c 0 0 [1.0 0.0 0.0 1.0])]
    c))

;; 检查像素近似相等
(defn- pixel-approx? [expected actual]
  (and (approx? (first expected) (first actual) 0.01)
       (approx? (second expected) (second actual) 0.01)
       (approx? (nth expected 2) (nth actual 2) 0.01)
       (approx? (nth expected 3) (nth actual 3) 0.01)))

(deftest convert-canvas-type-test
  (testing "raster -> run-length"
    (let [r (sample-raster)
          rl (convert/convert-canvas-type r :run-length)]
      (is (= :run-length (:type rl)))
      (is (some? (:runs rl)))
      (is (nil? (:pixels rl)))
      ;; 像素内容应保持一致
      (is (pixel-approx? [1.0 0.0 0.0 1.0] (runs/get-pixel-run-length rl 0 0)))
      (is (pixel-approx? [0.0 0.0 0.0 0.0] (runs/get-pixel-run-length rl 1 1)))))

  (testing "run-length -> raster"
    (let [rl (-> (sample-raster) (convert/convert-canvas-type :run-length))
          r (convert/convert-canvas-type rl :raster)]
      (is (= :raster (:type r)))
      (is (some? (:pixels r)))
      (is (nil? (:runs r)))
      (is (pixel-approx? [1.0 0.0 0.0 1.0] (raster/get-pixel-raster r 0 0)))
      (is (pixel-approx? [0.0 0.0 0.0 0.0] (raster/get-pixel-raster r 1 1)))))

  (testing "same type returns original"
    (let [r (sample-raster)]
      (is (identical? r (convert/convert-canvas-type r :raster))))
    (let [rl (-> (sample-raster) (convert/convert-canvas-type :run-length))]
      (is (identical? rl (convert/convert-canvas-type rl :run-length)))))

  (testing "unsupported conversion throws"
    (is (thrown? IllegalArgumentException
                 (convert/convert-canvas-type (sample-raster) :unknown)))))

(deftest convert-precision-test
  (testing "raster 8-bit -> 16-bit"
    (let [r (sample-raster)
          r16 (convert/convert-precision r 16)]
      (is (= 16 (:bits-per-channel r16)))
      (is (instance? (Class/forName "[S") (:pixels r16)))
      (is (pixel-approx? [1.0 0.0 0.0 1.0] (raster/get-pixel-raster r16 0 0)))
      (is (pixel-approx? [0.0 0.0 0.0 0.0] (raster/get-pixel-raster r16 1 1)))))

  (testing "raster 8-bit -> 32-bit"
    (let [r (sample-raster)
          r32 (convert/convert-precision r 32)]
      (is (= 32 (:bits-per-channel r32)))
      (is (instance? (Class/forName "[F") (:pixels r32)))
      (is (pixel-approx? [1.0 0.0 0.0 1.0] (raster/get-pixel-raster r32 0 0)))))

  (testing "run-length precision conversion"
    (let [rl (-> (sample-raster) (convert/convert-canvas-type :run-length))
          rl16 (convert/convert-precision rl 16)]
      (is (= :run-length (:type rl16)))
      (is (= 16 (:bits-per-channel rl16)))
      (is (instance? (Class/forName "[S") (:runs rl16)))
      ;; 像素内容通过解码验证
      (is (pixel-approx? [1.0 0.0 0.0 1.0] (runs/get-pixel-run-length rl16 0 0)))))

  (testing "same bits returns original"
    (let [r (sample-raster)]
      (is (identical? r (convert/convert-precision r 8))))))

(deftest convert-channels-test
  (testing "reduce channels: 4 -> 3"
    (let [r (sample-raster)
          r3 (convert/convert-channels r 3)]
      (is (= 3 (:channels r3)))
      (is (= (* 4 4 3) (alength (:pixels r3))))
      ;; 读出的像素应只有前3个通道
      (let [px (raster/get-pixel-raster r3 0 0)]
        (is (approx? 1.0 (first px) 0.01))
        (is (approx? 0.0 (second px) 0.01))
        (is (approx? 0.0 (nth px 2) 0.01)))))

  (testing "increase channels: 4 -> 5 with padding"
    (let [r (sample-raster)
          r5 (convert/convert-channels r 5 :pad-val 0.5)]
      (is (= 5 (:channels r5)))
      (let [px (raster/get-pixel-raster r5 0 0)]
        (is (approx? 1.0 (first px) 0.01))
        (is (approx? 0.0 (second px) 0.01))
        (is (approx? 0.0 (nth px 2) 0.01))
        (is (approx? 1.0 (nth px 3) 0.01))   ;; 原alpha
        (is (approx? 0.5 (nth px 4) 0.01))))  ;; 新通道被填充为0.5

    (testing "run-length channel conversion"
      (let [rl (-> (sample-raster) (convert/convert-canvas-type :run-length))
            rl3 (convert/convert-channels rl 3)]
        (is (= :run-length (:type rl3)))
        (is (= 3 (:channels rl3)))
        (let [px (runs/get-pixel-run-length rl3 0 0)]
          (is (approx? 1.0 (first px) 0.01))
          (is (approx? 0.0 (second px) 0.01))
          (is (approx? 0.0 (nth px 2) 0.01)))))

    (testing "same channels returns original"
      (let [r (sample-raster)]
        (is (identical? r (convert/convert-channels r 4)))))))