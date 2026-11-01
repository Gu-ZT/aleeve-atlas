package dev.dubhe.map.map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/// 待重采样区块。只查询当前采样范围，不复制或遍历跑图期间积累的历史标记。
///
/// 标记与采样均在客户端主线程执行。远处或暂未加载的区块必须保留标记，
/// 直到成功采样，否则回访时会把磁盘中的旧地形误认为最新数据。
public final class DirtyChunks {
    private final LongSet pending = new LongOpenHashSet();

    public void mark(long chunk) {
        this.pending.add(chunk);
    }

    public boolean contains(long chunk) {
        return this.pending.contains(chunk);
    }

    /// 仅在新采样已写入缓存后调用；采样失败时保留旧图像和更新标记。
    public void sampled(long chunk) {
        this.pending.remove(chunk);
    }

    public int size() {
        return this.pending.size();
    }

    public void clear() {
        this.pending.clear();
    }
}
