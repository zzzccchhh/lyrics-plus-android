package com.lyricsplus.android.spotify

object SpotifyBroadcasts {
    const val PACKAGE_NAME = "com.spotify.music"
    const val METADATA_CHANGED = "$PACKAGE_NAME.metadatachanged"
    const val PLAYBACK_STATE_CHANGED = "$PACKAGE_NAME.playbackstatechanged"
    const val QUEUE_CHANGED = "$PACKAGE_NAME.queuechanged"

    const val EXTRA_ID = "id"
    const val EXTRA_ARTIST = "artist"
    const val EXTRA_ALBUM = "album"
    const val EXTRA_TRACK = "track"
    const val EXTRA_LENGTH = "length"
    const val EXTRA_PLAYING = "playing"
    const val EXTRA_PLAYBACK_POSITION = "playbackPosition"
}
