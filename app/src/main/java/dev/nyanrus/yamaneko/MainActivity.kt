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
import mozilla.components.browser.engine.EngineSession
import mozilla.components.browser.engine.EngineSessionObserver
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.helper.Target
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.compose.engine.WebContent
import mozilla.components.feature.search.middleware.SearchMiddleware
import mozilla.components.feature.search.region.RegionMiddleware
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.service.location.LocationService

class MainActivity : ComponentActivity() {

    // addon logic
    private fun injectBackgroundPlayFix(session: EngineSession) {
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

        session.evaluateJavascript(js)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val engine = GeckoEngine(applicationContext)

        // allow autoplay
        engine.settings.mediaPlaybackRequiresUserGesture = false

        // observe sessions and inject JS
        engine.registerSessionObserver(object : EngineSessionObserver {
            override fun onLoadingStateChanged(session: EngineSession, loading: Boolean) {
                if (!loading) {
                    injectBackgroundPlayFix(session)
                }
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
                        Scaffold(bottomBar = {
                            BottomBar(nekoViewModel, Target.SelectedTab, Modifier.height(80.dp))
                        }) { innerPadding ->
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

@Composable
fun SearchWindow(nekoViewModel: NekoViewModel, target: Target) {
    val isEditing = nekoViewModel.isEditing.collectAsState()
    if (isEditing.value) {
        val selectedTab = target.observeAsComposableStateFrom(nekoViewModel.browserStore) {
            it?.content?.url
        }

        val url = selectedTab.value!!.content.url
        var text by remember { mutableStateOf(url) }

        val session = SessionUseCases(nekoViewModel.browserStore)
        val focusRequester = remember { FocusRequester() }
        val focused = remember { mutableStateOf(false) }

        TextField(
            value = text,
            modifier = Modifier.focusRequester(focusRequester).onFocusChanged {
                if (focused.value && !it.isFocused) {
                    nekoViewModel.setIsEditing(false)
                }
            },
            onValueChange = { text = it },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                session.loadUrl(text)
                nekoViewModel.setIsEditing(false)
            })
        )

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            focused.value = true
        }
    }
}

@Composable
fun BottomBar(nekoViewModel: NekoViewModel, target: Target, modifier: Modifier = Modifier) {
    BottomAppBar(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UrlBar(nekoViewModel, target)
            ReloadButton(nekoViewModel)
        }
    }
}

@Composable
fun UrlBar(nekoViewModel: NekoViewModel, target: Target) {
    val selectedTab = target.observeAsComposableStateFrom(nekoViewModel.browserStore) {
        it?.content?.url
    }

    val text = {
        val url = selectedTab.value!!.content.url
        val list = url.split("//", limit = 2)
        if (list.size < 2) url else list[1]
    }

    val tmp = text()
    val list = tmp.split("/")
    val list1 = list[0].split(".")
    val tmp1 = list1.slice(0..<list1.size - 2).joinToString(".") + "."
    val tmp2 = list1.slice(list1.size - 2..<list1.size).joinToString(".")

    ClickableText(
        buildAnnotatedString {
            withStyle(style = SpanStyle(color = Color.LightGray)) { append(tmp1) }
            withStyle(style = SpanStyle(color = Color.White)) { append(tmp2) }
            withStyle(style = SpanStyle(color = Color.LightGray)) {
                append("/" + list.slice(1..<list.size).joinToString("/"))
            }
        },
        onClick = { nekoViewModel.setIsEditing(true) },
        modifier = Modifier
            .background(Color.Gray)
            .width(300.dp)
            .padding(10.dp, 10.dp),
        overflow = TextOverflow.Clip,
        style = TextStyle(lineBreak = LineBreak.Heading),
        maxLines = 1
    )
}

@Composable
fun ReloadButton(nekoViewModel: NekoViewModel) {
    val session = SessionUseCases(nekoViewModel.browserStore)
    TextButton(onClick = { session.reload() }) {
        Icon(Icons.Default.Refresh, contentDescription = "Reload")
    }
}
