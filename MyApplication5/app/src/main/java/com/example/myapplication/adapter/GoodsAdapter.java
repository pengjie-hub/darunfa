package com.example.myapplication.adapter;



import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.entity.Goods;
import com.example.myapplication.util.PriceUtil;

import java.util.List;

public class GoodsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /**
     * 头部 item：曾用于把首页的分类网格 / 横向横幅 / 促销板块作为列表第 0 项。
     * <p>
     * <b>2026-09-14 起首页已不再使用这个能力</b>：共享头部被上移到
     * {@code fragment_home.xml} 里、位于 TabLayout 之上（切分类时保持不变），
     * 不再挂在商品列表上。这里先留着以便将来复用，{@code headerView} 恒为 null 时
     * 行为与普通适配器完全一致。
     */
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_GOODS = 1;

    private List<Goods> goodsList;
    private OnItemClickListener mListener;
    private OnAddToCartClickListener mAddToCartListener;

    /** 列表头部视图（为 null 时列表只有商品） */
    private View headerView;

    /** 搜索结果高亮关键词（null 或空表示不高亮，默认不影响其它场景复用） */
    private String highlightKeyword;

    public interface OnItemClickListener {
        void onItemClick(Goods goods);
    }
    
    public interface OnAddToCartClickListener {
        void onAddToCartClick(Goods goods);
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }
    
    public void setOnAddToCartClickListener(OnAddToCartClickListener listener) {
        mAddToCartListener = listener;
    }

    /** 设置标题高亮关键词（用于搜索结果页），传 null 或空串则不高亮 */
    public void setHighlightKeyword(String keyword) {
        this.highlightKeyword = keyword;
    }

    public GoodsAdapter(List<Goods> goodsList) {
        this.goodsList = goodsList;
    }

    /**
     * 设置列表头部视图（首页的分类网格 / 横向横幅 / 促销板块）。
     * <p>头部作为第 0 个 item 占满整行（配合 {@code spanSizeLookup} 返回 2），
     * 商品数据仍保持原有的两列瀑布流排列。
     *
     * @param headerView 已在外部完成数据绑定的视图；传 null 表示移除头部
     */
    public void setHeaderView(View headerView) {
        this.headerView = headerView;
        notifyDataSetChanged();
    }

    /** 是否已设置头部（布局管理器据此判断第 0 位是否为整行元素）。 */
    public boolean hasHeader() {
        return headerView != null;
    }

    /** position → 商品数据下标（有头部时整体后移一位）；越界返回 -1。 */
    private int toDataPosition(int position) {
        int index = hasHeader() ? position - 1 : position;
        int size = goodsList == null ? 0 : goodsList.size();
        return (index < 0 || index >= size) ? -1 : index;
    }

    /** 头部 item 与商品 item 走不同的 viewType，避免复用时互相错用布局。 */
    @Override
    public int getItemViewType(int position) {
        return (hasHeader() && position == 0) ? TYPE_HEADER : TYPE_GOODS;
    }

    // 创建 ViewHolder 并加载布局
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            // RecyclerView 要求 itemView 不能已挂在别的父容器上，否则会抛
            // "The specified child already has a parent"
            ViewGroup oldParent = (ViewGroup) headerView.getParent();
            if (oldParent != null) {
                oldParent.removeView(headerView);
            }
            return new HeaderViewHolder(headerView);
        }
        // 加载 item 布局（R.layout.item_goods 需与项目中实际布局文件对应）
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_goods, parent, false);
        return new GoodsViewHolder(view);
    }

    // 绑定数据到 ViewHolder：根据 position 取出数据，设置到控件
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            // 头部是外部自管理的视图（数据已在绑定前填充），此处无需处理
            return;
        }

        GoodsViewHolder itemHolder = (GoodsViewHolder) holder;
        int index = toDataPosition(position);
        if (index < 0) return;
        Goods goods = goodsList.get(index);

        // 设置商品图片、标题、价格、标签等
        itemHolder.ivGoods.setImageResource(goods.getImg());
        itemHolder.tvTitle.setText(highlightTitle(goods.getTitle(), itemHolder.itemView.getContext()));
        bindPrice(itemHolder, goods);
        itemHolder.tvTag.setText(goods.getTag());

        itemHolder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onItemClick(goods); // 触发监听器回调
            }
        });
        
        // 为购物车按钮添加点击事件
        itemHolder.btnAddToCart.setOnClickListener(v -> {
            if (mAddToCartListener != null) {
                mAddToCartListener.onAddToCartClick(goods); // 触发添加到购物车回调
            }
        });
    }

    /**
     * 绑定价格区。
     * <p>现价：先剥离数据里可能自带的货币符号（本地库 "29.9/斤" 无符号、后端 "¥9.90" 有符号），
     * 由布局中的 "¥" 统一提供符号，避免出现 "¥¥9.90"。
     * <p>优惠：原价以划线灰色展示，并附带 N.N折标签；无优惠（原价 ≤ 现价）时隐藏整行。
     */
    private void bindPrice(@NonNull GoodsViewHolder holder, Goods goods) {
        String rawPrice = goods.getPrice();
        String suffix = PriceUtil.extractSuffix(rawPrice);
        holder.tvPrice.setText(PriceUtil.stripSymbol(rawPrice));

        double priceValue = goods.getPriceValue();
        double originalPrice = goods.getOriginalPrice();
        if (PriceUtil.hasDiscount(priceValue, originalPrice)) {
            holder.llDiscount.setVisibility(View.VISIBLE);
            holder.tvOriginalPrice.setText(
                    PriceUtil.CURRENCY + PriceUtil.formatAmount(originalPrice) + suffix);
            holder.tvOriginalPrice.setPaintFlags(
                    holder.tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            String discount = PriceUtil.formatDiscount(priceValue, originalPrice);
            holder.tvDiscount.setText(discount == null ? "" : discount);
            holder.tvDiscount.setVisibility(discount == null ? View.GONE : View.VISIBLE);
        } else {
            holder.llDiscount.setVisibility(View.GONE);
        }
    }

    /** 标题关键词高亮（忽略大小写，命中处标红） */
    private CharSequence highlightTitle(String title, android.content.Context context) {
        if (title == null) return "";
        if (highlightKeyword == null || highlightKeyword.isEmpty()) {
            return title;
        }
        String lowerTitle = title.toLowerCase();
        String lowerKw = highlightKeyword.toLowerCase();
        int index = lowerTitle.indexOf(lowerKw);
        if (index < 0) {
            return title;
        }
        SpannableString spannable = new SpannableString(title);
        while (index >= 0) {
            int end = index + lowerKw.length();
            spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.rt_primary)),
                    index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            index = lowerTitle.indexOf(lowerKw, end);
        }
        return spannable;
    }


    // 返回 item 总数（商品数 + 可能存在的头部）
    @Override
    public int getItemCount() {
        int count = goodsList == null ? 0 : goodsList.size();
        return count + (hasHeader() ? 1 : 0);
    }

    /** 头部 item 的 ViewHolder：视图由外部提供，不做数据绑定。 */
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    // 静态内部类：ViewHolder 用于缓存 Item 控件
    static class GoodsViewHolder extends RecyclerView.ViewHolder {
        ImageView ivGoods;   // 商品图片控件
        TextView tvTitle;    // 商品标题控件
        TextView tvPrice;    // 商品价格控件
        TextView tvTag;      // 商品标签控件
        TextView tvOriginalPrice; // 原价（划线）
        TextView tvDiscount;      // 折扣标签（如 7.7折）
        View llDiscount;          // 原价 + 折扣 所在行容器
        androidx.appcompat.widget.AppCompatButton btnAddToCart; // 购物车按钮控件

        public GoodsViewHolder(@NonNull View itemView) {
            super(itemView);
            // 初始化控件（R.id.xxx 需与 item_goods 布局中控件 ID 对应）
            ivGoods = itemView.findViewById(R.id.iv_goods);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvPrice = itemView.findViewById(R.id.tv_price);
            tvTag = itemView.findViewById(R.id.tv_tag);
            tvOriginalPrice = itemView.findViewById(R.id.tv_original_price);
            tvDiscount = itemView.findViewById(R.id.tv_discount);
            llDiscount = itemView.findViewById(R.id.ll_discount);
            btnAddToCart = itemView.findViewById(R.id.btn_add_to_cart);
        }
    }
}
