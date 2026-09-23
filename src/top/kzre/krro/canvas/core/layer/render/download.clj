(ns top.kzre.krro.canvas.core.layer.render.download
  (:require [top.kzre.krro.core.util.promise :as promise])
  (:import (top.kzre.krro.canvas.core.layer.render TileUtils)
           (top.kzre.krro.util.tile TiledCanvas)))



(defn download
  "把 canvas 的所有瓦片下载到 CPU，返回新画布。

   <b>语义变化</b>：从「原地替换」改为「返回新画布」。原 canvas
   不被修改，不发生所有权转移。调用方拿到的新画布是纯 CPU 侧。

   返回 Promise<TiledCanvas>，所有下载完成后解析为新画布。
   无瓦片需要下载时立即返回已完成 promise。

   参数校验错误（尺寸、通道数不匹配）会同步抛出，包进失败的 promise。

   失败时新画布在 TileUtils.download 内部已 close，不会泄漏。"
  [^TiledCanvas canvas]
  (-> (TileUtils/download canvas)
      (promise/from-completable-future)))

(defn download!
  "把 canvas 的所有瓦片下载到 CPU，<b>原地替换</b>，返回同一 canvas。

   与 download 的区别：
     - download  : 返回新画布，原画布不动
     - download! : 原地替换，原画布被修改

   返回 Promise<TiledCanvas>，解析为原 canvas（已就地替换）。
   调用方持有原 canvas 的所有权不变。

   失败时 canvas 可能处于部分替换的中间状态——已替换的瓦片是 CPU 侧，
   未替换的仍是 GPU 侧。两者都是合法的 TileData，画布仍可用。
   重试本方法即可继续处理剩余的 GPU 瓦片。

   <b>并发契约</b>：调用期间 canvas 不应被其他线程写入。读取安全。"
  [^TiledCanvas canvas]
  (-> (TileUtils/downloadInPlace canvas)
      (promise/from-completable-future)
      (promise/fmap (fn [_] canvas))))
