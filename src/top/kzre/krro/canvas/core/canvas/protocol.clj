(ns top.kzre.krro.canvas.core.canvas.protocol)

(defprotocol ICanvas
  "画布核心协议：可读写像素容器，带脏矩形追踪。"
  (width ^long [canvas] "画布宽度（像素）")
  (height ^long [canvas] "画布高度（像素）")
  (data ^floats [canvas] "底层像素存储，返回 float-array，供高性能访问")
  (color-space [canvas] "返回色彩空间关键字，如 :gray 或 :rgba")
  (get-pixel [canvas x y] "获取 (x, y) 处像素的通道值，返回 float-array")
  (set-pixel! [canvas x y color] "设置 (x, y) 处像素，color 为 float-array，返回更新后的画布（脏矩形已更新）")
  (dirty-rect [canvas] "返回当前脏矩形，格式 [x y w h] 或 nil")
  (clear-dirty! [canvas] "清除脏矩形标记，返回更新后的画布"))