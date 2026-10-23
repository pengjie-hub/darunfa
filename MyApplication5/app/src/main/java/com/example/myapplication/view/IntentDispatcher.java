package com.example.myapplication.view;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.example.myapplication.activity.ProductDetailsActivity;

/**
 * 内部跳转工具：让 {@link AttachmentCardBinder} 等 view 内部组件跳详情页时不用 import 业务 Activity。
 * 避免循环依赖 view ⇄ activity。
 */
final class IntentDispatcher {

    private IntentDispatcher() {}

    /**
     * 打开商品详情页。使用 {@link ProductDetailsActivity#EXTRA_PRODUCT_ID} 走 ID 通路。
     *
     * @param ctx 任意 context；优先 ApplicationContext；会自动 new task
     * @param pid 商品 ID（Long；与后端主键一致）
     */
    static void openProductDetail(@NonNull Context ctx, long pid) {
        if (pid <= 0) return;
        Intent i = new Intent(ctx, ProductDetailsActivity.class);
        i.putExtra(ProductDetailsActivity.EXTRA_PRODUCT_ID, pid);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}
