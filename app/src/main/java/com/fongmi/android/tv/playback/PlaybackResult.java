package com.ikanbot.tv.playback;

import com.ikanbot.tv.bean.Result;

public record PlaybackResult<T>(T request, Result result) {
}
