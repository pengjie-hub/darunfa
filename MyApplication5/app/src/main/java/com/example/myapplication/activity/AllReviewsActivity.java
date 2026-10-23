package com.example.myapplication.activity;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.adapter.ReviewAdapter;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.data.ReviewRepository;
import com.example.myapplication.entity.ReviewInfo;
import com.example.myapplication.remote.ApiCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 全部评论页面：从后端加载某商品的所有用户评论。
 */
public class AllReviewsActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "product_id";

    private ReviewRepository reviewRepository;
    private List<ReviewInfo> reviewList = new ArrayList<>();
    private ReviewAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_reviews);

        int productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
        reviewRepository = RepositoryProvider.getInstance(this).review();

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());

        RecyclerView rvAll = findViewById(R.id.rv_all_reviews);
        TextView tvAvgRating = findViewById(R.id.tv_avg_rating);
        TextView tvCount = findViewById(R.id.tv_review_count);
        TextView tvEmpty = findViewById(R.id.tv_empty);

        rvAll.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReviewAdapter(reviewList);
        rvAll.setAdapter(adapter);

        if (productId == -1) {
            tvEmpty.setVisibility(TextView.VISIBLE);
            return;
        }

        reviewRepository.getReviewsByProduct(productId, new ApiCallback<List<ReviewInfo>>() {
            @Override
            public void onSuccess(List<ReviewInfo> reviews) {
                reviewList.clear();
                if (reviews != null) reviewList.addAll(reviews);
                adapter.updateData(reviewList);

                int count = reviewList.size();
                double avg = 0;
                if (count > 0) {
                    double sum = 0;
                    for (ReviewInfo r : reviewList) sum += r.getRating();
                    avg = sum / count;
                }
                tvAvgRating.setText(String.format(Locale.getDefault(), "%.1f", avg));
                tvCount.setText(String.format("共%d条评论", count));
                tvEmpty.setVisibility(reviewList.isEmpty() ? TextView.VISIBLE : TextView.GONE);
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(AllReviewsActivity.this, message, Toast.LENGTH_SHORT).show();
                tvEmpty.setVisibility(TextView.VISIBLE);
            }
        });
    }
}
