// SimilarRecommendation.kt

package co.oto.music.models

import com.music.innertube.models.YTItem
import co.oto.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
