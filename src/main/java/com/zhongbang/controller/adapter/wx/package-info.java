/**
 * 微信登录适配。
 *
 * <p>正式环境应拿小程序 {@code wx.login} 的 code 去微信换 openid。
 * 现在是本地占位：不访问微信，把 code 拼成 {@code dev_} 开头的 openid，方便没有微信账号也能登录。
 */
package com.zhongbang.controller.adapter.wx;
