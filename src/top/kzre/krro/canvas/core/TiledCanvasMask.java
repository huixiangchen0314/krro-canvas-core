package top.kzre.krro.canvas.core;

import top.kzre.colorutils.color.RGB;
import top.kzre.krro.util.tile.TiledCanvas;

public final class TiledCanvasMask implements Mask{
    private final TiledCanvas canvas;
    private final int channels;
    private final float[] pixel;
   public TiledCanvasMask(TiledCanvas canvas) {
        this.canvas = canvas;
        this.channels = canvas.getChannels();
        // 灰度，或者RGB/RGBA
        assert channels == 1 || channels == 3 || channels == 4;
        this.pixel = new float[channels];
    }

    @Override
    public float getValue(double x, double y) {
        canvas.bilinearSample((float) x, (float) y, pixel);
        float gray;
        if (channels == 1) {
            gray = pixel[0];
        }
        else if (channels == 3) {
            gray = RGB.luminance(pixel);
        }else {
            gray = RGB.luminance(pixel) * RGB.alpha(pixel);
        }

        return gray;
    }
}
