package com.joaoferreira.openbrowserpoc

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.joaoferreira.openbrowserpoc.ui.theme.OpenBrowserPOCTheme

/*
* Notes:
* It needs to include the androidx.browser:browser library (version 1.4.0 to this date).
* It also needs to include the <queries> tag inside the AndroidManifest file because Android API 30
* requires it when querying for compatible apps on the device (in this case, compatible browsers with
* the custom tabs feature).
*
* All of the code here followed the docs and recommendations of:
* https://developer.chrome.com/docs/android/custom-tabs/
* */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val googleUri = remember { Uri.parse("https://www.google.com/") }
            val bingUri = remember { Uri.parse("https://www.bing.com/") }
            val context = LocalContext.current
            val browserManager = remember {
                BrowserManager(
                    context,
                    customTabsIntentBuilder = {
                        setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                        setUrlBarHidingEnabled(true)
                    },
                    websitesToPreload = listOf(
                        googleUri,
                        bingUri,
                    )
                ).apply {
                    headers = listOf("test-key" to "test-value")
                }
            }
            OpenBrowserPOCTheme {
                // A surface container using the 'background' color from the theme
                Scaffold {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colors.background
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MyIconButton(Icons.Default.ExitToApp, "Default browser") {
                                browserManager.openBrowser(googleUri)
                            }
                            MyIconButton(Icons.Default.ExitToApp, "Light browser") {
                                browserManager.openBrowser(
                                    googleUri,
                                    otherCustomTabsIntentBuilder = {
                                        setColorScheme(CustomTabsIntent.COLOR_SCHEME_LIGHT)
                                    }
                                )
                            }
                            MyIconButton(Icons.Default.ExitToApp, "Browser with custom colors and action button") {
                                browserManager.openBrowser(
                                    googleUri,
                                    otherCustomTabsIntentBuilder = {
                                        setDefaultColorSchemeParams(
                                            CustomTabColorSchemeParams.Builder()
                                                .setToolbarColor(Color(0xFF7141d1).hashCode())
                                                .build()
                                        )
                                        setActionButton(
                                            resources.getDrawable(android.R.drawable.ic_input_get, resources.newTheme())!!.toBitmap(),
                                            "An action button",
                                            PendingIntent.getActivity(this@MainActivity, 100, Intent(), PendingIntent.FLAG_IMMUTABLE),
                                            true,
                                        )
                                    }
                                )
                            }
                            MyIconButton(Icons.Default.ExitToApp, "Browser with no hiding toolbar when scroll") {
                                browserManager.openBrowser(
                                    googleUri,
                                    otherCustomTabsIntentBuilder = {
                                        setUrlBarHidingEnabled(false)
                                    }
                                )
                            }
                            Divider(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), thickness = 2.dp)
                            Button(onClick = {
                                browserManager.updateWebsitesToPreload(listOf(bingUri))
                            }) {
                                Text("Preload some other websites")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MyIconButton(icon: ImageVector, text: String, onClick: () -> Unit = {}) {
        Button(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, "")
                Text(text)
            }
        }
    }
}
