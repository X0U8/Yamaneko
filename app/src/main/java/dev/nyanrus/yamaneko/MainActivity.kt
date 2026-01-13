package dev.nyanrus.yamaneko

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.helper.Target
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.compose.engine.WebContent
import mozilla.components.feature.search.middleware.SearchMiddleware
import mozilla.components.feature.search.region.RegionMiddleware
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.service.location.LocationService

class MainActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private fun injectAddonFix(session: SessionUseCases) {
        handler.postDelayed({
            session.loadUrl(
                "javascript:(function(){try{" +
                        "Object.defineProperty(document,'hidden',{value:false,configurable:true});" +
                        "Object.defineProperty(document,'visibilityState',{value:'visible',configurable:true});" +
                        "document.addEventListener('visibilitychange',e=>e.stopImmediatePropagation(),true);" +
                        "window.addEventListener('pagehide',e=>e.stopImmediatePropagation(),true);" +
                        "window.addEventListener('blur',e=>e.stopImmediatePropagation(),true);" +
                        "}catch(e){}})();"
            )
        }, 2000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val engine = GeckoEngine(applicationContext)
        engine.settings.mediaPlaybackRequiresUserGesture = false

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
        injectAddonFix(session)

        setContent {
            YamanekoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box {
                        Scaffold(bottomBar = {
                            BottomBar(nekoViewModel, Target.SelectedTab, session, ::injectAddonFix)
                        }) { innerPadding ->
                            Box(Modifier.padding(innerPadding)) {
                                WebContent(engine, nekoViewModel.browserStore, Target.SelectedTab)
                            }
                        }
                        SearchWindow(nekoViewModel, Target.SelectedTab, session, ::injectAddonFix)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchWindow(
    nekoViewModel: NekoViewModel,
    target: Target,
    session: SessionUseCases,
    injector: (SessionUseCases) -> Unit
) {
    val isEditing = nekoViewModel.isEditing.collectAsState()
    if (isEditing.value) {
        val selectedTab = target.observeAsComposableStateFrom(nekoViewModel.browserStore) { it?.content?.url }
        val url = selectedTab.value!!.content.url
        var text by remember { mutableStateOf(url) }

        val focusRequester = remember { FocusRequester() }
        val focused = remember { mutableStateOf(false) }

        TextField(
            value = text,
            modifier = Modifier.focusRequester(focusRequester).onFocusChanged {
                if (focused.value && !it.isFocused) nekoViewModel.setIsEditing(false)
            },
            onValueChange = { text = it },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                session.loadUrl(text)
                injector(session)
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
fun BottomBar(
    nekoViewModel: NekoViewModel,
    target: Target,
    session: SessionUseCases,
    injector: (SessionUseCases) -> Unit,
    modifier: Modifier = Modifier
) {
    BottomAppBar(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UrlBar(nekoViewModel, target)
            ReloadButton(session, injector)
        }
    }
}

@Composable
fun UrlBar(nekoViewModel: NekoViewModel, target: Target) {
    val selectedTab = target.observeAsComposableStateFrom(nekoViewModel.browserStore) { it?.content?.url }

    val text = {
        val url = selectedTab.value!!.content.url
        val list = url.split("//", limit = 2)
        if (list.size < 2) url else list[1]
    }

    val tmp = text()
    val list = tmp.split("/")
    val list1 = list[0].split(".")
    val tmp1 = list1.dropLast(2).joinToString(".") + "."
    val tmp2 = list1.takeLast(2).joinToString(".")

    ClickableText(
        buildAnnotatedString {
            withStyle(SpanStyle(Color.LightGray)) { append(tmp1) }
            withStyle(SpanStyle(Color.White)) { append(tmp2) }
            withStyle(SpanStyle(Color.LightGray)) { append("/" + list.drop(1).joinToString("/")) }
        },
        onClick = { nekoViewModel.setIsEditing(true) },
        modifier = Modifier.background(Color.Gray).width(300.dp).padding(10.dp),
        overflow = TextOverflow.Clip,
        style = TextStyle(lineBreak = LineBreak.Heading),
        maxLines = 1
    )
}

@Composable
fun ReloadButton(session: SessionUseCases, injector: (SessionUseCases) -> Unit) {
    TextButton(onClick = {
        session.reload()
        injector(session)
    }) {
        Icon(Icons.Default.Refresh, contentDescription = "Reload")
    }
}
