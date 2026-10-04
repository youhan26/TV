package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.impl.Diffable;
import com.fongmi.android.tv.utils.ResUtil;

public class Func implements Diffable<Func> {

    private final int resId;
    private int drawable;
    private String text;
    private String typeId;

    public static Func create(int resId) {
        return new Func(resId);
    }

    public static Func create(String typeId, String text) {
        Func func = new Func(R.string.home_class);
        func.typeId = typeId;
        func.text = text;
        func.drawable = R.drawable.ic_home_vod;
        return func;
    }

    public Func(int resId) {
        this.resId = resId;
        this.setDrawable();
    }

    public int getResId() {
        return resId;
    }

    public int getDrawable() {
        return drawable;
    }

    public String getText() {
        return TextUtils.isEmpty(text) ? ResUtil.getString(resId) : text;
    }

    public String getTypeId() {
        return TextUtils.isEmpty(typeId) ? "" : typeId;
    }

    public boolean isClass() {
        return !getTypeId().isEmpty();
    }

    public void setDrawable() {
        if (resId == R.string.home_home) this.drawable = R.drawable.ic_logo;
        else if (resId == R.string.home_vod) this.drawable = R.drawable.ic_home_vod;
        else if (resId == R.string.home_live) this.drawable = R.drawable.ic_home_live;
        else if (resId == R.string.home_keep) this.drawable = R.drawable.ic_home_keep;
        else if (resId == R.string.home_push) this.drawable = R.drawable.ic_home_push;
        else if (resId == R.string.home_search) this.drawable = R.drawable.ic_home_search;
        else if (resId == R.string.home_setting) this.drawable = R.drawable.ic_home_setting;
        else if (resId == R.string.home_class) this.drawable = R.drawable.ic_home_vod;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Func it)) return false;
        return getResId() == it.getResId() && getTypeId().equals(it.getTypeId());
    }

    @Override
    public boolean isSameItem(Func other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Func other) {
        return equals(other);
    }
}
