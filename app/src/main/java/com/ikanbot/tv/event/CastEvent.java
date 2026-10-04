package com.ikanbot.tv.event;

import com.ikanbot.tv.bean.Config;
import com.ikanbot.tv.bean.Device;
import com.ikanbot.tv.bean.History;

import org.greenrobot.eventbus.EventBus;

public record CastEvent(Config config, Device device, History history) {

    public static void post(Config config, Device device, History history) {
        EventBus.getDefault().post(new CastEvent(config, device, history));
    }
}
