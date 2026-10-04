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
    private final List<Flag> flags = new ArrayList<>();
    private List<Episode> episodes = new ArrayList<>();
    private Flag flag;
    private int flagPosition = -1;
    private int episodePosition = -1;
    private String key;
    private Vod vod;

    public SourceGroup(@NonNull ViewGroup parent, @NonNull Listener listener) {
        binding = ItemSourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        this.listener = listener;
        flagAdapter = new FlagAdapter(this::onFlagClick);
        episodeAdapter = new EpisodeAdapter(item -> listener.onEpisodeClick(this, flag, item));
        binding.flag.setAdapter(flagAdapter);
        binding.episode.setAdapter(episodeAdapter);
        binding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        binding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        binding.episode.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.flag.setId(View.generateViewId());
        binding.episode.setId(View.generateViewId());
        flagAdapter.setNextFocusDown(binding.episode.getId());
        episodeAdapter.setNextFocusUp(binding.flag.getId());
        binding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (position >= 0 && position < flags.size()) onFlagSelected(position);
            }
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

    public int getFlagId() {
        return binding.flag.getId();
    }

    public int getEpisodeId() {
        return binding.episode.getId();
    }

    public void setEpisodeNextFocusDown(int id) {
        episodeAdapter.setNextFocusDown(id);
    }

    public void renderFlags(List<Flag> items) {
        flags.clear();
        if (items != null) flags.addAll(items);
        flag = null;
        flagPosition = -1;
        episodePosition = -1;
        binding.flag.setVisibility(flags.isEmpty() ? View.GONE : View.VISIBLE);
        flagAdapter.addAll(flags);
        if (!flags.isEmpty()) selectFlagAt(0, flags.get(0));
    }

    public void renderEpisodes(List<Episode> items) {
        renderEpisodes(items, false);
    }

    public void renderEpisodes(List<Episode> items, boolean force) {
        List<Episode> next = items == null ? new ArrayList<>() : items;
        boolean changed = force || next != episodes;
        episodes = next;
        binding.episode.setVisibility(next.isEmpty() ? View.GONE : View.VISIBLE);
        if (changed) {
            episodePosition = -1;
            episodeAdapter.addAll(next);
        }
    }

    public void selectFlag(Flag item) {
        int position = flagAdapter.indexOf(item);
        if (position < 0) position = 0;
        if (position >= flags.size()) return;
        selectFlagAt(position, flags.get(position));
    }

    public void selectEpisode(Episode item) {
        int position = episodeAdapter.getPosition();
        if (position < 0 || position >= episodes.size()) return;
        int prev = episodePosition;
        episodePosition = position;
        if (prev >= 0 && prev != position && prev < episodes.size()) episodeAdapter.notifyItemChanged(prev);
        episodeAdapter.notifyItemChanged(position);
    }

    public void deselect() {
        for (Flag f : flags) f.getEpisodes().forEach(Episode::deselect);
        episodePosition = -1;
        if (episodeAdapter.getItemCount() > 0) episodeAdapter.notifyItemRangeChanged(0, episodeAdapter.getItemCount());
    }

    private void onFlagClick(Flag item) {
        onFlagSelected(flagAdapter.indexOf(item));
    }

    private void onFlagSelected(int position) {
        if (position < 0 || position >= flags.size()) return;
        binding.flag.post(() -> selectFlagAt(position, flags.get(position)));
    }

    private void selectFlagAt(int position, Flag item) {
        if (position < 0 || position >= flags.size()) return;
        if (item == flag) return;
        flag = item;
        for (Flag f : flags) f.setSelected(item);
        int prev = flagPosition;
        flagPosition = position;
        if (prev >= 0 && prev != position && prev < flags.size()) flagAdapter.notifyItemChanged(prev);
        flagAdapter.notifyItemChanged(position);
        renderEpisodes(item.getEpisodes());
    }

    public interface Listener {

        void onEpisodeClick(SourceGroup group, Flag flag, Episode episode);
    }
}
