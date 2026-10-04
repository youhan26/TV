package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

/**
 * 进度条视图：media3 1.4.0 起移除了 {@code androidx.media3.ui.PlayerSeekView}，
 * 这里用 {@link DefaultTimeBar} 复刻其行为（绑定播放器、显示进度、拖动 seek）。
 */
public class PlayerSeekView extends FrameLayout {

    private final DefaultTimeBar timeBar;
    private final ComponentListener componentListener;
    @Nullable
    private Player player;

    public PlayerSeekView(Context context) {
        this(context, null);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        timeBar = new DefaultTimeBar(context);
        timeBar.setId(androidx.media3.ui.R.id.exo_progress);
        componentListener = new ComponentListener();
        timeBar.addListener(componentListener);
        addView(timeBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setPlayer(@Nullable Player player) {
        if (this.player == player) return;
        if (this.player != null) this.player.removeListener(componentListener);
        this.player = player;
        if (player != null) player.addListener(componentListener);
        componentListener.update();
    }

    public TimeBar getTimeBar() {
        return timeBar;
    }

    private final class ComponentListener implements Player.Listener, TimeBar.OnScrubListener {

        @Override
        public void onScrubStart(@NonNull TimeBar timeBar, long position) {
        }

        @Override
        public void onScrubMove(@NonNull TimeBar timeBar, long position) {
            if (player != null) player.seekTo(position);
        }

        @Override
        public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
            if (player != null && !canceled) player.seekTo(position);
        }

        @Override
        public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_AVAILABLE_COMMANDS_CHANGED)) update();
        }

        private void update() {
            Player p = player;
            if (p == null) return;
            long duration = p.getDuration();
            boolean durationSet = duration != C.TIME_UNSET && duration > 0;
            long position = durationSet ? p.getCurrentPosition() : 0;
            timeBar.setDuration(durationSet ? duration : 0);
            timeBar.setPosition(position);
            timeBar.setBufferedPosition(p.getBufferedPosition());
        }
    }
}
