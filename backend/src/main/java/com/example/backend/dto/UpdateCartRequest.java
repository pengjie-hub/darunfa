package com.example.backend.dto;

/**
 * 更新购物车项请求（数量 / 勾选状态，均可选）。
 */
public class UpdateCartRequest {

    private Integer quantity;
    private Boolean selected;

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Boolean getSelected() { return selected; }
    public void setSelected(Boolean selected) { this.selected = selected; }
}
