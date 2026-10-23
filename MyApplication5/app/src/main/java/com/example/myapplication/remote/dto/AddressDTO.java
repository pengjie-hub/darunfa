package com.example.myapplication.remote.dto;

/**
 * 收货地址 DTO（对应后端 {@code AddressDTO}）。
 */
public class AddressDTO {

    public long id;
    public String receiverName;
    public String receiverPhone;
    /** 所在地区（可能为空）。 */
    public String region;
    /** 详细地址。 */
    public String detail;
    /** 地区 + 详细地址拼好的完整地址串（下单直接用它当 receiverAddress）。 */
    public String fullAddress;
    public boolean isDefault;
    public String createdAt;

    /** 列表副标题：手机号 + 完整地址。 */
    public String subtitle() {
        String phone = receiverPhone == null ? "" : receiverPhone;
        String addr = fullAddress == null ? "" : fullAddress;
        if (phone.isEmpty()) return addr;
        return phone + "  " + addr;
    }
}
