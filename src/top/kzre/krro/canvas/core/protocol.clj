(ns top.kzre.krro.canvas.core.protocol
  "Krrō 画布核心协议 —— 零依赖、纯抽象的像素容器与图层体系。
   所有协议均为纯数据接口，不包含任何实现、状态或具体值集合。
   适用于 2D 绘画、3D 纹理绘制、漫画分镜、动画帧等所有需要层叠合成的场景。")

;; ── 像素容器 ──────────────────────────────────────────
(defprotocol ICanvas
  "平台无关的像素画布。颜色分量均为 0.0 ~ 1.0 的双精度浮点数。"
  (width      [this]  "返回整数宽度")
  (height     [this]  "返回整数高度")
  (get-pixel  [this x y]
    "返回 [r g b a]，x、y 为整数坐标")
  (set-pixel! [this x y rgba]
    "设置指定像素，返回 ICanvas（可能为原对象，也可能是新对象）。
     调用者不应依赖可变性，需用返回值继续操作。"))

;; ── 批量访问（可选扩展） ──────────────────────────────
(defprotocol IRawArrayCanvas
  "为高性能关键路径暴露底层 double 数组（行主序 RGBA）。
   消费者应先检查 (satisfies? IRawArrayCanvas canvas)，未实现时回退到 ICanvas 方法。"
  (raw-pixels [this]
    "返回长度为 (* width height 4) 的 double 数组，直接读写。"))

;; ── 图层 ──────────────────────────────────────────────
(defprotocol ILayer
  "独立的视觉内容层，携带透明度、混合模式等合成属性。
   所有 setter 应返回更新后的图层（函数式风格或可变实现均可）。"
  (layer-id         [this]         "全局唯一标识")
  (layer-name       [this]         "用户可读名称")
  (set-layer-name!  [this name]    "返回更新后的图层")
  (opacity          [this]         "不透明度，0.0（完全透明）~ 1.0（完全不透明）")
  (set-opacity!     [this val]     "返回更新后的图层")
  (blend-mode       [this]         "混合模式，具体关键字由颜色系统定义，此处不约束")
  (set-blend-mode!  [this mode]    "返回更新后的图层")
  (visible?         [this]         "是否可见")
  (set-visible!     [this v]       "返回更新后的图层")
  (locked?          [this]         "是否锁定（不可编辑）")
  (set-locked!      [this v]       "返回更新后的图层")
  ;; 核心渲染
  (render-to!       [this target-canvas dx dy]
    "将图层自身内容按当前混合模式与透明度合成到 target-canvas。
     偏移 (dx, dy) 为图层左上角在目标画布中的位置。
     返回脏矩形序列 [[x y w h] ...]，坐标系为 target-canvas。"))

;; ── 画板（图层栈） ─────────────────────────────────────
(defprotocol IArtboard
  "拥有固定尺寸和有序图层栈的画板。
   图层索引从 0（最底层）开始。"
  (artboard-id        [this]           "唯一标识")
  (width              [this]           "整数宽度")
  (height             [this]           "整数高度")
  (set-size!          [this w h]       "返回更新后的画板")
  (layers             [this]           "从下到上的 ILayer 向量")
  (add-layer!         [this layer index] "在指定索引插入图层，返回新画板")
  (remove-layer!      [this layer]     "移除图层，返回新画板")
  (move-layer!        [this from-index to-index] "移动图层顺序，返回新画板")
  (composite!         [this target-canvas]
    "从底向上依次将每个可见图层的 render-to! 作用于 target-canvas。
     返回所有脏矩形的并集序列，坐标系为 target-canvas。"))