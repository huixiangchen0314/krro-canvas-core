package top.kzre.krro.canvas.core;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 二维四叉树，用于空间索引。
 * 非线程安全，适合单线程或不可变数据结构。
 */
public final class QuadTree<T> {

    private static final int DEFAULT_CAPACITY = 8;
    private static final double DEFAULT_RANGE = 1e9;

    private Rect bounds;
    private final int capacity;
    private final boolean autoExpand;
    private final List<Entry<T>> entries;
    private QuadTree<T> nw, ne, sw, se;
    private boolean divided = false;

    // ========== 构造器 ==========

    public QuadTree() {
        this(new Rect(-DEFAULT_RANGE, -DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE), DEFAULT_CAPACITY, true);
    }

    public QuadTree(Rect bounds) {
        this(bounds, DEFAULT_CAPACITY, false);
    }

    public QuadTree(Rect bounds, int capacity) {
        this(bounds, capacity, false);
    }

    private QuadTree(Rect bounds, int capacity, boolean autoExpand) {
        if (bounds == null) throw new IllegalArgumentException("bounds must not be null");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.bounds = bounds;
        this.capacity = capacity;
        this.autoExpand = autoExpand;
        this.entries = new ArrayList<>(capacity);
    }

    // ========== 插入 ==========

    public void insert(double x, double y, T value) {
        if (!bounds.contains(x, y)) {
            if (autoExpand) {
                expandBounds(x, y);
            } else {
                throw new IllegalArgumentException("Point (" + x + ", " + y + ") out of bounds");
            }
        }
        insertInternal(x, y, value);
    }

    private void insertInternal(double x, double y, T value) {
        if (!bounds.contains(x, y)) {
            throw new IllegalStateException("Point (" + x + ", " + y + ") out of bounds after expansion");
        }

        if (!divided) {
            if (entries.size() < capacity) {
                entries.add(new Entry<>(x, y, value));
                return;
            }
            subdivide();
        }

        if (divided) {
            for (Entry<T> e : entries) {
                insertIntoChild(e.x, e.y, e.value);
            }
            entries.clear();
        }

        insertIntoChild(x, y, value);
    }

    private void insertIntoChild(double x, double y, T value) {
        if (nw.bounds.contains(x, y)) nw.insert(x, y, value);
        else if (ne.bounds.contains(x, y)) ne.insert(x, y, value);
        else if (sw.bounds.contains(x, y)) sw.insert(x, y, value);
        else if (se.bounds.contains(x, y)) se.insert(x, y, value);
        else {
            if (x < bounds.midX()) nw.insert(x, y, value);
            else ne.insert(x, y, value);
        }
    }

    private void subdivide() {
        double midX = bounds.midX();
        double midY = bounds.midY();

        nw = new QuadTree<>(new Rect(bounds.xMin, bounds.yMin, midX, midY), capacity, false);
        ne = new QuadTree<>(new Rect(midX, bounds.yMin, bounds.xMax, midY), capacity, false);
        sw = new QuadTree<>(new Rect(bounds.xMin, midY, midX, bounds.yMax), capacity, false);
        se = new QuadTree<>(new Rect(midX, midY, bounds.xMax, bounds.yMax), capacity, false);

        divided = true;
    }

    private void expandBounds(double x, double y) {
        double newMinX = Math.min(bounds.xMin, x);
        double newMinY = Math.min(bounds.yMin, y);
        double newMaxX = Math.max(bounds.xMax, x);
        double newMaxY = Math.max(bounds.yMax, y);

        double marginX = (newMaxX - newMinX) * 0.1;
        double marginY = (newMaxY - newMinY) * 0.1;
        newMinX -= marginX;
        newMinY -= marginY;
        newMaxX += marginX;
        newMaxY += marginY;

        Rect newBounds = new Rect(newMinX, newMinY, newMaxX, newMaxY);

        List<Entry<T>> allEntries = getAllEntries();
        this.bounds = newBounds;
        this.divided = false;
        this.entries.clear();
        this.nw = this.ne = this.sw = this.se = null;

        for (Entry<T> e : allEntries) {
            insertInternal(e.x, e.y, e.value);
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
        } else {
            collector.addAll(entries);
        }
    }

    // ========== 删除 ==========

