(ns top.kzre.krro.canvas.core.rect
  "脏矩形工具函数 —— 所有矩形均为 [x y width height] 向量。")

(defn rect-x [[x _ _ _]] x)
(defn rect-y [[_ y _ _]] y)
(defn rect-w [[_ _ w _]] w)
(defn rect-h [[_ _ _ h]] h)

(defn make-rect
  "创建矩形向量，默认 [0 0 0 0]。"
  ([]
   [0 0 0 0])
  ([x y w h]
   [x y w h]))

(defn area [[_ _ w h]]
  (* w h))

(defn perimeter [[_ _ w h]]
  (* 2 (+ w h)))

(defn rect-contains-point?
  "判断矩形是否包含点 (px, py)，边界包含在内。"
  [[x y w h] px py]
  (and (>= px x) (< px (+ x w))
       (>= py y) (< py (+ y h))))

(defn rect-intersects?
  "判断两个矩形是否相交（至少共享一个点）。"
  [[x1 y1 w1 h1] [x2 y2 w2 h2]]
  (and (< x1 (+ x2 w2))
       (> (+ x1 w1) x2)
       (< y1 (+ y2 h2))
       (> (+ y1 h1) y2)))

(defn rect-intersect
  "返回两个矩形的交集，若不相交返回 nil。"
  [[x1 y1 w1 h1] [x2 y2 w2 h2]]
  (let [ix (max x1 x2)
        iy (max y1 y2)
        iw (- (min (+ x1 w1) (+ x2 w2)) ix)
        ih (- (min (+ y1 h1) (+ y2 h2)) iy)]
    (when (and (pos? iw) (pos? ih))
      [ix iy iw ih])))

(defn rect-union
  "返回包围两个矩形的最小矩形。"
  [[x1 y1 w1 h1] [x2 y2 w2 h2]]
  (if (and (zero? w1) (zero? h1))
    [x2 y2 w2 h2]
    (if (and (zero? w2) (zero? h2))
      [x1 y1 w1 h1]
      (let [ux (min x1 x2)
            uy (min y1 y2)
            uw (- (max (+ x1 w1) (+ x2 w2)) ux)
            uh (- (max (+ y1 h1) (+ y2 h2)) uy)]
        [ux uy uw uh]))))

(defn merge-rects
  "合并一系列矩形为最小包围盒。"
  [rects]
  (if (empty? rects)
    []
    (reduce rect-union [0 0 0 0] rects)))

(defn clip-rect
  "将矩形裁剪到画布边界内，若完全在画布外返回 nil。"
  [[x y w h] canvas-w canvas-h]
  (let [cx (max 0 x)
        cy (max 0 y)
        cw (- (min canvas-w (+ x w)) cx)
        ch (- (min canvas-h (+ y h)) cy)]
    (when (and (pos? cw) (pos? ch))
      [cx cy cw ch])))