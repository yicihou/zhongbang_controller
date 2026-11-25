package com.zhongbang.controller.pojo.product.vo;

import java.util.List;

/**
 * 商品返回值。小程序列表用 price、stock、coverImage；管理端另外看到全部规格和上下架状态。
 */
public final class ProductVo {

    private ProductVo() {
    }

    public record Card(
            Long id,
            String name,
            String model,
            String coverImage,
            long price,
            long depositAmount,
            int stock,
            List<String> tags,
            Integer categoryId,
            String subtitle,
            String brand,
            int depositRate,
            String status
    ) {
    }

    public record Detail(
            Long id,
            String name,
            String model,
            String coverImage,
            long price,
            long depositAmount,
            int stock,
            List<String> tags,
            Integer categoryId,
            String category,
            String subtitle,
            String brand,
            int depositRate,
            String status,
            Warranty warranty,
            List<String> services,
            List<Spec> specs,
            List<Sku> skus
    ) {
    }

    public record Sku(Long id, String name, String title, long price, int stock) {
    }

    public record Spec(String name, String value) {
    }

    public record Warranty(int months, int motorMonths, String desc) {
    }

    public record Admin(
            Long id,
            String name,
            String brand,
            String category,
            Integer categoryId,
            String model,
            String subtitle,
            String coverImage,
            String tags,
            String status,
            long price,
            int stock,
            String createdAt,
            String updatedAt,
            List<AdminSku> skus
    ) {
    }

    /**
     * costPrice 只有老板能看到。其他角色这个字段是空的，响应里不会出现。
     * minStock 为空表示用全店默认最低库存。
     */
    public record AdminSku(Long id, String title, long price, int stock, Long costPrice, Integer minStock) {
    }

    public record Category(int id, String name, long count) {
    }
}
