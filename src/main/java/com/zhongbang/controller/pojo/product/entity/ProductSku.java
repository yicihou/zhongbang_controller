package com.zhongbang.controller.pojo.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 商品规格。价格单位是分。下单时按这一行扣库存。
 */
@TableName("product_sku")
public class ProductSku {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long productId;
    private String title;
    private Long price;
    /** 当前进价，单位分。空表示还没设过，不能当成 0。 */
    private Long costPrice;
    /** 最低库存。空则用全店默认值。 */
    private Integer minStock;
    private Integer stock;
    private Integer locked;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getPrice() { return price; }
    public void setPrice(Long price) { this.price = price; }
    public Long getCostPrice() { return costPrice; }
    public void setCostPrice(Long costPrice) { this.costPrice = costPrice; }
    public Integer getMinStock() { return minStock; }
    public void setMinStock(Integer minStock) { this.minStock = minStock; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getLocked() { return locked; }
    public void setLocked(Integer locked) { this.locked = locked; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
