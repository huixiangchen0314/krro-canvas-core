(ns top.kzre.krro.canvas.core.layer.spec
  "图层数据规格 —— 公共属性集中定义，特有属性由 multi-spec 分派。
   不包含任何具体图层类型实现，完全开放扩展。"
  (:require [clojure.spec.alpha :as s]))

;; ── 公共属性（所有图层必须包含） ─────────────────
(s/def ::id keyword?)   ;; 恢复为关键字
(s/def ::name string?)
(s/def ::type keyword?)
(s/def ::opacity (s/and number? #(<= 0.0 % 1.0)))
(s/def ::blend-mode keyword?)
(s/def ::visible? boolean?)
(s/def ::locked? boolean?)

(s/def ::layer-common
  (s/keys :req-un [::id ::type ::name ::opacity ::blend-mode ::visible? ::locked?]))

;; ── 多方法分派（仅负责特有属性） ────────────────
(defmulti layer-spec :type)

;; ── 图层组特有属性 ──────────────────────────────
;; 先声明 ::layers 的递归容器，此时 ::layer 尚未定义，但 spec 允许延迟解析
(s/def ::layers
  (s/coll-of ::layer :kind vector?))

;; 为 :group 类型注册特有属性 spec
(defmethod layer-spec :group [_]
  (s/keys :req-un [::layers]))

;; ── 完整图层（公共 + 特有） ─────────────────────
(s/def ::layer
  (s/merge ::layer-common
           (s/multi-spec layer-spec :type)))