package com.example.myapplication.fragment;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.activity.PayActivity;
import com.example.myapplication.adapter.CarListAdapter;
import com.example.myapplication.entity.CarInfo;
import com.example.myapplication.data.CartRepository;
import com.example.myapplication.data.CouponRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.dto.CheckoutPreviewDTO;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CarFragment extends Fragment {

    private static final String TAG = "CarFragment";
    private RecyclerView recyclerView;
    private TextView tvTotalPrice, tvEmptyCart, tvCartTitle, tvSelectedHint, tvEdit;
    private TextView tvPromoHint;
    private Button btnSettlement, btnGoShopping;
    private CheckBox cbSelectAll;
    private ImageView ivEmptyCart;
    private Toolbar toolbar;
    private View bottomLayout;
    private View emptyCarLayout;

    private List<CarInfo> carItemList;
    private CarListAdapter carListAdapter;
    private CartRepository carRepository;
    private CouponRepository couponRepository;
    private SharedPreferences mSharedPreferences;
    private int mUserId;

    /**
     * 试算请求序号：金额一变就作废在途请求的响应。
     * <p>
     * 加减数量会连点，若不做序号校验，先发的请求后返回就会用旧金额的提示盖掉新的。
     */
    private int previewSeq;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_car, container, false);

        mSharedPreferences = getActivity().getSharedPreferences("user", Context.MODE_PRIVATE);
        mUserId = mSharedPreferences.getInt("user_id", -1);

        initViews(view);
        initData();
        setListeners();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mUserId = mSharedPreferences.getInt("user_id", -1);
        loadData();
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.rv_car_items);
        tvTotalPrice = view.findViewById(R.id.tv_total_price);
        btnSettlement = view.findViewById(R.id.btn_settlement);
        cbSelectAll = view.findViewById(R.id.cb_select_all);
        tvEmptyCart = view.findViewById(R.id.tv_empty_cart);
        ivEmptyCart = view.findViewById(R.id.iv_empty_cart);
        btnGoShopping = view.findViewById(R.id.btn_go_shopping);
        toolbar = view.findViewById(R.id.toolbar);
        tvCartTitle = view.findViewById(R.id.tv_cart_title);
        tvSelectedHint = view.findViewById(R.id.tv_selected_hint);
        tvEdit = view.findViewById(R.id.tv_edit);
        tvPromoHint = view.findViewById(R.id.tv_promo_hint);
        bottomLayout = view.findViewById(R.id.bottom_layout);
        emptyCarLayout = view.findViewById(R.id.empty_car_layout);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
    }

    private void initData() {
        carItemList = new ArrayList<>();
        RepositoryProvider provider = RepositoryProvider.getInstance(getActivity());
        carRepository = provider.cart();
        couponRepository = provider.coupon();

        carListAdapter = new CarListAdapter(getActivity(), carItemList);
        recyclerView.setAdapter(carListAdapter);

        carListAdapter.setOnCarItemClickListener(new CarListAdapter.OnCarItemClickListener() {
            @Override
            public void onSelectChange(int position, boolean isSelected) {
                CarInfo carInfo = carItemList.get(position);
                carInfo.setSelected(isSelected);
                carRepository.updateProductSelected(carInfo.getProduct_id(), isSelected, new ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean data) {
                        updateTotalPrice();
                        checkSelectAllStatus();
                    }

                    @Override
                    public void onError(int code, String message) {
                        carInfo.setSelected(!isSelected);
                        carListAdapter.notifyItemChanged(position);
                        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onQuantityChange(int position, int quantity) {
                CarInfo carInfo = carItemList.get(position);
                int oldCount = carInfo.getProduct_count();
                if (quantity == oldCount) return;
                if (quantity < 1) {
                    carListAdapter.notifyItemChanged(position);
                    return;
                }
                carRepository.updateProductQuantity(carInfo.getProduct_id(), quantity, new ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean data) {
                        carInfo.setProduct_count(quantity);
                        carListAdapter.notifyItemChanged(position);
                        updateTotalPrice();
                    }

                    @Override
                    public void onError(int code, String message) {
                        carListAdapter.notifyItemChanged(position);
                        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onItemLongClick(int position) {
                showDeleteConfirmDialog(position);
            }
        });
    }

    private void setListeners() {
        cbSelectAll.setOnClickListener(v -> {
            boolean isSelected = cbSelectAll.isChecked();
            for (CarInfo carInfo : carItemList) {
                carInfo.setSelected(isSelected);
            }
            carListAdapter.notifyDataSetChanged();
            updateTotalPrice();
            carRepository.updateAllProductsSelected(isSelected, new ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean data) {
                }

                @Override
                public void onError(int code, String message) {
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnSettlement.setOnClickListener(v -> {
            List<CarInfo> selectedCars = getSelectedCars();
            if (selectedCars.isEmpty()) {
                Toast.makeText(getActivity(), "请选择要结算的商品", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Long> cartItemIds = new ArrayList<>();
            for (CarInfo c : selectedCars) {
                cartItemIds.add(c.getCartItemId());
            }
            String receiverName = mSharedPreferences.getString("username", "收货人");

            // 结算不再走本地弹窗：直接进收银台（下单模式）。
            // 收银台里补电话/地址、选支付方式，点「确认支付」才真正下单并支付。
            // 中途返回不会留下「待付款」垃圾订单（订单只在确认时创建）。
            PayActivity.startCheckout(requireContext(), cartItemIds,
                    calculateSelectedTotal(), receiverName);
        });

        btnGoShopping.setOnClickListener(v -> {
            if (getActivity() instanceof com.example.myapplication.activity.MainActivity) {
                ((com.example.myapplication.activity.MainActivity) getActivity()).switchToHome();
            }
        });
    }

    private void loadData() {
        carRepository.getAllCars(new ApiCallback<List<CarInfo>>() {
            @Override
            public void onSuccess(List<CarInfo> data) {
                carItemList.clear();
                if (data != null) carItemList.addAll(data);
                carListAdapter.updateData(carItemList);
                updateTotalPrice();
                checkSelectAllStatus();
                updateEmptyViewVisibility();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<CarInfo> getSelectedCars() {
        List<CarInfo> selected = new ArrayList<>();
        for (CarInfo c : carItemList) {
            if (c.isSelected()) selected.add(c);
        }
        return selected;
    }

    private double calculateSelectedTotal() {
        double total = 0;
        for (CarInfo c : carItemList) {
            if (c.isSelected()) total += c.getTotalPrice();
        }
        return total;
    }

    private void updateTotalPrice() {
        double totalPrice = calculateSelectedTotal();
        tvTotalPrice.setText(formatPrice(totalPrice));

        List<CarInfo> selectedCars = getSelectedCars();
        int selectedCount = selectedCars.size();
        int selectedQty = 0;
        for (CarInfo c : selectedCars) {
            selectedQty += c.getProduct_count();
        }

        btnSettlement.setText("结算(" + selectedCount + ")");
        tvSelectedHint.setText("已选 " + selectedQty + " 件");

        if (tvCartTitle != null) {
            int totalQty = 0;
            for (CarInfo c : carItemList) {
                totalQty += c.getProduct_count();
            }
            tvCartTitle.setText(totalQty > 0 ? "购物车(" + totalQty + ")" : "购物车");
        }

        refreshPromoHint();
    }

    /**
     * 向后端试算「这笔钱能减多少」，把结果显示在合计下方。
     * <p>
     * 为什么不自己在前端算满减：满减规则随时可能被运营改动，
     * 且下单时是后端按同一套算法再算一次 —— 前端自己算迟早会和订单对不上，
     * 于是干脆只负责显示后端给的结论。
     */
    private void refreshPromoHint() {
        if (tvPromoHint == null || couponRepository == null) return;

        double total = calculateSelectedTotal();
        if (mUserId == -1 || total <= 0) {
            tvPromoHint.setVisibility(View.GONE);
            return;
        }

        final int seq = ++previewSeq;
        couponRepository.preview(total, null, new ApiCallback<CheckoutPreviewDTO>() {
            @Override
            public void onSuccess(CheckoutPreviewDTO data) {
                // 过期响应直接丢弃，避免旧金额的提示盖掉新金额的
                if (seq != previewSeq || tvPromoHint == null) return;

                if (data == null) {
                    tvPromoHint.setVisibility(View.GONE);
                    return;
                }
                if (data.promotionDiscount > 0) {
                    String name = data.promotionName == null ? "" : data.promotionName;
                    tvPromoHint.setText(String.format(Locale.CHINA,
                            "已享「%s」立减 ¥%.2f，应付 ¥%.2f", name,
                            data.promotionDiscount, data.payAmount));
                    tvPromoHint.setVisibility(View.VISIBLE);
                } else if (data.hint != null && !data.hint.isEmpty()) {
                    tvPromoHint.setText(data.hint);
                    tvPromoHint.setVisibility(View.VISIBLE);
                } else {
                    tvPromoHint.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(int code, String message) {
                if (seq != previewSeq || tvPromoHint == null) return;
                // 提示行是锦上添花，失败就不显示，不打扰用户
                tvPromoHint.setVisibility(View.GONE);
            }
        });
    }

    private void checkSelectAllStatus() {
        boolean allSelected = !carItemList.isEmpty();
        for (CarInfo carInfo : carItemList) {
            if (!carInfo.isSelected()) {
                allSelected = false;
                break;
            }
        }
        cbSelectAll.setChecked(allSelected);
    }

    private void updateEmptyViewVisibility() {
        if (carItemList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            if (emptyCarLayout != null) emptyCarLayout.setVisibility(View.VISIBLE);
            if (bottomLayout != null) bottomLayout.setVisibility(View.GONE);
            if (tvCartTitle != null) tvCartTitle.setText("购物车");
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            if (emptyCarLayout != null) emptyCarLayout.setVisibility(View.GONE);
            if (bottomLayout != null) bottomLayout.setVisibility(View.VISIBLE);
        }
    }

    private String formatPrice(double price) {
        DecimalFormat df = new DecimalFormat("¥#0.00");
        return df.format(price);
    }

    private void showDeleteConfirmDialog(final int position) {
        final CarInfo carInfo = carItemList.get(position);

        new androidx.appcompat.app.AlertDialog.Builder(getActivity())
                .setTitle("删除确认")
                .setMessage("确定要删除\"" + carInfo.getProduct_title() + "\"吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    carRepository.removeCar(carInfo.getProduct_id(), new ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean data) {
                            carItemList.remove(position);
                            carListAdapter.notifyItemRemoved(position);
                            updateTotalPrice();
                            checkSelectAllStatus();
                            updateEmptyViewVisibility();
                            Toast.makeText(getActivity(), "商品已删除", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(int code, String message) {
                            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
