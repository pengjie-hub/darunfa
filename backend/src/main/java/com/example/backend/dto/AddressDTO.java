package com.example.backend.dto;

import com.example.backend.entity.Address;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 收货地址展示 DTO。
 */
public class AddressDTO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Long id;
    private String receiverName;
    private String receiverPhone;
    /** 所在地区（可能为空）。 */
    private String region;
    /** 详细地址。 */
    private String detail;
    /** 地区 + 详细地址拼好的完整地址串（下单时直接用作 receiverAddress）。 */
    private String fullAddress;
    private Boolean isDefault;
    private String createdAt;

    public static AddressDTO from(Address a) {
        AddressDTO d = new AddressDTO();
        d.id = a.getId();
        d.receiverName = a.getReceiverName();
        d.receiverPhone = a.getReceiverPhone();
        d.region = a.getProvince();
        d.detail = a.getDetail();
        d.fullAddress = join(a.getProvince(), a.getDetail());
        d.isDefault = a.getIsDefault() != null && a.getIsDefault();
        LocalDateTime t = a.getCreatedAt();
        d.createdAt = t == null ? null : t.format(FMT);
        return d;
    }

    /** 「地区 详细地址」，地区为空时不留下前导空格。 */
    public static String join(String region, String detail) {
        String r = region == null ? "" : region.trim();
        String de = detail == null ? "" : detail.trim();
        if (r.isEmpty()) return de;
        if (de.isEmpty()) return r;
        return r + " " + de;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getFullAddress() { return fullAddress; }
    public void setFullAddress(String fullAddress) { this.fullAddress = fullAddress; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
