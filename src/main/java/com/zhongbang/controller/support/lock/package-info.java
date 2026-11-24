/**
 * 短时互斥锁占位，打算用来占师傅档期或扣库存。没有业务代码调用。
 * 内存版只在单个进程里有效；Redis 版用 {@code SETNX} 加过期时间，解锁时直接删 key，不校验是谁加的锁。
 */
package com.zhongbang.controller.support.lock;
