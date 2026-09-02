package top.kzre.krro.canvas.core;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 专门用于矩形条目的四叉树，适用于曲线段 AABB 索引。
 * 非线程安全。
 */
public final class RectQuadTree<T> {

    private static final int DEFAULT_CAPACITY = 8;
    private static final double DEFAULT_RANGE = 1e9;

    private Rect bounds;
    private final int capacity;
    private final boolean autoExpand;
    private final List<Entry<T>> entries;
    private RectQuadTree<T> nw, ne, sw, se;
    private boolean divided = false;

    // ========== 构造器 ==========

    public RectQuadTree() {
        this(new Rect(-DEFAULT_RANGE, -DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE),
                DEFAULT_CAPACITY, true);
    }

    public RectQuadTree(Rect bounds) {
        this(bounds, DEFAULT_CAPACITY, false);
    }

    public RectQuadTree(Rect bounds, int capacity) {
        this(bounds, capacity, false);
    }

    private RectQuadTree(Rect bounds, int capacity, boolean autoExpand) {
        if (bounds == null) throw new IllegalArgumentException("bounds must not be null");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.bounds = bounds;
        this.capacity = capacity;
        this.autoExpand = autoExpand;
        this.entries = new ArrayList<>(capacity);
    }

    // ========== 插入 ==========

    /**
     * 插入一个矩形及其关联值。
     */
    public void insert(Rect rect, T value) {
        if (!bounds.intersects(rect)) {
            if (autoExpand) expandBounds(rect);
            else throw new IllegalArgumentException("Rect out of bounds");
        }
        insertInternal(rect, value);
    }

    private void insertInternal(Rect rect, T value) {
        if (!divided) {
            if (entries.size() < capacity) {
                entries.add(new Entry<>(rect, value));
                return;
            }
            subdivide();
        }

        // 尝试分配给子节点（根据矩形中心）
        double cx = rect.midX(), cy = rect.midY();
        if (nw.bounds.contains(cx, cy)) {
            nw.insertInternal(rect, value);
        } else if (ne.bounds.contains(cx, cy)) {
            ne.insertInternal(rect, value);
        } else if (sw.bounds.contains(cx, cy)) {
            sw.insertInternal(rect, value);
        } else if (se.bounds.contains(cx, cy)) {
            se.insertInternal(rect, value);
        } else {
            // 矩形跨越多个子节点，存于当前节点
            entries.add(new Entry<>(rect, value));
        }
    }

    private void subdivide() {
        double midX = bounds.midX();
        double midY = bounds.midY();

        nw = new RectQuadTree<>(new Rect(bounds.xMin, bounds.yMin, midX, midY), capacity, false);
        ne = new RectQuadTree<>(new Rect(midX, bounds.yMin, bounds.xMax, midY), capacity, false);
        sw = new RectQuadTree<>(new Rect(bounds.xMin, midY, midX, bounds.yMax), capacity, false);
        se = new RectQuadTree<>(new Rect(midX, midY, bounds.xMax, bounds.yMax), capacity, false);

        divided = true;
    }

    // ========== 扩展边界 ==========

    private void expandBounds(Rect rect) {
        double newMinX = Math.min(bounds.xMin, rect.xMin);
        double newMinY = Math.min(bounds.yMin, rect.yMin);
        double newMaxX = Math.max(bounds.xMax, rect.xMax);
        double newMaxY = Math.max(bounds.yMax, rect.yMax);

        double marginX = (newMaxX - newMinX) * 0.1;
        double marginY = (newMaxY - newMinY) * 0.1;
        newMinX -= marginX;
        newMinY -= marginY;
        newMaxX += marginX;
        newMaxY += marginY;

        Rect newBounds = new Rect(newMinX, newMinY, newMaxX, newMaxY);

        List<Entry<T>> all = getAllEntries();
        this.bounds = newBounds;
        this.divided = false;
        this.entries.clear();
        this.nw = this.ne = this.sw = this.se = null;
        for (Entry<T> e : all) {
            insertInternal(e.rect, e.value);
        }
    }

    private List<Entry<T>> getAllEntries() {
        List<Entry<T>> result = new ArrayList<>();
        collectEntries(result);
        return result;
    }

    private void collectEntries(List<Entry<T>> collector) {
        if (divided) {
            if (nw != null) nw.collectEntries(collector);
            if (ne != null) ne.collectEntries(collector);
            if (sw != null) sw.collectEntries(collector);
            if (se != null) se.collectEntries(collector);
        }
        collector.addAll(entries);
    }

