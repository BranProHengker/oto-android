package com.mochyy.mochymusic.playback

enum class ShuffleMode {
    OFF,
    ON,
    SMART;

    fun next(): ShuffleMode = when (this) {
        OFF -> ON
        ON -> SMART
        SMART -> OFF
    }
}
