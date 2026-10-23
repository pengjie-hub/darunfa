package com.example.myapplication.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.adapter.AddressAdapter;
import com.example.myapplication.data.AddressRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.AddressDTO;
import com.example.myapplication.remote.dto.AddressRequest;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 收货地址管理页。
 * <p>
 * 一个用户的地址是「一组 + 至多一个默认」，所以增删改之后**一律整表重拉**，
 * 而不是在本地列表上打补丁 —— 因为「设默认」会连带把别人的 isDefault 清掉，
 * 「删除默认地址」会让后端自动顶一条上来，这两件事本地算不准，只能以服务端为准。
 * <p>
 * 页面是纯登录态功能：未登录（401）直接提示并退出，不做「匿名也能加地址」这种假象。
 */
public class AddressListActivity extends AppCompatActivity implements AddressAdapter.Listener {

    /** 手机号：后端是 {@code ^\d{11}$}，前端先拦一道，避免白跑一次网络。 */
    private static final Pattern PHONE = Pattern.compile("^\\d{11}$");

    private RecyclerView rvAddresses;
    private View stateEmpty;
    private View stateLoading;

    private AddressAdapter adapter;
    private AddressRepository addressRepository;

    /** 当前列表快照，用于判断「这是不是第一条地址」。 */
    private final List<AddressDTO> current = new ArrayList<>();

    private AlertDialog editDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_list);

        // 该页可作为独立任务入口（如通知/深链），保证网络层就绪早于任何请求
        RetrofitClient.init(getApplicationContext());
        addressRepository = RepositoryProvider.getInstance(this).address();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvAddresses = findViewById(R.id.rv_addresses);
        stateEmpty = findViewById(R.id.state_empty);
        stateLoading = findViewById(R.id.state_loading);

        adapter = new AddressAdapter();
        adapter.setListener(this);
        rvAddresses.setLayoutManager(new LinearLayoutManager(this));
        rvAddresses.setAdapter(adapter);

        findViewById(R.id.btn_add_address).setOnClickListener(v -> showEditDialog(null));

        loadAddresses();
    }

    // ==================== 列表加载 ====================

    private void loadAddresses() {
        showLoading();
        addressRepository.list(new ApiCallback<List<AddressDTO>>() {
            @Override
            public void onSuccess(List<AddressDTO> data) {
                current.clear();
                if (data != null) current.addAll(data);
                adapter.setData(data);
                if (current.isEmpty()) {
                    showEmpty();
                } else {
                    showList();
                }
            }

            @Override
            public void onError(int code, String message) {
                if (handleAuthError(code)) return;
                showEmpty();
                toast(message == null ? "地址加载失败" : message);
            }
        });
    }

    private void showLoading() {
        stateLoading.setVisibility(View.VISIBLE);
        stateEmpty.setVisibility(View.GONE);
        rvAddresses.setVisibility(View.GONE);
    }

    private void showList() {
        stateLoading.setVisibility(View.GONE);
        stateEmpty.setVisibility(View.GONE);
        rvAddresses.setVisibility(View.VISIBLE);
    }

    private void showEmpty() {
        stateLoading.setVisibility(View.GONE);
        rvAddresses.setVisibility(View.GONE);
        stateEmpty.setVisibility(View.VISIBLE);
    }

    // ==================== 新增 / 编辑 ====================

    /**
     * 弹出新增/编辑弹窗。
     *
     * @param existing 传 null 表示新增
     */
    private void showEditDialog(@Nullable final AddressDTO existing) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_address_edit, null);

        final TextInputEditText etName = content.findViewById(R.id.et_name);
        final TextInputEditText etPhone = content.findViewById(R.id.et_phone);
        final TextInputEditText etRegion = content.findViewById(R.id.et_region);
        final TextInputEditText etDetail = content.findViewById(R.id.et_detail);
        final CheckBox cbDefault = content.findViewById(R.id.cb_default);

        if (existing != null) {
            etName.setText(existing.receiverName);
            etPhone.setText(existing.receiverPhone);
            etRegion.setText(existing.region);
            etDetail.setText(existing.detail);
            cbDefault.setChecked(existing.isDefault);
        } else if (current.isEmpty()) {
            // 第一条地址一定是默认地址，先替用户勾上，省一次往返
            cbDefault.setChecked(true);
        }

        editDialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "新增收货地址" : "编辑收货地址")
                .setView(content)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();

        // 校验不通过时不能关弹窗，所以不用框架的自动 dismiss，自己接管点击
        editDialog.setOnShowListener(d -> editDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = text(etName);
                    String phone = text(etPhone);
                    String region = text(etRegion);
                    String detail = text(etDetail);

                    if (TextUtils.isEmpty(name)) {
                        toast("请填写收货人姓名");
                        return;
                    }
                    if (!PHONE.matcher(phone).matches()) {
                        toast("请填写 11 位手机号");
                        return;
                    }
                    if (TextUtils.isEmpty(detail)) {
                        toast("请填写详细地址");
                        return;
                    }

                    AddressRequest req = new AddressRequest(
                            name, phone, region, detail, cbDefault.isChecked());
                    submit(existing, req);
                }));

        editDialog.show();
    }

    private void submit(@Nullable AddressDTO existing, AddressRequest req) {
        ApiCallback<AddressDTO> cb = new ApiCallback<AddressDTO>() {
            @Override
            public void onSuccess(AddressDTO data) {
                dismissEditDialog();
                toast(existing == null ? "地址已添加" : "地址已更新");
                loadAddresses();
            }

            @Override
            public void onError(int code, String message) {
                if (handleAuthError(code)) return;
                // 失败保留弹窗，用户填的内容不用重打一遍
                toast(message == null ? "保存失败" : message);
            }
        };
        if (existing == null) {
            addressRepository.create(req, cb);
        } else {
            addressRepository.update(existing.id, req, cb);
        }
    }

    private void dismissEditDialog() {
        if (editDialog != null && editDialog.isShowing()) {
            editDialog.dismiss();
        }
        editDialog = null;
    }

    // ==================== 设为默认 / 删除 ====================

    @Override
    public void onSetDefault(AddressDTO address) {
        addressRepository.setDefault(address.id, new ApiCallback<AddressDTO>() {
            @Override
            public void onSuccess(AddressDTO data) {
                toast("已设为默认地址");
                loadAddresses();
            }

            @Override
            public void onError(int code, String message) {
                if (handleAuthError(code)) return;
                toast(message == null ? "设置失败" : message);
            }
        });
    }

    @Override
    public void onEdit(AddressDTO address) {
        showEditDialog(address);
    }

    @Override
    public void onDelete(AddressDTO address) {
        new AlertDialog.Builder(this)
                .setTitle("删除地址")
                .setMessage("确定删除「" + safe(address.receiverName) + " " + safe(address.receiverPhone)
                        + "」这条地址吗？")
                .setPositiveButton("删除", (d, w) -> doDelete(address))
                .setNegativeButton("取消", null)
                .show();
    }

    private void doDelete(AddressDTO address) {
        addressRepository.delete(address.id, new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                toast("地址已删除");
                loadAddresses();
            }

            @Override
            public void onError(int code, String message) {
                if (handleAuthError(code)) return;
                toast(message == null ? "删除失败" : message);
            }
        });
    }

    // ==================== 工具 ====================

    /** 未登录（401）时提示并关页，返回 true 表示已处理、调用方不要再弹错误。 */
    private boolean handleAuthError(int code) {
        if (code == 401) {
            toast("请先登录");
            finish();
            return true;
        }
        return false;
    }

    private static String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
