package co.oto.music.eq

import co.oto.music.eq.audio.LoudnessParams
import co.oto.music.eq.audio.ReplayGainParams
import co.oto.music.eq.audio.ReverbParams
import co.oto.music.eq.audio.SpatialParams
import co.oto.music.eq.audio.VolumeCurve
import javax.inject.Inject
import javax.inject.Singleton

/** the audio fx settings every player's effects read at the start of each buffer */
@Singleton
class AudioFxService @Inject constructor() {
    @Volatile
    var reverb: ReverbParams = ReverbParams()

    @Volatile
    var loudness: LoudnessParams = LoudnessParams()

    @Volatile
    var spatial: SpatialParams = SpatialParams()

    /** replaced, never mutated: the sink notices a new curve by identity */
    @Volatile
    var volumeCurve: VolumeCurve = VolumeCurve()

    /** "even out songs": youtube's loudness numbers, or a file's own replaygain */
    @Volatile
    var normalize: Boolean = true

    @Volatile
    var replayGain: ReplayGainParams = ReplayGainParams()

    /** how much the loudness stage is pulling peaks down right now, in db (0 or less), for the meter */
    @Volatile
    var loudnessReductionDb: Float = 0f
}