    // ========== 查询 ==========

    /**
     * 查询与给定矩形相交的所有条目。
     */
    public void query(Rect rect, Consumer<Entry<T>> consumer) {
        if (!bounds.intersects(rect)) return;

        if (divided) {
            nw.query(rect, consumer);
            ne.query(rect, consumer);
            sw.query(rect, consumer);
            se.query(rect, consumer);
        }
        for (Entry<T> e : entries) {
            if (e.rect.intersects(rect)) {
                consumer.accept(e);
            }
        }
    }

    public List<Entry<T>> query(Rect rect) {
        List<Entry<T>> result = new ArrayList<>();
        query(rect, result::add);
        return result;
    }

    /**
     * 查询点附近（扩展阈值）的矩形条目。
     */
    public List<Entry<T>> queryPoint(double x, double y, double threshold) {
        Rect rect = new Rect(x - threshold, y - threshold, x + threshold, y + threshold);
        List<Entry<T>> result = new ArrayList<>();
        query(rect, result::add);
        return result;
    }

    /**
     * 查找最近的 k 个矩形条目（按中心距离排序）。
     */
    public List<NearestResult<T>> nearestK(double x, double y, int k, Predicate<Entry<T>> predicate) {
        List<Entry<T>> candidates = new ArrayList<>();
        Rect queryRect = new Rect(x - 1000, y - 1000, x + 1000, y + 1000); // 大范围初始
        query(queryRect, e -> {
            if (predicate.test(e)) candidates.add(e);
        });

        PriorityQueue<NearestResult<T>> heap = new PriorityQueue<>(
                (a, b) -> Double.compare(b.distSq, a.distSq)
        );
        for (Entry<T> e : candidates) {
            double dx = x - e.rect.midX();
            double dy = y - e.rect.midY();
            double d = dx * dx + dy * dy;
            if (heap.size() < k) {
                heap.offer(new NearestResult<>(e.value, d, e.rect.midX(), e.rect.midY()));
            } else {
                assert heap.peek() != null;
                if (d < heap.peek().distSq) {
                    heap.poll();
                    heap.offer(new NearestResult<>(e.value, d, e.rect.midX(), e.rect.midY()));
                }
            }
        }
        List<NearestResult<T>> list = new ArrayList<>(heap);
        list.sort(Comparator.comparingDouble(r -> r.distSq));
        return list;
    }

    public List<NearestResult<T>> nearestK(double x, double y, int k) {
        return nearestK(x, y, k, e -> true);
    }

    // ========== 删除 ==========

    public boolean delete(Rect rect, T value) {
        if (divided) {
            boolean removed = false;
            if (nw != null) removed |= nw.delete(rect, value);
            if (ne != null) removed |= ne.delete(rect, value);
            if (sw != null) removed |= sw.delete(rect, value);
            if (se != null) removed |= se.delete(rect, value);
            return removed;
        } else {
            return entries.removeIf(e -> e.rect.equals(rect) && e.value.equals(value));
        }
    }

    public boolean deleteIf(Predicate<Entry<T>> predicate) {
        if (divided) {
            boolean any = false;
            if (nw != null) any |= nw.deleteIf(predicate);
            if (ne != null) any |= ne.deleteIf(predicate);
            if (sw != null) any |= sw.deleteIf(predicate);
            if (se != null) any |= se.deleteIf(predicate);
            return any;
        } else {
            return entries.removeIf(predicate);
        }
    }

    // ========== 统计 ==========

    public boolean isEmpty() {
        return !divided && entries.isEmpty();
    }

    public int size() {
        if (divided) {
            return (nw != null ? nw.size() : 0) +
                    (ne != null ? ne.size() : 0) +
                    (sw != null ? sw.size() : 0) +
                    (se != null ? se.size() : 0);
        } else {
            return entries.size();
        }
    }

    // ========== 内部类 ==========

    public static class Entry<T> {
        public final Rect rect;
        public final T value;

        Entry(Rect rect, T value) {
            this.rect = rect;
            this.value = value;
        }

        @Override
        public String toString() {
            return String.format("Entry{rect=%s, value=%s}", rect, value);
        }
    }

    public static class NearestResult<T> {
        public final T value;
        public final double distSq;
        public final double x;
        public final double y;

        NearestResult(T value, double distSq, double x, double y) {
            this.value = value;
            this.distSq = distSq;
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return String.format("NearestResult{value=%s, dist=%.2f, (%.2f, %.2f)}",
                    value, Math.sqrt(distSq), x, y);
        }
    }

}