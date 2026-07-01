(ns top.kzre.krro.canvas.canvas.util-test
  (:require [clojure.test :refer :all]
            [top.kzre.krro.canvas.core.canvas.util :as util]
            [top.kzre.krro.canvas.canvas.test-utils :refer [approx?]]))

(deftest native->float-test
  (testing "8-bit conversion"
    (is (= 0.0 (util/native->float (util/int->byte 0) 8)))
    (is (= 1.0 (util/native->float (util/int->byte 255) 8)))
    (is (approx? 0.5 (util/native->float (util/int->byte 128) 8) 0.01)))
  (testing "16-bit conversion"
    (is (= 0.0 (util/native->float (util/int->short 0) 16)))
    (is (= 1.0 (util/native->float (util/int->short 65535) 16)))
    (is (approx? 0.5 (util/native->float (util/int->short 32768) 16) 0.01)))
  (testing "32-bit float conversion"
    (is (= 0.0 (util/native->float 0.0 32)))
    (is (= 1.0 (util/native->float 1.0 32)))
    (is (= 0.5 (util/native->float 0.5 32)))))

(deftest float->native-test
  (testing "8-bit conversion returns int 0-255"
    (is (= 0   (util/float->native 0.0 8)))
    (is (= 255 (util/float->native 1.0 8)))
    (is (= 128 (util/float->native 0.5 8))))   ;; 127.5 → 128
  (testing "16-bit conversion returns int 0-65535"
    (is (= 0     (util/float->native 0.0 16)))
    (is (= 65535 (util/float->native 1.0 16)))
    (is (= 32768 (util/float->native 0.5 16))))  ;; 32767.5 → 32768
  (testing "32-bit float conversion returns float"
    (is (= 0.0 (util/float->native 0.0 32)))
    (is (= 1.0 (util/float->native 1.0 32)))
    (is (= 0.5 (util/float->native 0.5 32)))))

(deftest int->byte-test
  (is (= (byte 0)   (util/int->byte 0)))
  (is (= (byte -1)  (util/int->byte 255)))
  (is (= (byte 127) (util/int->byte 127)))
  (is (= (byte -128)(util/int->byte 128))))

(deftest int->short-test
  (is (= (short 0)     (util/int->short 0)))
  (is (= (short -1)    (util/int->short 65535)))
  (is (= (short 32767) (util/int->short 32767)))
  (is (= (short -32768)(util/int->short 32768))))

(deftest roundtrip-test
  (testing "8-bit roundtrip"
    (let [values [0.0 0.25 0.5 0.75 1.0]]
      (doseq [v values]
        (let [native (util/float->native v 8)
              back   (util/native->float (util/int->byte native) 8)]
          (is (approx? v back 0.01))))))
  (testing "16-bit roundtrip"
    (let [values [0.0 0.25 0.5 0.75 1.0]]
      (doseq [v values]
        (let [native (util/float->native v 16)
              back   (util/native->float (util/int->short native) 16)]
          (is (approx? v back 0.001))))))
  (testing "32-bit roundtrip"
    (let [values [0.0 0.25 0.5 0.75 1.0]]
      (doseq [v values]
        (let [native (util/float->native v 32)
              back   (util/native->float native 32)]
          (is (approx? v back 0.0001)))))))

(deftest canvas-dimensions-test
  (let [canvas {:type :raster :width 100 :height 200}]
    (is (= 100 (util/canvas-width canvas)))
    (is (= 200 (util/canvas-height canvas)))))