package com.example.myapplication.data;

import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.dto.CsConversationDTO;
import com.example.myapplication.remote.dto.CsMessageDTO;
import com.example.myapplication.remote.dto.CsSendResponse;

import java.util.List;

/**
 * 在线客服数据访问接口。
 * <p>
 * 职责边界：只负责「客户与客服会话」的读写 —— 取会话、拉消息、发消息（服务端自动回复）、结束会话。
 * 服务端每次发消息都会调用大模型生成自动回复，因此这里是唯一会「写 + 等模型」的仓储。
 * 不涉及订单、商品、用户资料。
 */
public interface CsRepository {

    /** 取（或创建）当前用户的客服会话。 */
    void session(ApiCallback<CsConversationDTO> cb);

    /** 会话的完整消息（时间正序）。 */
    void messages(ApiCallback<List<CsMessageDTO>> cb);

    /** 发送一条客户消息，回调里带着服务端生成的回复。 */
    void send(String content, ApiCallback<CsSendResponse> cb);

    /** 结束当前会话（下次进入会重新激活）。 */
    void close(ApiCallback<Boolean> cb);
}
