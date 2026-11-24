package com.zhongbang.controller.common.enums;

public enum DeliveryMethod {
    EXPRESS,
    FREIGHT,
    HOME_DELIVERY,
    ON_SITE_INSTALL,
    STORE_PICKUP;

    public boolean requiresInstall() {
        return this == ON_SITE_INSTALL;
    }
}
