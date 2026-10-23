package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder>{

    private List<Integer> mPicaList;

    public BannerAdapter(List<Integer> mPicaList) {
        this.mPicaList = mPicaList;
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
       View view= LayoutInflater.from(parent.getContext()).inflate(R.layout.item_banner,parent,false);
       return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        holder.viewpagerImg.setImageResource(mPicaList.get(position));
    }

    @Override
    public int getItemCount() {
        return mPicaList==null?0:mPicaList.size();
    }


    static class BannerViewHolder extends RecyclerView.ViewHolder{

        ImageView viewpagerImg;

        public BannerViewHolder(@NonNull View itemView){
            super(itemView);
            viewpagerImg=itemView.findViewById(R.id.banner_img);
        }

    }

}
