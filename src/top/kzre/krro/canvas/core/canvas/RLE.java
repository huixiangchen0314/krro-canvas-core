package top.kzre.krro.canvas.core.canvas;

/**
 * 游程编码压缩/解压工具（针对 float[] 光栅，位深 32）。
 * 压缩格式：int[] 数组，每个游程由 [count, ch0, ch1, ..., chN-1] 组成。
 * count 最大值为 Integer.MAX_VALUE（但实际上分段处理以避免溢出）。
 */
public class RLE {

    private RLE() {}

    /**
     * 压缩光栅数据。支持任意通道数。
     * @param pixels 源光栅数组，长度 width*height*channels
     * @param w 宽度
     * @param h 高度
     * @param channels 通道数（1 或 4）
     * @param bits 每通道位数（仅支持 32，忽略，保留兼容性）
     * @return 压缩后的 int[] 数组
     */
    public static int[] compress(float[] pixels, int w, int h, int channels, int bits) {
        if (bits != 32) {
            throw new UnsupportedOperationException("RLE only supports 32-bit float channels");
        }
        int totalPixels = w * h;
        int maxCapacity = totalPixels * (1 + channels); // 最坏情况：每个像素一个游程
        int[] runs = new int[maxCapacity];
        int runIndex = 0; // 写入位置

        int pixelIdx = 0;
        while (pixelIdx < totalPixels) {
            int startIdx = pixelIdx * channels;
            // 当前像素的通道值
            int[] currentChannels = new int[channels];
            for (int c = 0; c < channels; c++) {
                currentChannels[c] = Float.floatToIntBits(pixels[startIdx + c]);
            }

            // 统计连续相同像素的个数
            int count = 1;
            while (pixelIdx + count < totalPixels) {
                int nextIdx = (pixelIdx + count) * channels;
                boolean same = true;
                for (int c = 0; c < channels; c++) {
                    if (Float.floatToIntBits(pixels[nextIdx + c]) != currentChannels[c]) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    count++;
                } else {
                    break;
                }
            }

            // 写入游程（可能分段，避免 count 过大？此处暂不处理，count 不会超过 totalPixels）
            runs[runIndex++] = count;
            for (int c = 0; c < channels; c++) {
                runs[runIndex++] = currentChannels[c];
            }

            pixelIdx += count;
        }

        // 截断数组
        int[] trimmed = new int[runIndex];
        System.arraycopy(runs, 0, trimmed, 0, runIndex);
        return trimmed;
    }

    /**
     * 解压为 float[] 光栅。
     * @param runs 压缩数据
     * @param w 宽度
     * @param h 高度
     * @param channels 通道数
     * @param bits 位数（仅支持 32）
     * @return 解压后的 float[] 像素数组
     */
    public static float[] decompress(int[] runs, int w, int h, int channels, int bits) {
        if (bits != 32) {
            throw new UnsupportedOperationException("RLE only supports 32-bit float channels");
        }
        int totalPixels = w * h;
        float[] pixels = new float[totalPixels * channels];
        int runIndex = 0;
        int pixelIdx = 0;

        while (pixelIdx < totalPixels && runIndex < runs.length) {
            int count = runs[runIndex++];
            // 读取当前游程的通道值
            int[] chanVals = new int[channels];
            for (int c = 0; c < channels; c++) {
                chanVals[c] = runs[runIndex++];
            }

            // 填充 count 个像素
            for (int i = 0; i < count; i++) {
                int base = pixelIdx * channels;
                for (int c = 0; c < channels; c++) {
                    pixels[base + c] = Float.intBitsToFloat(chanVals[c]);
                }
                pixelIdx++;
            }
        }
        return pixels;
    }
}