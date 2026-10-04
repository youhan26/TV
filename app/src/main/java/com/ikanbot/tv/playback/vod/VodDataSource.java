package com.ikanbot.tv.playback.vod;

import com.ikanbot.tv.bean.Site;

import java.util.List;

public interface VodDataSource {

    void detailContent(String key, String id);

    void playerContent(VodPlayRequest request);

    void preloadContent(VodPlayRequest request);

    void searchContent(List<Site> sites, String keyword, boolean quick);
}
