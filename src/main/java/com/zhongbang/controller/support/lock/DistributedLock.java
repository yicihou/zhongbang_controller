package com.zhongbang.controller.support.lock;

import java.time.Duration;

/**
 * 短时互斥锁。打算给档期、库存用。当前没有业务调用。
 */
public interface DistributedLock {

    /**
     * @param key 业务自己拼，例如某个师傅加日期加时段
     * @param ttl 锁自动失效的时间，防止持有者崩溃后永远不解锁
     * @return true 表示这次加上了；false 表示已经有人持有
     */
    boolean tryLock(String key, Duration ttl);

    /** 主动释放。Redis 实现不校验持有者，谁调用都会删掉这把锁。 */
    void unlock(String key);
}
