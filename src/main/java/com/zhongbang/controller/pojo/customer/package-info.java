/**
 * 客户域的数据对象。{@code entity} 对应表，{@code dto} 是设备档案接口的请求和返回字段。
 *
 * <p>这些类不写 SQL。查和改由 {@code mapper.customer} 完成，规则在 {@code service.customer}
 * （客户行的插入目前发生在登录的 {@code service.auth}）。
 */
package com.zhongbang.controller.pojo.customer;
