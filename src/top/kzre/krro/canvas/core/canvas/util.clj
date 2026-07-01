(ns top.kzre.krro.canvas.core.canvas.util
  "通用像素值转换与画布尺寸查询。")

(def ^:const ^double byte-max  255.0)
(def ^:const ^double short-max 65535.0)

(defn native->float
  "将原生数组中的值 v 转换为 0.0‑1.0 浮点。"
  [v bits]
  (case bits
    8  (double (/ (bit-and (int v) 0xFF) byte-max))
    16 (double (/ (bit-and (int v) 0xFFFF) short-max))
    32 (double v)))

(defn float->native
  "将 0.0‑1.0 浮点值转换为原生存储值。"
  [val bits]
  (case bits
    8  (byte (Math/round (* (double val) byte-max)))
    16 (short (Math/round (* (double val) short-max)))
    32 (float val)))

(defn canvas-width  [canvas] (:width canvas))
(defn canvas-height [canvas] (:height canvas))