package com.joaoferreira.openbrowserpoc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession

/**
 * In-app browsers are called CustomTabs (Because they are actually custom tabs of the device browsers
 * that are specially made to operate as they are part of the app). This class makes the use of the
 * CustomTabs API easier. It automatically checks for compatible browsers and preloads websites that
 * the user will most likely visit. (It is performance aware, so if the user has a low-end device or
 * bad internet connection it won't preload).
 *
 * @param context The context needed to search all compatible browsers with Custom Tabs and to
 * open the browser.
 * @param websitesToPreload The list of websites to be preloaded (for seamless transitions from
 * app to browser and vice-versa). It is guaranteed to run and it doesn't preload websites on
 * low end devices and those with bad internet connection.
 * @param customTabsIntentBuilder The builder to give the In-app browser custom behaviors. See
 * the sample below to get more info.
 * @param customTabsCallback A callback to be aware of the navigation status and some other
 * things.
 *
 * - Here is a sample and explanation about each method of [customTabsIntentBuilder]:
 * ```
 * val red = 0xFF0000
 * val green = 0x00FF00
 * val blue = 0x0000FF

 * val pendingIntent = PendingIntent.getActivity(
 * context,
 * 100,
 * Intent(
 *      context,
 *      MainActivity::class.java),
 *      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
 * )
 *
 * CustomTabsIntent.Builder()
 * //Define the colors to be used inside custom tabs. Note that the colors defined here
 * //overrides the CustomTabsIntent#Builder#setColorScheme behavior.
 * .setDefaultColorSchemeParams(
 *      CustomTabColorSchemeParams.Builder()
 *      //Defines the top toolbar background color
 *      .setToolbarColor(Color(0xFFFFFFFF).hashCode())
 *      //Defines the Android navigation bar (back, home and apps bar) color
 *      .setNavigationBarColor(red)
 *      //Only used on Android P and above (API 28). I don't know what is this
 *      .setNavigationBarDividerColor(green)
 *      /*Color for the secondary toolbar (Aka the bottom toolbar) that can be
 *      * defined with CustomTabsIntent#Builder#setSecondaryToolbarViews*/
 *      .setSecondaryToolbarColor(red)
 *      .build()
 * )
 * //Customize the close button icon, for some reason it only works with vector
 * //drawables (aka a drawable with a vector).
 * //It should have a max of 48dp width and 24dp height, otherwise it will not appear.
 * .setCloseButtonIcon(resources.getDrawable(R.drawable.custom_back, resources.newTheme())!!.apply {
 *      setTint(Color(0xFFFF0000).hashCode())
 * }.toBitmap())
 * //Animations that occur when the browser appears. The exit animation should be
 * //the reverse of the enter animation
 * .setStartAnimations(context, R.anim.anim_slide_up, R.anim.anim_slide_down)
 * //Defines a menu item with a label and custom behavior set by a pending intent. It
 * //will appear on the context menu on top toolbar (three dots).
 * //you can add up to 5 items.
 * .addMenuItem("Custom menu item 1", pendingIntent)
 * //Sets a action button with the given bitmap icon with a custom behavior set by a
 * //pending intent. If shouldTint is set to true, android automatically picks a
 * //contrasting color with the toolbar background color defined in
 * //CustomTabColorSchemeParams and the drawable color will be ignored.
 * //IMPORTANT: If the description is empty, the icon WILL NOT SHOW UP!
 * .setActionButton(
 *      resources.getDrawable(R.drawable.custom_back, resources.newTheme())!!.apply {
 *          setTint(Color(0xFFFF0000).hashCode())
 *      }.toBitmap(),
 *      "Custom action button",
 *      pendingIntent,
 *      true
 * )
 * //Set what color theme (dark or light) should the custom tab use. It will be ignored
 * //based on CustomTabColorSchemeParams values. They have precedence.
 * .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
 * //Animations that occur when the browser disappears. The exit animation should be
 * //the reverse of the enter animation
 * .setExitAnimations(context, R.anim.anim_slide_up, R.anim.anim_slide_down)
 * //Set if it is possible to open Instant Apps inside the custom tabs.
 * //Instant apps are like some peaces of application that can be downloaded and run
 * //on the current session, and then deleted afterwards.
 * .setInstantAppsEnabled(false)
 * //Set whenever the title of the current link should be shown or not. It will appear
 * //above the link in top toolbar.
 * .setShowTitle(true)
 * //Set the view for the custom bottom toolbar, accepting only remote views. If there are any
 * //buttons in layout, you can pass its id to the clickableIds parameter and set its behavior onClick
 * //with the pendingIntent parameter.
 * .setSecondaryToolbarViews(RemoteViews(context.packageName, R.layout.remote_view_bottom_toolbar), null, null)
 * //Set if the user can see the option "Share..." inside the context menu to share
 * //the link to the website outside the app.
 * //CustomTabsIntent#SHARE_STATE_DEFAULT sets the share state depending on the browser.
 * .setShareState(CustomTabsIntent.SHARE_STATE_DEFAULT)
 * //Configures the toolbars to hide when scrolling web content, similar to custom
 * //scroll behaviors on Android
 * .setUrlBarHidingEnabled(true)
 * .apply {
 *      customTabsSession?.let {
 *          //Set the session for this particular custom tab. More details about a
 *          //session explained on onStart configuration.
 *          setSession(it)
 *      }
 * }
 * .build()
 * .apply {
 *      //It is a good practice to let websites know what app is calling them
 *      intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
 *      //Open the url in a custom tab
 *      launchUrl(context, uri)
 * }
 * ```
 * */
class BrowserManager(
    private val context: Context,
    private val websitesToPreload: List<Uri> = listOf(),
    customTabsIntentBuilder: CustomTabsIntent.Builder.() -> Unit = {},
    private val customTabsCallback: CustomTabsCallback? = null,
) {
    private val customTabsIntentBuilder = CustomTabsIntent.Builder().apply(customTabsIntentBuilder)
    private var canOpenCustomTab: Boolean = false

    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null
    private var customServiceConnectionCallback: CustomTabsServiceConnection? = null

    /**
     * Headers to put inside intent that opens the browser. */
    var headers: List<Pair<String, String>>? = null

    init {
        val supportedPackages = getCustomTabsPackages(context)

        canOpenCustomTab = supportedPackages.isNotEmpty()
        if (canOpenCustomTab) {
            if (customServiceConnectionCallback == null) {
                customServiceConnectionCallback = object : CustomTabsServiceConnection() {
                    override fun onServiceDisconnected(name: ComponentName?) {

                    }

                    override fun onCustomTabsServiceConnected(
                        name: ComponentName,
                        client: CustomTabsClient
                    ) {
                        customTabsClient = client
                        /* The customTabsCallback is a callback to be aware of some events with the custom tab, like getting
                         * notified when the load finishes or didn't complete successfully.*/
                        /* Sets a new CustomTabsSession that provides a lot of extra configuration
                         * options for the browser, including performance improvements.*/
                        customTabsSession = client.newSession(customTabsCallback)
                        customTabsSession?.let {
                            /*Tell the browser that these urls might open, so it pre-loads them to open faster
                            It is also smart, it won't preload on low end devices.*/
                            preloadWebsites(websitesToPreload)
                        }
                    }
                }
            }

            CustomTabsClient.bindCustomTabsService(
                context,
                supportedPackages[0].activityInfo.packageName,
                customServiceConnectionCallback!!
            )
        }
    }

    /**
     * Opens an In-app browser if the app supports this feature or a normal browser if it doesn't.
     * Note that the behavior when the device doesn't support custom tabs can be replaced with the
     * [onNoCustomTabsSupported].
     *
     * @param uri The URI that will be opened on the browser (In-app or not)
     * @param otherCustomTabsIntentBuilder The builder to give the In-app browser custom
     * behaviors. It overrides the default [customTabsIntentBuilder] from the constructor. See the
     * sample below to get more info.
     * @param headers overrides the class default headers
     * @param onNoCustomTabsSupported The behavior when the device doesn't have a browser that
     * supports In-app browser. The default behavior is to open a browser outside the app, but it
     * can be replaced by implementing this callback.
     *
     * - Here is a sample and explanation about each method of [otherCustomTabsIntentBuilder]:
     * ```
     * val red = 0xFF0000
     * val green = 0x00FF00
     * val blue = 0x0000FF

     * val pendingIntent = PendingIntent.getActivity(
     * context,
     * 100,
     * Intent(
     *      context,
     *      MainActivity::class.java),
     *      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
     * )
     *
     * CustomTabsIntent.Builder()
     * //Define the colors to be used inside custom tabs. Note that the colors defined here
     * //overrides the CustomTabsIntent#Builder#setColorScheme behavior.
     * .setDefaultColorSchemeParams(
     *      CustomTabColorSchemeParams.Builder()
     *      //Defines the top toolbar background color
     *      .setToolbarColor(Color(0xFFFFFFFF).hashCode())
     *      //Defines the Android navigation bar (back, home and apps bar) color
     *      .setNavigationBarColor(red)
     *      //Only used on Android P and above (API 28). I don't know what is this
     *      .setNavigationBarDividerColor(green)
     *      /*Color for the secondary toolbar (Aka the bottom toolbar) that can be
     *      * defined with CustomTabsIntent#Builder#setSecondaryToolbarViews*/
     *      .setSecondaryToolbarColor(red)
     *      .build()
     * )
     * //Customize the close button icon, for some reason it only works with vector
     * //drawables (aka a drawable with a vector).
     * //It should have a max of 48dp width and 24dp height, otherwise it will not appear.
     * .setCloseButtonIcon(resources.getDrawable(R.drawable.custom_back, resources.newTheme())!!.apply {
     *      setTint(Color(0xFFFF0000).hashCode())
     * }.toBitmap())
     * //Animations that occur when the browser appears. The exit animation should be
     * //the reverse of the enter animation
     * .setStartAnimations(context, R.anim.anim_slide_up, R.anim.anim_slide_down)
     * //Defines a menu item with a label and custom behavior set by a pending intent. It
     * //will appear on the context menu on top toolbar (three dots).
     * //you can add up to 5 items.
     * .addMenuItem("Custom menu item 1", pendingIntent)
     * //Sets a action button with the given bitmap icon with a custom behavior set by a
     * //pending intent. If shouldTint is set to true, android automatically picks a
     * //contrasting color with the toolbar background color defined in
     * //CustomTabColorSchemeParams and the drawable color will be ignored.
     * //IMPORTANT: If the description is empty, the icon WILL NOT SHOW UP!
     * .setActionButton(
     *      resources.getDrawable(R.drawable.custom_back, resources.newTheme())!!.apply {
     *          setTint(Color(0xFFFF0000).hashCode())
     *      }.toBitmap(),
     *      "Custom action button",
     *      pendingIntent,
     *      true
     * )
     * //Set what color theme (dark or light) should the custom tab use. It will be ignored
     * //based on CustomTabColorSchemeParams values. They have precedence.
     * .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
     * //Animations that occur when the browser disappears. The exit animation should be
     * //the reverse of the enter animation
     * .setExitAnimations(context, R.anim.anim_slide_up, R.anim.anim_slide_down)
     * //Set if it is possible to open Instant Apps inside the custom tabs.
     * //Instant apps are like some peaces of application that can be downloaded and run
     * //on the current session, and then deleted afterwards.
     * .setInstantAppsEnabled(false)
     * //Set whenever the title of the current link should be shown or not. It will appear
     * //above the link in top toolbar.
     * .setShowTitle(true)
     * //Set the view for the custom bottom toolbar, accepting only remote views. If there are any
     * //buttons in layout, you can pass its id to the clickableIds parameter and set its behavior onClick
     * //with the pendingIntent parameter.
     * .setSecondaryToolbarViews(RemoteViews(context.packageName, R.layout.remote_view_bottom_toolbar), null, null)
     * //Set if the user can see the option "Share..." inside the context menu to share
     * //the link to the website outside the app.
     * //CustomTabsIntent#SHARE_STATE_DEFAULT sets the share state depending on the browser.
     * .setShareState(CustomTabsIntent.SHARE_STATE_DEFAULT)
     * //Configures the toolbars to hide when scrolling web content, similar to custom
     * //scroll behaviors on Android
     * .setUrlBarHidingEnabled(true)
     * .apply {
     *      customTabsSession?.let {
     *          //Set the session for this particular custom tab. More details about a
     *          //session explained on onStart configuration.
     *          setSession(it)
     *      }
     * }
     * .build()
     * .apply {
     *      //It is a good practice to let websites know what app is calling them
     *      intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
     *      //Open the url in a custom tab
     *      launchUrl(context, uri)
     * }
     * ```
     */
    fun openBrowser(
        uri: Uri,
        headers: List<Pair<String, String>>? = null,
        otherCustomTabsIntentBuilder: (CustomTabsIntent.Builder.() -> Unit)? = null,
        onNoCustomTabsSupported: (uri: Uri) -> Unit = {
            //If the device doesn't have compatible browsers for custom tabs, just open it normally
            val browserIntent = Intent(Intent.ACTION_VIEW, uri)

            (headers ?: this.headers)?.let {
                val headersBundle = Bundle()
                it.forEach { header ->
                    headersBundle.putString(header.first, header.second)
                }
                browserIntent.putExtra(Browser.EXTRA_HEADERS, headersBundle)
            }
            context.startActivity(browserIntent)
        },
    ) {
        if (canOpenCustomTab) {
            (otherCustomTabsIntentBuilder?.let { CustomTabsIntent.Builder().apply(it) }
                ?: customTabsIntentBuilder)
                .apply {
                    customTabsSession?.let {
                        setSession(it)
                    }
                }
                .build()
                .apply {
                    //It is a good practice to let websites know what app is calling them
                    intent.putExtra(
                        Intent.EXTRA_REFERRER,
                        Uri.parse("android-app://${context.packageName}")
                    )

                    headers?.let {
                        val headersBundle = Bundle()
                        it.forEach { header ->
                            headersBundle.putString(header.first, header.second)
                        }
                        intent.putExtra(Browser.EXTRA_HEADERS, headersBundle)
                    }

                    //Open the url in a custom tab
                    launchUrl(context, uri)
                }
        } else {
            onNoCustomTabsSupported(uri)
        }
    }

    /**
     * Set the websites the user might visit. They have to be inserted on priority order
     * (left to right).
     * This function is not guaranteed to perform because it depends on the service connection to be
     * established first.
     *
     * @param websitesToPreload The websites that will be preloaded, in ascendant priority order.
     * */
    fun updateWebsitesToPreload(websitesToPreload: List<Uri>) {
        preloadWebsites(websitesToPreload)
    }

    /**
     * This function takes a list of websites and then tell the browser to preload those in ascendant
     * priority order. Know that those websites aren't guaranteed to be preloaded, it automatically
     * checks for low-end devices or bad internet connection as a criteria to preload them or not.
     * */
    private fun preloadWebsites(websitesToPreload: List<Uri>) {
        if (canOpenCustomTab) {
            customTabsSession?.let {
                //Tell the browser that these URIs might open, so it pre-loads them to open faster
                //It is also smart, it won't preload on low end devices or with bad internet connection
                if (websitesToPreload.isNotEmpty()) {
                    it.mayLaunchUrl(
                        //The prioritized Uri to preload
                        websitesToPreload[0],
                        null,
                        //The other URIs to preload
                        if (websitesToPreload.size > 1) {
                            websitesToPreload.takeLast(websitesToPreload.size - 1).map {
                                Bundle().apply {
                                    putParcelable(
                                        CustomTabsService.KEY_URL,
                                        it
                                    )
                                }
                            }
                        } else emptyList()
                    )
                }
            }
            customTabsClient?.warmup(0L)
        }
    }
}