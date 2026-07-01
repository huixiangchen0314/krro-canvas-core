(ns top.kzre.krro.canvas.canvas.core-test
  "画布核心统一接口测试。"
  (:require [clojure.test :refer :all]
            [top.kzre.krro.canvas.canvas.test-utils :refer [approx?]]
            [top.kzre.krro.canvas.core.canvas.core :as canvas]
            [top.kzre.krro.canvas.core.canvas.spec :as spec]
            [clojure.spec.alpha :as s]))

(deftest make-canvas-test
  (testing "create raster canvas with defaults"
    (let [c (canvas/make-canvas :raster :width 10 :height 10 :bits-per-channel 8)]
      (is (= :raster (:type c)))
      (is (= 10 (canvas/canvas-width c)))
      (is (= 10 (canvas/canvas-height c)))
      (is (= 8 (:bits-per-channel c)))
      (is (= 4 (:channels c)))
      (is (s/valid? ::spec/raster-canvas c))))

  (testing "create run-length canvas with color"
    (let [c (canvas/make-canvas :run-length :width 5 :height 5 :bits-per-channel 16
                                :channels 3 :color [1.0 0.5 0.0])]
      (is (= :run-length (:type c)))
      (is (s/valid? ::spec/run-length-canvas c))))

  (testing "unsupported type throws"
    (is (thrown? IllegalArgumentException
                 (canvas/make-canvas :unknown :width 1 :height 1 :bits-per-channel 8)))))

(deftest get-set-pixel-test
  (testing "raster canvas get/set"
    (let [c (canvas/make-canvas :raster :width 4 :height 4 :bits-per-channel 8)]
      (canvas/set-pixel! c 0 0 [1.0 0.5 0.25 1.0])
      (let [px (canvas/get-pixel c 0 0)]
        (is (approx? 1.0 (first px) 0.01))
        (is (approx? 0.5 (second px) 0.01))
        (is (approx? 0.25 (nth px 2) 0.01))
        (is (approx? 1.0 (nth px 3) 0.01)))))

  (testing "run-length canvas get/set"
    (let [c (canvas/make-canvas :run-length :width 4 :height 4 :bits-per-channel 8)
          c' (canvas/set-pixel! c 1 2 [0.2 0.4 0.6 0.8])]
      (is (not (identical? c c')))
      (let [px (canvas/get-pixel c' 1 2)]
        (is (approx? 0.2 (first px) 0.01))
        (is (approx? 0.4 (second px) 0.01))
        (is (approx? 0.6 (nth px 2) 0.01))
        (is (approx? 0.8 (nth px 3) 0.01))))))

(deftest raw-pixels-test
  (let [c (canvas/make-canvas :raster :width 2 :height 2 :bits-per-channel 8)]
    (is (some? (canvas/raw-pixels c)))
    (is (instance? (Class/forName "[B") (canvas/raw-pixels c))))
  (let [c (canvas/make-canvas :run-length :width 2 :height 2 :bits-per-channel 8)]
    (is (nil? (canvas/raw-pixels c)))))