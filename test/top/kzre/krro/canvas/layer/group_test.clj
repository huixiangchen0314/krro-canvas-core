(ns top.kzre.krro.canvas.layer.group-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [top.kzre.krro.canvas.core.layer.spec :as layer-spec]
            [top.kzre.krro.canvas.core.layer.group :as group]))

;; 注册 :mock 图层类型的 spec，以便测试通过验证
(s/def ::mock-props (s/keys))  ;; 仅要求是一个 map
(defmethod layer-spec/layer-spec :mock [_] ::mock-props)

;; 辅助：创建一个简单的“模拟”图层，仅包含 id 和 type
(defn- mock-layer [id]
  {:id id :type :mock :name "mock" :opacity 1.0 :blend-mode :normal
   :visible? true :locked? false})

(deftest make-layer-group-test
  (testing "默认属性"
    (let [g (group/make-layer-group)]
      (is (keyword? (:id g)))
      (is (= :group (:type g)))
      (is (= "Group" (:name g)))
      (is (= 1.0 (:opacity g)))
      (is (= :pass-through (:blend-mode g)))
      (is (true? (:visible? g)))
      (is (false? (:locked? g)))
      (is (vector? (:layers g)))
      (is (empty? (:layers g)))
      (is (s/valid? ::layer-spec/layer g))))

  (testing "自定义属性"
    (let [g (group/make-layer-group :id :my-group
                                    :name "My"
                                    :opacity 0.8
                                    :blend-mode :multiply
                                    :visible? false
                                    :locked? true
                                    :layers [(mock-layer :l1) (mock-layer :l2)])]
      (is (= :my-group (:id g)))
      (is (= "My" (:name g)))
      (is (= 0.8 (:opacity g)))
      (is (= :multiply (:blend-mode g)))
      (is (false? (:visible? g)))
      (is (true? (:locked? g)))
      (is (= 2 (count (:layers g))))
      (is (s/valid? ::layer-spec/layer g)))))

(deftest add-layer-test
  (let [g (group/make-layer-group)
        g1 (group/add-layer g (mock-layer :l1))
        g2 (group/add-layer g1 (mock-layer :l2))]
    (is (= 1 (count (group/get-layers g1))))
    (is (= :l1 (:id (first (group/get-layers g1)))))
    (is (= 2 (count (group/get-layers g2))))
    (is (= :l2 (:id (second (group/get-layers g2)))))))

(deftest add-layer-at-test
  (let [g (-> (group/make-layer-group)
              (group/add-layer (mock-layer :l1))
              (group/add-layer (mock-layer :l2)))
        ;; 在索引 1 插入
        g2 (group/add-layer-at g 1 (mock-layer :l3))]
    (is (= :l1 (:id (first (group/get-layers g2)))))
    (is (= :l3 (:id (second (group/get-layers g2)))))
    (is (= :l2 (:id (nth (group/get-layers g2) 2))))))

(deftest remove-layer-test
  (let [g (-> (group/make-layer-group)
              (group/add-layer (mock-layer :l1))
              (group/add-layer (mock-layer :l2)))
        g2 (group/remove-layer g :l1)]
    (is (= 1 (count (group/get-layers g2))))
    (is (= :l2 (:id (first (group/get-layers g2)))))
    ;; 移除不存在的 id 应返回原图层组
    (let [g3 (group/remove-layer g2 :non-existent)]
      (is (= (group/get-layers g3) (group/get-layers g2))))))

(deftest update-layer-test
  (let [g (-> (group/make-layer-group)
              (group/add-layer (mock-layer :l1)))
        g2 (group/update-layer g :l1 #(assoc % :name "updated"))]
    (is (= "updated" (:name (first (group/get-layers g2))))))
  ;; 更新不存在的 id 不影响任何东西
  (let [g (group/make-layer-group)
        g2 (group/update-layer g :nonexistent #(assoc % :name "nope"))]
    (is (= (group/get-layers g) (group/get-layers g2)))))

(deftest layer-count-test
  (let [g (group/make-layer-group)]
    (is (zero? (group/layer-count g)))
    (let [g1 (group/add-layer g (mock-layer :l1))]
      (is (= 1 (group/layer-count g1))))))

(deftest immutability-test

  (let [g (group/make-layer-group :layers [(mock-layer :a)])
        g2 (group/add-layer g (mock-layer :b))]
    (is (not= (:layers g) (:layers g2)))
    (is (= 1 (count (:layers g))))))

(deftest spec-validation-test

  (let [g (-> (group/make-layer-group)
              (group/add-layer (mock-layer :l1))
              (group/add-layer (mock-layer :l2))
              (group/update-layer :l1 #(assoc % :opacity 0.5)))]
    (is (s/valid? ::layer-spec/layer g))))