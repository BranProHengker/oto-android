// pairs a signed code module with its manifest's look, player, settings and art so a code zip can ship a custom player and live surface

package co.oto.music.modules.code

import co.oto.music.module.AccountState
import co.oto.music.module.DiagnosticStep
import co.oto.music.module.LookModule
import co.oto.music.module.LyricsModule
import co.oto.music.module.MetadataModule
import co.oto.music.module.OtoModule
import co.oto.music.module.ModuleArt
import co.oto.music.module.ModuleChoice
import co.oto.music.module.ModuleFeature
import co.oto.music.module.ModuleField
import co.oto.music.module.ModuleFields
import co.oto.music.module.ModuleHost
import co.oto.music.module.ModuleInfo
import co.oto.music.module.ResolveResult
import co.oto.music.module.SignInSpec
import co.oto.music.module.SourceModule
import co.oto.music.module.TrackMetadata
import co.oto.music.module.TrackQuery
import co.oto.music.module.bridge.BridgedModule
import co.oto.music.modules.skin.ModuleSkin
import co.oto.music.modules.skin.SkinGlass
import co.oto.music.modules.skin.SkinParser
import co.oto.music.modules.skin.SkinSource
import co.oto.music.modules.skin.appliedLook
import co.oto.music.modules.web.FieldRange
import co.oto.music.modules.web.FieldRanges
import co.oto.music.modules.web.ManifestSettings
import co.oto.music.modules.web.WebModuleManifest
import co.oto.music.modules.web.resolveModuleArt
import co.oto.music.ui.player.styles.SurfaceSession
import java.io.File

internal class CodeSkinModule private constructor(
    private val inner: BridgedModule,
    private val manifest: WebModuleManifest,
    files: File?,
) : SourceModule, LyricsModule, LookModule, MetadataModule, ModuleArt, ModuleFields, SkinSource, FieldRanges, AutoCloseable {

    override val info: ModuleInfo get() = inner.info

    override val skin: ModuleSkin? = SkinParser.parse(
        inner.info.id, inner.info.name, manifest.player, manifest.font, manifest.pages,
        files, base = null, manifest.actions, manifest.glass, manifest.players, manifest.bar, manifest.mini,
        surface = inner.surface, floating = manifest.floating, background = manifest.background,
        appBackground = manifest.appBackground, sheet = manifest.sheet, menu = manifest.menu,
    )

    override val adds: Set<ModuleFeature> =
        if (skin != null || manifest.lookValues.isNotEmpty()) inner.adds + ModuleFeature.Look else inner.adds

    override val signIn: SignInSpec? get() = inner.signIn

    override val choices: List<ModuleChoice> get() = inner.choices

    override val icon: String? = resolveModuleArt(manifest.icon, files, base = null) ?: inner.icon

    override val banner: String? = resolveModuleArt(manifest.banner, files, base = null) ?: inner.banner

    private val settings = ManifestSettings(manifest)

    override val fields: List<ModuleField> get() = settings.fields.ifEmpty { inner.fields }

    override val ranges: Map<String, FieldRange> get() = settings.ranges

    override fun onFields(values: Map<String, String>) {
        settings.values = values
        inner.onFields(values)
    }

    override val look: Map<String, String>
        get() = appliedLook(inner.look + settings.look(manifest.lookValues), skin)

    override val glass: SkinGlass? get() = settings.glass(skin?.glass)

    override fun attach(host: ModuleHost) = inner.attach(host)

    override fun accountState(): AccountState = inner.accountState()

    override fun onSignInHeader(name: String, value: String, url: String): Boolean = inner.onSignInHeader(name, value, url)

    override suspend fun verifyAccount(): AccountState = inner.verifyAccount()

    override fun signOut() = inner.signOut()

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult = inner.resolve(track, picks)

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult =
        inner.diagnose(track, picks, report)

    override suspend fun lyrics(track: TrackQuery): String? = inner.lyrics(track)

    override suspend fun metadata(track: TrackQuery): TrackMetadata? = inner.metadata(track)

    override fun close() {
        SurfaceSession.release(inner.surface)
        inner.close()
    }

    companion object {
        /** returns [inner] untouched when the manifest has no skin, look or art of its own (so older code zips like js modules stay as-is) */
        fun wrap(inner: BridgedModule, manifest: WebModuleManifest?, files: File?): OtoModule {
            if (manifest == null) return inner
            val hasExtras = manifest.changesLook || manifest.icon != null || manifest.banner != null || manifest.settings.isNotEmpty()
            return if (hasExtras) CodeSkinModule(inner, manifest, files) else inner
        }
    }
}
