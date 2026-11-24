/**
 * 登录业务：换 openid、查或建客户、签发 JWT。由 {@code controller.auth} 的 {@code AuthController} 调用。
 *
 * <p>客户行不是本业务自己的 Mapper。登录写的是客户表，所以这里使用
 * {@code mapper.customer.CustomerMapper}，客户对象在 {@code pojo.customer.entity}。
 */
package com.zhongbang.controller.service.auth;
