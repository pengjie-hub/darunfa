package com.example.myapplication.data;

import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiUtils;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.CsConversationDTO;
import com.example.myapplication.remote.dto.CsMessageDTO;
import com.example.myapplication.remote.dto.CsSendRequest;
import com.example.myapplication.remote.dto.CsSendResponse;

import java.util.List;

/**
 * 在线客服的远程实现（后端 {@code /api/cs/**}）。
 */
public class RemoteCsRepository implements CsRepository {

    @Override
    public void session(final ApiCallback<CsConversationDTO> cb) {
        ApiUtils.enqueue(RetrofitClient.api().csSession(), new ApiCallback<CsConversationDTO>() {
            @Override
            public void onSuccess(CsConversationDTO data) {
                cb.onSuccess(data);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void messages(final ApiCallback<List<CsMessageDTO>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().csMessages(), new ApiCallback<List<CsMessageDTO>>() {
            @Override
            public void onSuccess(List<CsMessageDTO> data) {
                cb.onSuccess(data);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    @Override
    public void send(String content, final ApiCallback<CsSendResponse> cb) {
        ApiUtils.enqueue(RetrofitClient.api().csSend(new CsSendRequest(content)),
                new ApiCallback<CsSendResponse>() {
                    @Override
                    public void onSuccess(CsSendResponse data) {
                        cb.onSuccess(data);
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    @Override
    public void close(final ApiCallback<Boolean> cb) {
        ApiUtils.enqueue(RetrofitClient.api().csClose(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                cb.onSuccess(true);
            }

            @Override
            public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }
}
