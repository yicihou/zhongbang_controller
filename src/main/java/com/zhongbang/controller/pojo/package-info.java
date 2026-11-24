/**
 * 数据对象层。装字段，不访问数据库，也不写「允许不允许」的规则。
 *
 * <p>按业务再分子包。一个业务下面还可以按用途再分：
 * {@code entity} 对应一张表，{@code dto} 是接口的请求体，{@code vo} 是返回给页面的字段。
 * 现在已有代码的是 {@code auth} 的登录请求和响应，以及 {@code customer} 的客户、设备档案。
 * 还没写类的业务只有本层的说明文件。
 */
package com.zhongbang.controller.pojo;
