package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.remote.dto.AddressDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 收货地址列表适配器。
 * <p>
 * 三个操作入口都通过 {@link Listener} 回调出去，适配器自己不碰网络 ——
 * 这样「删除后要不要重拉列表」这类事务性逻辑集中在 Activity 里，不容易漏刷新。
 */
public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.VH> {

    public interface Listener {
        void onSetDefault(AddressDTO address);

        void onEdit(AddressDTO address);

        void onDelete(AddressDTO address);
    }

    private final List<AddressDTO> data = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<AddressDTO> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_address, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AddressDTO a = data.get(position);
        h.tvName.setText(a.receiverName == null ? "" : a.receiverName);
        h.tvPhone.setText(a.receiverPhone == null ? "" : a.receiverPhone);
        h.tvDetail.setText(a.fullAddress == null ? "" : a.fullAddress);

        h.tvDefault.setVisibility(a.isDefault ? View.VISIBLE : View.GONE);
        // 已经是默认地址时把「设为默认」置灰并改文案，避免用户反复点却没反馈
        if (a.isDefault) {
            h.tvSetDefault.setText("当前默认地址");
            h.tvSetDefault.setTextColor(0xFF9E9E9E);
            h.tvSetDefault.setEnabled(false);
        } else {
            h.tvSetDefault.setText("设为默认");
            h.tvSetDefault.setTextColor(h.itemView.getContext()
                    .getColor(R.color.rt_text_secondary));
            h.tvSetDefault.setEnabled(true);
        }

        h.tvSetDefault.setOnClickListener(v -> {
            if (listener != null && !a.isDefault) listener.onSetDefault(a);
        });
        h.tvEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(a);
        });
        h.tvDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(a);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvPhone;
        final TextView tvDetail;
        final TextView tvDefault;
        final TextView tvSetDefault;
        final TextView tvEdit;
        final TextView tvDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_addr_name);
            tvPhone = itemView.findViewById(R.id.tv_addr_phone);
            tvDetail = itemView.findViewById(R.id.tv_addr_detail);
            tvDefault = itemView.findViewById(R.id.tv_addr_default);
            tvSetDefault = itemView.findViewById(R.id.tv_addr_set_default);
            tvEdit = itemView.findViewById(R.id.tv_addr_edit);
            tvDelete = itemView.findViewById(R.id.tv_addr_delete);
        }
    }
}
