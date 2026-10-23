package com.example.myapplication.remote.dto;

/**
 * 新增 / 编辑收货地址请求（对应后端 {@code AddressRequest}）。
 * <p>
 * 注意字段名必须是 {@code isDefault}：后端是 {@code getIsDefault()/setIsDefault()}
 * （不是 {@code isDefault()}），Jackson 的属性名就是 {@code isDefault}。
 * 写成 {@code default} 会被后端忽略，导致「勾了设为默认但没生效」。
 */
public class AddressRequest {

    public String receiverName;
    public String receiverPhone;
    public String region;
    public String detail;
    public Boolean isDefault;

    public AddressRequest() {
    }

    public AddressRequest(String receiverName, String receiverPhone,
                          String region, String detail, Boolean isDefault) {
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.region = region;
        this.detail = detail;
        this.isDefault = isDefault;
    }
}