    public boolean delete(double x, double y, T value) {
        if (!bounds.contains(x, y)) return false;

        if (divided) {
            boolean removed = false;
            if (nw != null && nw.delete(x, y, value)) removed = true;
            else if (ne != null && ne.delete(x, y, value)) removed = true;
            else if (sw != null && sw.delete(x, y, value)) removed = true;
            else if (se != null && se.delete(x, y, value)) removed = true;
            return removed;
        } else {
            for (int i = 0; i < entries.size(); i++) {
                Entry<T> e = entries.get(i);
                if (Math.abs(e.x - x) < 1e-9 && Math.abs(e.y - y) < 1e-9 && e.value.equals(value)) {
                    entries.remove(i);
                    return true;
                }
            }
            return false;
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


    // ========== 查询 ==========

    public void query(Rect rect, Consumer<Entry<T>> consumer) {
        if (!bounds.intersects(rect)) return;

        if (divided) {
            nw.query(rect, consumer);
            ne.query(rect, consumer);
            sw.query(rect, consumer);
            se.query(rect, consumer);
        } else {
            for (Entry<T> e : entries) {
                if (rect.contains(e.x, e.y)) {
                    consumer.accept(e);
                }
            }
        }
    }

    public List<Entry<T>> query(Rect rect) {
        List<Entry<T>> result = new ArrayList<>();
        query(rect, result::add);
        return result;
    }

    public List<Entry<T>> queryPoint(double x, double y, double threshold) {
        Rect rect = new Rect(x - threshold, y - threshold, x + threshold, y + threshold);
        List<Entry<T>> result = new ArrayList<>();
        query(rect, result::add);
        return result;
    }

    public NearestResult<T> nearest(double x, double y) {
        return nearest(x, y, Double.MAX_VALUE);
    }

    private NearestResult<T> nearest(double x, double y, double bestDist) {
        if (!divided) {
            T bestValue = null;
            double best = bestDist;
            double bestX = 0, bestY = 0;
            for (Entry<T> e : entries) {
                double d = distSq(x, y, e.x, e.y);
                if (d < best) {
                    best = d;
                    bestValue = e.value;
                    bestX = e.x;
                    bestY = e.y;
                }
            }
            return (bestValue == null) ? null : new NearestResult<>(bestValue, best, bestX, bestY);
        } else {
            QuadTree<T>[] children = orderChildren(x, y);
            NearestResult<T> bestResult = null;
            double currentBest = bestDist;
            for (QuadTree<T> child : children) {
                if (child == null) continue;
                double minDist = child.bounds.distToPointSq(x, y);
                if (minDist >= currentBest) continue;
                NearestResult<T> candidate = child.nearest(x, y, currentBest);
                if (candidate != null && candidate.distSq < currentBest) {
                    currentBest = candidate.distSq;
                    bestResult = candidate;
                }
            }
            return bestResult;
        }
    }


    /**
     * 查找最近的 k 个点，按距离升序排列，仅包含满足 predicate 的点。
     */
    public List<NearestResult<T>> nearestK(double x, double y, int k, Predicate<Entry<T>> predicate) {
        if (k <= 0) return Collections.emptyList();
        PriorityQueue<NearestResult<T>> heap = new PriorityQueue<>(
                (a, b) -> Double.compare(b.distSq, a.distSq)
        );
        nearestK(x, y, k, heap, predicate);
        List<NearestResult<T>> list = new ArrayList<>(heap);
        list.sort(Comparator.comparingDouble(r -> r.distSq));
        return list;
    }

    private void nearestK(double x, double y, int k,
                          PriorityQueue<NearestResult<T>> heap,
                          Predicate<Entry<T>> predicate) {
        if (!divided) {
            for (Entry<T> e : entries) {
                if (!predicate.test(e)) continue;
                double d = distSq(x, y, e.x, e.y);
                if (heap.size() < k) {
                    heap.offer(new NearestResult<>(e.value, d, e.x, e.y));
                } else {
                    assert heap.peek() != null;
                    if (d < heap.peek().distSq) {
                        heap.poll();
                        heap.offer(new NearestResult<>(e.value, d, e.x, e.y));
                    }
                }
            }
            return;
        }

        QuadTree<T>[] children = orderChildren(x, y);
        for (QuadTree<T> child : children) {
            if (child == null) continue;
            double minDist = child.bounds.distToPointSq(x, y);
            if (heap.size() == k) {
                assert heap.peek() != null;
                if (minDist >= heap.peek().distSq) continue;
            }
            child.nearestK(x, y, k, heap, predicate);
        }
    }

    // 无 predicate 的重载
    public List<NearestResult<T>> nearestK(double x, double y, int k) {
        return nearestK(x, y, k, e -> true);
    }

    private void nearestK(double x, double y, int k,
                          PriorityQueue<NearestResult<T>> heap) {
        if (!divided) {
            for (Entry<T> e : entries) {
                double d = distSq(x, y, e.x, e.y);
                if (heap.size() < k) {
                    heap.offer(new NearestResult<>(e.value, d, e.x, e.y));
                } else {
                    assert heap.peek() != null;
                    if (d < heap.peek().distSq) {
                        heap.poll();
                        heap.offer(new NearestResult<>(e.value, d, e.x, e.y));
                    }
                }
            }
            return;
        }

        // 按子节点到查询点的最小距离排序
        QuadTree<T>[] children = orderChildren(x, y);
        for (QuadTree<T> child : children) {
            if (child == null) continue;
            double minDist = child.bounds.distToPointSq(x, y);
            if (heap.size() == k) {
                assert heap.peek() != null;
                if (minDist >= heap.peek().distSq) continue;
            }
            child.nearestK(x, y, k, heap);
        }
    }



    // ========== 辅助 ==========

    @SuppressWarnings("unchecked")
    private QuadTree<T>[] orderChildren(double x, double y) {
        QuadTree<T>[] ordered = new QuadTree[4];
        int i = 0;
        if (x < bounds.midX()) {
            if (y < bounds.midY()) {
                ordered[i++] = nw; ordered[i++] = ne;
                ordered[i++] = sw; ordered[i++] = se;
            } else {
                ordered[i++] = sw; ordered[i++] = se;
                ordered[i++] = nw; ordered[i++] = ne;
            }
        } else {
            if (y < bounds.midY()) {
                ordered[i++] = ne; ordered[i++] = nw;
                ordered[i++] = se; ordered[i++] = sw;
            } else {
                ordered[i++] = se; ordered[i++] = sw;
                ordered[i++] = ne; ordered[i++] = nw;
            }
        }
        int count = 0;
        for (QuadTree<T> child : ordered) {
            if (child != null) count++;
        }
        QuadTree<T>[] filtered = new QuadTree[count];
        int idx = 0;
        for (QuadTree<T> child : ordered) {
            if (child != null) filtered[idx++] = child;
        }
        return filtered;
    }

    private static double distSq(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return dx * dx + dy * dy;
    }

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
        public final double x, y;
        public final T value;
        Entry(double x, double y, T value) {
            this.x = x;
            this.y = y;
            this.value = value;
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
            return String.format("NearestResult{value=%s, dist=%.4f, (%.2f, %.2f)}",
                    value, Math.sqrt(distSq), x, y);
        }
    }

}