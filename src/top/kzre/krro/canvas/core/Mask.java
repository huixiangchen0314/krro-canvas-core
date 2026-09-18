package top.kzre.krro.canvas.core;

/**
 * 蒙板接口, 注意 krro-canvas 仅做线性合成
 * 这里的蒙板接口仅用于平凡的蒙板数据传递，而不支持引用
 */
@FunctionalInterface
public interface Mask {
    float getValue(double x, double y);
}
