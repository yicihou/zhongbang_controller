package com.zhongbang.controller.pojo.product.dto;

import java.util.List;

/**
 * 管理端维护商品。价格单位是分。coverImage 是图片地址，不是上传文件。
 */
public final class ProductRequests {

    private ProductRequests() {
    }

    public static class Save {
        private String name;
        private String brand;
        private String category;
        private Integer categoryId;
        private String model;
        private String subtitle;
        private String coverImage;
        private String tags;
        private String status;
        private List<Sku> skus;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public Integer getCategoryId() { return categoryId; }
        public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
        public String getCoverImage() { return coverImage; }
        public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<Sku> getSkus() { return skus; }
        public void setSkus(List<Sku> skus) { this.skus = skus; }
    }

    public static class Sku {
        private Long id;
        private String title;
        private Long price;
        private Integer stock;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Long getPrice() { return price; }
        public void setPrice(Long price) { this.price = price; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
    }

    /** 改某一条规格的单价。不传 skuId 时改这个商品的第一条规格。 */
    public static class Price {
        private Long skuId;
        private Long price;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Long getPrice() { return price; }
        public void setPrice(Long price) { this.price = price; }
    }
}
