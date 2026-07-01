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
  "将 0.0‑1.0 浮点值转换为无符号整数（int）。
   8 位返回 0‑255，16 位返回 0‑65535，32 位返回 float。
   使用四舍五入取整，极值钳制防止越界。"
  [val bits]
  (let [val (max 0.0 (min 1.0 (double val)))]
    (case bits
      8  (int (max 0 (min 255 (Math/round (* val byte-max)))))
      16 (int (max 0 (min 65535 (Math/round (* val short-max)))))
      32 (float val))))

(defn int->byte
  "将 0‑255 的 int 转换为等价的 byte（有符号表示）。"
  [i]
  (byte (if (<= i 127) i (- i 256))))

(defn int->short
  "将 0‑65535 的 int 转换为等价的 short（有符号表示）。"
  [i]
  (short (if (<= i 32767) i (- i 65536))))

(defn canvas-width  [canvas] (:width canvas))
(defn canvas-height [canvas] (:height canvas))