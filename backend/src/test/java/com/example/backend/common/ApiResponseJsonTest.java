package com.example.backend.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApiResponse} 的序列化兼容性测试：本次新增 {@code subCode} 不能污染既有响应体。
 *
 * <h3>为什么这条必须测</h3>
 * {@code subCode} 是纯增量字段，但它一旦出现在<b>所有</b>既有响应里（哪怕是 {@code null}），
 * 前台商城 App 与后台 8 个模块的响应体就都变了。同时 {@code @JsonInclude(NON_NULL)} 如果加得太粗暴，
 * 会把 {@code "data":null} 也一起抹掉 —— 那同样是破坏。两条都必须用断言钉住。
 */
class ApiResponseJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("正常响应（code=0）不出现 subCode，且 data:null 仍在（既有响应逐字节不变）")
    void successResponseHasNoSubCode() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.ok());
        assertFalse(json.contains("subCode"), "成功响应不应出现 subCode: " + json);
        assertTrue(json.contains("\"code\":0"), json);
        assertTrue(json.contains("\"message\":\"ok\""), json);
        assertTrue(json.contains("\"data\":null"), "data:null 必须保留（既有响应体不变）: " + json);
    }

    @Test
    @DisplayName("带数据的成功响应：只有 code/message/data 三个字段")
    void successWithData() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.ok(Map.of("answer", "你好")));
        assertFalse(json.contains("subCode"), json);
        assertTrue(json.contains("\"answer\":\"你好\""), json);
    }

    @Test
    @DisplayName("新错误码：subCode 必须出现")
    void errorWithSubCode() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.error(409, "DUPLICATE_ACTION", "该操作已执行过"));
        assertTrue(json.contains("\"subCode\":\"DUPLICATE_ACTION\""), json);
        assertTrue(json.contains("\"code\":409"), json);
        assertTrue(json.contains("\"message\":\"该操作已执行过\""), json);
        assertTrue(json.contains("\"data\":null"), json);
    }

    @Test
    @DisplayName("历史两参错误响应：文案不变，且不出现 subCode")
    void legacyErrorUnchanged() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.error(400, "库存不足，无法扣减"));
        assertFalse(json.contains("subCode"), json);
        assertTrue(json.contains("\"message\":\"库存不足，无法扣减\""), json);
    }

    @Test
    @DisplayName("BusinessException：两参构造 subCode 为 null，三参构造带细化码")
    void businessExceptionCompatibility() {
        BusinessException legacy = new BusinessException(400, "库存不足，无法扣减");
        assertTrue(legacy.getSubCode() == null);
        assertTrue(legacy.getMessage().equals("库存不足，无法扣减"));

        BusinessException refined = new BusinessException(409, "DUPLICATE_ACTION", "该操作已执行过");
        assertTrue("DUPLICATE_ACTION".equals(refined.getSubCode()));
        assertTrue(409 == refined.getCode());
    }
}
