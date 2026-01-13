package dev.nyanrus.yamaneko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.nyanrus.yamaneko.ui.theme.YamanekoTheme
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import mozilla.components.browser.engine.gecko.GeckoSessionState
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.helper.Target
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.compose.engine.WebContent
import mozilla.components.feature.search.middleware.SearchMiddleware
import mozilla.components.feature.search.region.RegionMiddleware
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.service.location.LocationService

class MainActivity : ComponentActivity() {

    // 🔧 addon logic
    private fun injectBackgroundPlayFix(session: GeckoEngineSession) {
        val js = """
            (() => {
              try {
                Object.defineProperty(document, 'hidden', { value: false, configurable: true });
                Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
                document.addEventListener('visibilitychange', e => e.stopImmediatePropagation(), true);
                window.addEventListener('pagehide', e => e.stopImmediatePropagation(), true);
                window.addEventListener('blur', e => e.stopImmediatePropagation(), true);
              } catch(e) {}
            })();
        """.trimIndent()

        session.evaluateJS(js)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val engine = GeckoEngine(applicationContext)

        // ✅ allow autoplay
        engine.settings.mediaPlaybackRequiresUserGesture = false

        // ✅ observe sessions and inject JS
        engine.registerSessionObserver(object : GeckoEngine.SessionObserver {
            override fun onSessionStateChanged(session: GeckoEngineSession, state: GeckoSessionState) {
                injectBackgroundPlayFix(session)
            }
        })

        val locationService by lazy { LocationService.default() }

        val nekoViewModel = NekoViewModel(
            BrowserStore(
                middleware = listOf(
                    RegionMiddleware(applicationContext, locationService),
                    SearchMiddleware(applicationContext),
                ) + EngineMiddleware.create(engine),
            )
        )

        val session = SessionUseCases(nekoViewModel.browserStore)
        session.loadUrl("https://gixplay.glixar.com")

        setContent {
            YamanekoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box {
                        Scaffold(
                            bottomBar = {
                                BottomBar(nekoViewModel, Target.SelectedTab, Modifier.height(80.dp))
                            },
                        ) { innerPadding ->
                            Box(Modifier.padding(innerPadding)) {
                                WebContent(
                                    engine = engine,
                                    store = nekoViewModel.browserStore,
                                    target = Target.SelectedTab
                                )
                            }
                        }
                        SearchWindow(nekoViewModel, Target.SelectedTab)
                    }
                }
            }
        }
    }
}
