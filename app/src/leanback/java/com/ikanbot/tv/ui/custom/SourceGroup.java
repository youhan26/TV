package com.ikanbot.tv.ui.custom;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;

import com.ikanbot.tv.bean.Episode;
import com.ikanbot.tv.bean.Flag;
import com.ikanbot.tv.bean.Vod;
import com.ikanbot.tv.databinding.ItemSourceBinding;
import com.ikanbot.tv.ui.adapter.EpisodeAdapter;
import com.ikanbot.tv.ui.adapter.FlagAdapter;
import com.ikanbot.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class SourceGroup {

    private final ItemSourceBinding binding;
    private final FlagAdapter flagAdapter;
    private final EpisodeAdapter episodeAdapter;
    private final Listener listener;
    private List<Flag> flags;
    private Flag flag;
    private String key;
    private Vod vod;

    public SourceGroup(@NonNull ViewGroup parent, @NonNull Listener listener) {
        binding = ItemSourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        this.listener = listener;
        this.flags = new ArrayList<>();
        flagAdapter = new FlagAdapter(this::onFlagClick);
        episodeAdapter = new EpisodeAdapter(item -> listener.onEpisodeClick(this, flag, item));
        binding.flag.setAdapter(flagAdapter);
        binding.episode.setAdapter(episodeAdapter);
        binding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        binding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        binding.episode.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (flagAdapter.getItemCount() > 0) onFlagClick(flagAdapter.get(position));
            }
        });
    }

    private void onFlagClick(Flag item) {
        binding.flag.post(() -> {
            flag = item;
            selectFlag(item);
            renderEpisodes(item.getEpisodes());
        });
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public void setVod(Vod vod) {
        this.vod = vod;
    }

    public Vod getVod() {
        return vod;
    }

    public void setTitle(String title) {
        binding.title.setText(title);
    }

    public View getView() {
        return binding.getRoot();
    }

    public FlagAdapter getFlagAdapter() {
        return flagAdapter;
    }

    public EpisodeAdapter getEpisodeAdapter() {
        return episodeAdapter;
    }

    public void renderFlags(List<Flag> items) {
        flags = items == null ? new ArrayList<>() : items;
        binding.flag.setVisibility(flags.isEmpty() ? View.GONE : View.VISIBLE);
        flagAdapter.addAll(flags);
        if (!flags.isEmpty()) onFlagClick(flags.get(0));
    }

    public void renderEpisodes(List<Episode> items) {
        List<Episode> episodes = items == null ? new ArrayList<>() : items;
        binding.episode.setVisibility(episodes.isEmpty() ? View.GONE : View.VISIBLE);
        episodeAdapter.addAll(episodes);
    }

    public void selectFlag(Flag item) {
        for (Flag f : flags) f.setSelected(item);
        binding.flag.post(() -> {
            flagAdapter.notifyDataSetChanged();
            binding.flag.setSelectedPosition(flagAdapter.indexOf(item));
        });
    }

    public void selectEpisode(Episode item) {
        binding.episode.post(() -> {
            episodeAdapter.notifyDataSetChanged();
            binding.episode.setSelectedPosition(episodeAdapter.getPosition());
        });
    }

    public interface Listener {

        void onEpisodeClick(SourceGroup group, Flag flag, Episode episode);
    }
}
