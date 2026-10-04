package com.ikanbot.tv.player.engine;

import androidx.media3.common.Player;

import com.ikanbot.tv.player.exo.ExoPlayerEngine;
import com.ikanbot.tv.player.media.PlaySpec;

public final class PlayerEngineFactory {

    public static PlayerEngine create(int decode, Player.Listener listener) {
        return new ExoPlayerEngine(decode, listener);
    }

    public static PlayerEngine create(int decode, PlaySpec spec, Player.Listener listener) {
        return create(decode, listener);
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return engine != null && !engine.needsRebuild();
    }
}
