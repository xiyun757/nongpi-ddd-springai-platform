package com.nongpi.fulfillment.common.domain;

/**
 * 乐观锁冲突异常 — 并发修改聚合根时抛出
 *
 * <p>当两个请求同时修改同一聚合根时，后到的请求因 version 不匹配
 * 导致更新行数为 0，此时抛出本异常。</p>
 */
public class OptimisticLockException extends RuntimeException {

    public OptimisticLockException(String message) {
        super(message);
    }
}
