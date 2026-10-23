package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发货请求（物流公司与运单号）。
 */
public class ShipRequest {

    @NotBlank(message = "物流公司不能为空")
    private String company;

    @NotBlank(message = "运单号不能为空")
    private String trackingNo;

    private String remark;

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
