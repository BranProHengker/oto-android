// a module's sign-in in a sheet over its page: the library import's own browser bar, web view and apple scripts

package co.oto.music.ui.screens.settings.modules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.oto.music.R
import co.oto.music.importer.account.AccountService
import co.oto.music.module.AccountState
import co.oto.music.module.OtoModule
import co.oto.music.module.SignInSpec
import co.oto.music.module.applemusic.AppleMusicModule
import co.oto.music.ui.component.PopupSheetHandle
import co.oto.music.ui.component.PopupSheetState
import co.oto.music.ui.screens.settings.importsync.CaptureWebContent
import co.oto.music.ui.screens.settings.importsync.onSignInTick
import co.oto.music.ui.screens.setup.onboarding.BrowserSheetBar
import co.oto.music.ui.screens.setup.onboarding.ProviderSquircle
import co.oto.music.viewmodels.ModulesViewModel

// the import browser's own grey, so both sign-ins look the same
private val BrowserSurface = Color(0xFF1C1C1C)

/** opens the module's sign-in; [onDone] hears how it went once the account is in, the sheet closes itself */
fun PopupSheetState.showModuleSignInSheet(module: OtoModule, viewModel: ModulesViewModel, onDone: (AccountState) -> Unit) {
    val spec = module.signIn ?: return
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(fullBleed = true, surface = BrowserSurface) {
        ModuleSignInContent(
            module = module,
            spec = spec,
            viewModel = viewModel,
            onDone = { state ->
                sheet?.dismiss()
                onDone(state)
            },
            onClose = { sheet?.dismiss() },
        )
    }
}

@Composable
private fun ModuleSignInContent(
    module: OtoModule,
    spec: SignInSpec,
    viewModel: ModulesViewModel,
    onDone: (AccountState) -> Unit,
    onClose: () -> Unit,
) {
    val id = module.info.id
    // apple music is the import's apple music: same page, same scripts pressing sign in and reading musickit's tokens
    val importService = if (id == AppleMusicModule.Id) AccountService.APPLE_MUSIC else null
    // tall enough for apple's whole sign-in, short enough to stay a sheet
    val pageHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
    var url by remember { mutableStateOf(spec.startUrl) }

    DisposableEffect(Unit) {
        viewModel.resetSignIn()
        onDispose { }
    }

    Column(Modifier.fillMaxWidth()) {
        BrowserSheetBar(
            icon = {
                if (importService != null) ProviderSquircle(importService, size = 38.dp) else ModuleLogo(id, module.info.name, size = 38)
            },
            title = stringResource(R.string.module_sign_in_to, module.info.name),
            url = url,
            onClose = onClose,
        )
        Spacer(Modifier.height(12.dp))
        CaptureWebContent(
            startUrl = spec.startUrl,
            playerOrigins = spec.sessionOrigins,
            desktopSite = spec.desktopSite,
            onHeader = { name, value, at -> viewModel.onSignInHeader(id, name, value, at, onDone) },
            onTick = { view ->
                importService?.onSignInTick(
                    view,
                    onApplePageProbe = { viewModel.onApplePageProbe(id, it, onDone) },
                    onCookies = { viewModel.onAppleCookies(id, it, onDone) },
                )
            },
            onExit = onClose,
            onUrlChange = { url = it },
            modifier = Modifier.fillMaxWidth().height(pageHeight),
        )
    }
}
