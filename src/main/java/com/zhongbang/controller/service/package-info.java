/**
 * 业务规则层。Controller 调用这里，这里再调用 Mapper。
 *
 * <p>本层按业务再分子包，和 {@code controller}、{@code mapper}、{@code pojo} 使用同一个业务名。
 * 现在的 {@code AuthService}、{@code AssetService} 是直接放在业务子包里的具体类。
 * 以后某个业务要拆成接口和实现时，实现类放在该业务下的 {@code impl} 子包，调用方仍然依赖接口。
 *
 * <p>已经有类的：{@code auth}、{@code customer}。其余业务子包只有说明文件。
 */
package com.zhongbang.controller.service;
