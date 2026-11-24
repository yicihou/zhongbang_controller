package com.zhongbang.controller.pojo.customer.vo;

/**
 * {@code GET /api/me/changes} 的返回。
 * cursor 是服务器当前时间的毫秒时间戳，下次请求放在 since 里。
 * orders / repairs / products 分别表示这一段时间里，当前客户的订单、报修，或任一商品（上下架、改价）有更新。
 */
public record ChangeVo(
        String cursor,
        boolean changed,
        boolean orders,
        boolean repairs,
        boolean products
) {
}
