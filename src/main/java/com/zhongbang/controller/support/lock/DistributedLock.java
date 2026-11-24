package com.zhongbang.controller.support.lock;

import java.time.Duration;

/** Slot / capacity lock hook (docs §4.5). */
public interface DistributedLock {

    boolean tryLock(String key, Duration ttl);

    void unlock(String key);
}
