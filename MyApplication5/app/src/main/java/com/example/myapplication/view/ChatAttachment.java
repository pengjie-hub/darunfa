package com.example.myapplication.view;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天消息的"附件"——即后端 assistant 在回复里夹带的结构化数据。
 * <p>
 * 支持 4 种类型（与后端 {@code AssistantChatService#mapAttachmentType} 一一对应）：
 * <ul>
 *   <li>{@link Type#PRODUCTS} — 找商品结果。data 是 {@code {count, products:[{id,name,subtitle,price,originalPrice,stock,sales,unit,image}]}}</li>
 *   <li>{@link Type#COMPARISON} — 比参数结果。data 是 {@code {headers:[{id,name,image}], rows:[{label,values:[]}]}}</li>
 *   <li>{@link Type#LOGISTICS} — 查物流结果。data 是订单+物流对象</li>
 *   <li>{@link Type#ORDER_PREVIEW} — 准备下单结果。data 是总价+商品列表+默认地址</li>
 * </ul>
 * <p>
 * 不同类型的 {@link #data} 是任意 JSON 结构。{@link ChatAssistantView} 在解析时按 type 路由到对应卡片渲染。
 */
public final class ChatAttachment {

    public enum Type {
        PRODUCTS("products"),
        COMPARISON("comparison"),
        LOGISTICS("logistics"),
        ORDER_PREVIEW("order_preview");

        public final String wire;
        Type(String wire) { this.wire = wire; }

        public static Type fromWire(String s) {
            if (s == null) return null;
            for (Type t : values()) if (t.wire.equalsIgnoreCase(s)) return t;
            return null;
        }
    }

    @NonNull public final Type type;
    @NonNull public final JSONObject data;

    public ChatAttachment(@NonNull Type type, @NonNull JSONObject data) {
        this.type = type;
        this.data = data;
    }

    /** 后端响应里的原始一条附件 → 解析为 ChatAttachment；解析失败返回 null。 */
    @Nullable
    public static ChatAttachment parse(String typeStr, JSONObject data) {
        Type t = Type.fromWire(typeStr);
        if (t == null || data == null) return null;
        return new ChatAttachment(t, data);
    }

    /** 解 list（空集合安全）。 */
    @NonNull
    public static List<ChatAttachment> emptyList() {
        return Collections.emptyList();
    }

    /** 解 list（拷贝一份，避免外部改我们的内部 List）。 */
    @NonNull
    public static List<ChatAttachment> copyOf(@Nullable List<ChatAttachment> src) {
        if (src == null || src.isEmpty()) return emptyList();
        return new ArrayList<>(src);
    }
}
