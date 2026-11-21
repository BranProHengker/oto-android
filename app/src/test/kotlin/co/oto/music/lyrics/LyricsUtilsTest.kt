package co.oto.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {
    @Test
    fun `keeps the space when the gap between words is its own timed piece`() {
        val lines = LyricsUtils.parseLyrics(
            "[00:01.00]<00:01.00>I<00:01.40> <00:01.50>love<00:01.90> <00:02.00>you"
        )
        assertEquals("I love you", lines.single().text)
    }

    @Test
    fun `keeps the space when it comes right after the word tag`() {
        val lines = LyricsUtils.parseLyrics("[00:01.00]<00:01.00>I<00:01.50> love<00:02.00> you")
        assertEquals("I love you", lines.single().text)
    }

    @Test
    fun `one stray double space does not glue short words together`() {
        assertEquals("I love you baby", LyricsUtils.repairRichSyncPlainText("I love you  baby"))
    }

    @Test
    fun `old syllable-split text is still rejoined`() {
        assertEquals("want you", LyricsUtils.repairRichSyncPlainText("wa nt  yo u"))
    }

    @Test
    fun `lines all stamped at zero count as unsynced`() {
        val lines = LyricsUtils.parseLyrics("[00:00.00]first line\n[00:00.00]second line\n[00:00.00]third line")
        assertTrue(LyricsUtils.isUnsynced(lines))
        assertEquals(LyricsUtils.SyncKind.PLAIN, LyricsUtils.syncKind("[00:00.00]first line\n[00:00.00]second line"))
    }

    @Test
    fun `timed lines are not unsynced`() {
        val lines = LyricsUtils.parseLyrics("[00:01.00]first line\n[00:04.00]second line")
        assertFalse(LyricsUtils.isUnsynced(lines))
    }

    @Test
    fun `plain lyrics parse one entry per line`() {
        val lines = LyricsUtils.parsePlainLyrics("[ar:Someone]\nfirst line\n\nsecond line\n")
        assertEquals(listOf("first line", "second line"), lines.map { it.text })
    }

    @Test
    fun `duet needs two voices, not just tags`() {
        assertTrue(LyricsUtils.hasDistinctVoices("[00:01.00]{agent:v1}one\n[00:03.00]{agent:v2}two"))
        assertFalse(LyricsUtils.hasDistinctVoices("[00:01.00]{agent:v1}one\n[00:03.00]{agent:v1}two"))
        assertTrue(LyricsUtils.hasDistinctVoices("[00:01.00]one\n[00:03.00]{agent:v2}two"))
    }

    @Test
    fun `enhanced lrc merges bilingual same-timestamp romaji line`() {
        val raw = """
            [00:09.82]<00:09.82>貴方の<00:10.53>足が<00:10.97>月を<00:11.48>蹴った
            [00:09.82] anata no ashi ga tsuki o ketta
            [00:12.27]<00:12.27>白い砂が<00:13.40>夜灯<00:13.92>みたいだ
            [00:12.27] shiroi suna ga yorutomoshibimitaida
        """.trimIndent()
        val lines = LyricsUtils.parseLyrics(raw)
        assertEquals(2, lines.size)
        assertEquals("貴方の足が月を蹴った", lines[0].text)
        assertEquals("anata no ashi ga tsuki o ketta", lines[0].initialRomanizedText)
        assertEquals(4, lines[0].words?.size)
        assertEquals("白い砂が夜灯みたいだ", lines[1].text)
        assertEquals("shiroi suna ga yorutomoshibimitaida", lines[1].initialRomanizedText)
    }

    @Test
    fun `standard lrc merges bilingual same-timestamp line`() {
        val raw = """
            [00:27.43] あぁ 世界に色が付いて
            [00:27.43] aa sekai ni iro ga tsui te
            [00:30.51] 懐かしい夏の匂いがする
            [00:30.51] natsukashi i natsu no nioi gasuru
        """.trimIndent()
        val lines = LyricsUtils.parseLyrics(raw)
        assertEquals(2, lines.size)
        assertEquals("あぁ 世界に色が付いて", lines[0].text)
        assertEquals("aa sekai ni iro ga tsui te", lines[0].initialRomanizedText)
    }

    @Test
    fun `isEssentiallySame correctly matches romaji variations and prevents duplicate subline`() {
        assertTrue(LyricsUtils.isEssentiallySame("anata no ashi ga tsuki o ketta", "anata  no  ashi  ga  tsuki  o  ketta"))
        assertTrue(LyricsUtils.isEssentiallySame("Hello, World!", "hello world"))
        assertFalse(LyricsUtils.isEssentiallySame("anata no ashi", "different line"))
    }
}
