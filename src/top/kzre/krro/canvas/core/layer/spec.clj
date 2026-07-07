(ns top.kzre.krro.canvas.core.layer.spec
  "图层数据规格 —— 公共属性集中定义，特有属性由 multi-spec 分派。
   移除 :merged 类型（它只是内部合并描述，不是图层）。"
  (:require [clojure.spec.alpha :as s]))

(s/def ::float-array
  #(instance? (Class/forName "[F") %))

;; ── 公共属性 ─────────────────────────────────
(s/def ::id keyword?)
(s/def ::name string?)
(s/def ::type keyword?)
(s/def ::opacity (s/and number? #(<= 0.0 % 1.0)))
(s/def ::blend-mode keyword?)
(s/def ::visible? boolean?)
(s/def ::backend keyword?)

;; 变换分解属性
(s/def ::x number?)                               ;; X 轴平移
(s/def ::y number?)                               ;; Y 轴平移
(s/def ::scale-x (s/and number? #(> % 0.0)))      ;; X 轴缩放（>0）
(s/def ::scale-y (s/and number? #(> % 0.0)))      ;; Y 轴缩放（>0）
(s/def ::rotation number?)                        ;; 旋转角度（弧度）

;; ── 蒙板属性 ─────────────────────────────────
(s/def ::mask
  (s/or :data  ::float-array   ;; 直接灰度像素蒙板（尺寸应与图层一致）
        :layer ::layer))         ;; 引用另一个图层作为蒙板（剪贴蒙板/Alpha继承）

(s/def ::layer-common
  (s/keys :req-un [::id ::type ::name ::opacity ::blend-mode ::visible?]
          :opt-un [::backend
                   ::x ::y
                   ::scale-x ::scale-y
                   ::rotation
                   ::mask]))     ;; 蒙板为可选公共属性

;; ── 多方法分派（特有属性） ──────────────────────
(defmulti layer-spec :type)

;; ── 图层组 ─────────────────────────────────────
(s/def ::layers (s/coll-of ::layer :kind vector?))
(defmethod layer-spec :group [_]
  (s/keys :req-un [::layers]))

(s/def ::cache-data ::float-array)
;; ── 缓存图层 ─────────────────────────────────
(defmethod layer-spec :cached [_]
  (s/keys :req-un [::cache-data]))

;; ── 完整图层 ───────────────────────────────────
(s/def ::layer
  (s/merge ::layer-common
           (s/multi-spec layer-spec :type)))