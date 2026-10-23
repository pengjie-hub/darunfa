package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 编辑收货地址请求。
 * <p>
 * 字段与 {@code addresses} 表的映射：
 * <ul>
 *   <li>{@code region}（所在地区）→ {@code province} 列，可选，如「广东省深圳市南山区」；</li>
 *   <li>{@code detail}（详细地址）→ {@code detail} 列，必填，如「中山路 1 号 2 栋 301」。</li>
 * </ul>
 * {@code city} / {@code district} 两列保留不用 —— 订单侧只需要一个地址串
 * （{@code orders.receiver_address}），拆得太细反而让用户多填两格。
 */
public class AddressRequest {

    @NotBlank(message = "收货人不能为空")
    @Size(max = 32, message = "收货人姓名过长")
    private String receiverName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^\\d{11}$", message = "手机号必须为 11 位数字")
    private String receiverPhone;

    /** 所在地区（可选）。 */
    @Size(max = 32, message = "地区过长")
    private String region;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 200, message = "详细地址过长")
    private String detail;

    /** 是否设为默认地址（true 时会把该用户其它地址的默认标记清掉）。 */
    private Boolean isDefault;

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
}
