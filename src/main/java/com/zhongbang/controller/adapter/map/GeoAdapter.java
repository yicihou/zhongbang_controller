package com.zhongbang.controller.adapter.map;

import org.springframework.stereotype.Component;

/**
 * 两点距离的占位实现。启动时会被创建，但目前没有任何类注入它。
 *
 * <p>{@code @Component} 表示把这个类交给 Spring 管理，别处可以用构造器参数要到同一个实例。
 * 后面的 {@code @Service}、{@code @RestController} 也是组件，只是名字标明用途。
 *
 * <p>{@code distanceKm} 用 Haversine 公式估算球面距离，单位公里。地球半径取 6371。
 * 四个参数是两点的纬度和经度（角度）。这里没有调用地图厂商的接口。
 */
@Component
public class GeoAdapter {

    /** @return 两点球面距离，单位公里 */
    public double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371.0 * c;
    }
}
