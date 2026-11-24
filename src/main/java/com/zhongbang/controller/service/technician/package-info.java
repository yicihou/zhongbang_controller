/**
 * 师傅档案的业务规则占位，目前没有 Service。
 * 以后的规则放在本包，由同名 Controller 调用，通过同名 Mapper 落库。
 * 需要拆成接口和实现时，实现类放在本包下的 {@code impl}。
 *
 * <p>表 {@code technician} 已建好。没有查询或排班接口。
 * 安全配置预留了 {@code /api/tech/**}，只允许 {@code ROLE_TECHNICIAN}，还没有人签发这种 JWT。
 * 同一业务在四层各有一个同名子包，目前都只有说明文件：
 * {@code controller.technician}、{@code service.technician}、{@code mapper.technician}、{@code pojo.technician}。
 */
package com.zhongbang.controller.service.technician;
