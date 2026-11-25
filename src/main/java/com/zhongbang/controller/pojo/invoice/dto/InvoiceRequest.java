package com.zhongbang.controller.pojo.invoice.dto;

/** 给订单或报修单打上开票标记。税率是百分数乘 100，13% 传 1300。不传税率也可以。 */
public class InvoiceRequest {

    private Boolean invoiced;
    private String invoiceType;
    private Integer taxRate;
    private Boolean taxIncluded;

    public Boolean getInvoiced() { return invoiced; }
    public void setInvoiced(Boolean invoiced) { this.invoiced = invoiced; }
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
    public Integer getTaxRate() { return taxRate; }
    public void setTaxRate(Integer taxRate) { this.taxRate = taxRate; }
    public Boolean getTaxIncluded() { return taxIncluded; }
    public void setTaxIncluded(Boolean taxIncluded) { this.taxIncluded = taxIncluded; }
}
