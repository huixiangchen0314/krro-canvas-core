(ns top.kzre.krro.canvas.core.layer.util
  "图层相关的通用工具，包括变换矩阵构造、组合，图层性质判断，
   以及临时缓冲区分配和 Alpha 提取。")

;; ── 变换矩阵常量 ──────────────────────────────────
(def identity-matrix
  "2D 仿射变换的单位矩阵 [a b c d tx ty]，即 [1 0 0 1 0 0]。"
  [1.0 0.0 0.0 1.0 0.0 0.0])

;; ── 矩阵乘法 ──────────────────────────────────────
(defn multiply-transform
  "右乘两个 2D 仿射变换矩阵：parent * local。
   每个矩阵表示为 6 元素向量 [a b c d tx ty]。"
  [[a1 b1 c1 d1 tx1 ty1] [a2 b2 c2 d2 tx2 ty2]]
  [(+ (* a1 a2) (* c1 b2))
   (+ (* b1 a2) (* d1 b2))
   (+ (* a1 c2) (* c1 d2))
   (+ (* b1 c2) (* d1 d2))
   (+ (* a1 tx2) (* c1 ty2) tx1)
   (+ (* b1 tx2) (* d1 ty2) ty1)])

;; ── 局部变换构造 ──────────────────────────────────
(defn compose-local-transform
  "从图层属性中的 :x :y :scale-x :scale-y :rotation 构造局部仿射矩阵。
   变换顺序：缩放 → 旋转 → 平移。
   返回 [a b c d tx ty]。"
  [{:keys [x y scale-x scale-y rotation]
    :or {x 0.0 y 0.0 scale-x 1.0 scale-y 1.0 rotation 0.0}}]
  (let [cos-r (Math/cos rotation)
        sin-r (Math/sin rotation)]
    (double-array
      [(* scale-x cos-r)
       (* scale-x sin-r)
       (* scale-y (- sin-r))
       (* scale-y cos-r)
       x
       y])))

;; ── 直通组判断 ────────────────────────────────────
(defn pass-through?
  "判断图层是否为直通组（其子图层直接穿透到父级）。
   条件：:blend-mode 为 nil 或 :pass-through。"
  [layer]
  (let [bm (:blend-mode layer)]
    (or (nil? bm) (= :pass-through bm))))

;; ── 缓冲区分配 ────────────────────────────────────
(defn allocate-data
  "分配一个 w*h*4 的浮点数组，用于 RGBA 画布。"
  [w h]
  (float-array (* w h 4)))

;; ── Alpha 提取 ────────────────────────────────────
(defn extract-alpha
  "从 RGBA 浮点数组中提取 alpha 通道，返回单独的 float 数组，长度 w*h。"
  [^floats rgba w h]
  (let [alpha (float-array (* w h))]
    (dotimes [i (* w h)]
      (aset alpha i (aget rgba (+ (* 4 i) 3))))
    alpha))