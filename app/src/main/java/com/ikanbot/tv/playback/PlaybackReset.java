package com.ikanbot.tv.playback;

import com.ikanbot.tv.bean.Track;
import com.ikanbot.tv.player.PlayerManager;

public final class PlaybackReset {

    public static void afterError(PlayerManager player) {
        afterError(player, null);
    }

    public static void afterError(PlayerManager player, Runnable beforeReset) {
        if (beforeReset != null) beforeReset.run();
        Track.delete(player.getKey());
        player.resetTrack();
        player.reset();
        player.stop();
    }
}
