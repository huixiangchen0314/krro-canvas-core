package top.kzre.krro.canvas.core;

/**
 * 蒙板接口
 */
@FunctionalInterface
public interface Mask {
    float getValue(double x, double y);
}
