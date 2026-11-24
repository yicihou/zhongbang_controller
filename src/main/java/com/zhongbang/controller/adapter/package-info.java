/**
 * 外部系统适配层。
 *
 * <p>业务代码不直接调用微信、对象存储或地图，而是调用这里的类。以后换成真正的 SDK 时，只改这一层。
 *
 * <p>目前三个实现都是占位：只有 {@code WxAuthAdapter} 被登录流程用到，
 * 距离计算和上传凭证没有任何接口调用。
 */
package com.zhongbang.controller.adapter;
