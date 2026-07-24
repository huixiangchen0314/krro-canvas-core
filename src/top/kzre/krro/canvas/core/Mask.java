package top.kzre.krro.canvas.core;

/**
 * 掩码接口
 */
@FunctionalInterface
public interface Mask {
    float getValue(double x, double y);
}
