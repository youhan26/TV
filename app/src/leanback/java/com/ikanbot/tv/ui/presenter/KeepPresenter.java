package com.ikanbot.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.ikanbot.tv.Product;
import com.ikanbot.tv.bean.Keep;
import com.ikanbot.tv.databinding.AdapterVodBinding;
import com.ikanbot.tv.utils.ImgUtil;
import com.ikanbot.tv.utils.ResUtil;

public class KeepPresenter extends Presenter {

    private final OnClickListener listener;
    private int width, height;

    public KeepPresenter(OnClickListener listener) {
        this.listener = listener;
        setLayoutSize();
    }

    public interface OnClickListener {

        void onItemClick(Keep item);
    }

    private void setLayoutSize() {
        int space = ResUtil.dp2px(48) + ResUtil.dp2px(16 * (Product.getColumn() - 1));
        int base = ResUtil.getScreenWidth() - space;
        width = base / Product.getColumn();
        height = (int) (width / 0.75f);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        ViewHolder holder = new ViewHolder(AdapterVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Keep item = (Keep) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.name.setText(item.getVodName());
        holder.binding.site.setText(item.getSiteName());
        holder.binding.site.setVisibility(View.VISIBLE);
        holder.binding.remark.setVisibility(View.GONE);
        holder.binding.delete.setVisibility(View.GONE);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterVodBinding binding;

        public ViewHolder(@NonNull AdapterVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
