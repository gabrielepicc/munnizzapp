package com.gabrielepiccione.onda.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Supporto agli icon pack nel formato standard "appfilter.xml"
 * (lo stesso usato da ADW, Nova, Lawnchair e dalla maggior parte dei pack sul Play Store).
 */
object IconPacks {

    data class Pack(val packageName: String, val label: String)

    private val THEME_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
    )

    fun installed(context: Context): List<Pack> {
        val pm = context.packageManager
        return THEME_ACTIONS
            .flatMap { action -> runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }
            .distinct()
            .map { pkg ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                Pack(pkg, label)
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Pack caricato: risorse + mappa "componente -> nome drawable". */
    class Loaded(private val res: Resources, private val pkg: String, private val map: Map<String, String>) {
        fun iconFor(component: ComponentName, densityDpi: Int): Drawable? {
            val name = map[component.flattenToString()] ?: map[component.packageName] ?: return null
            val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: return null
            return runCatching { res.getDrawableForDensity(id, densityDpi, null) }.getOrNull()
        }
    }

    fun load(context: Context, packageName: String): Loaded? {
        if (packageName.isBlank()) return null
        val res = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull() ?: return null
        val map = HashMap<String, String>()

        val parser: XmlPullParser? = run {
            val id = res.getIdentifier("appfilter", "xml", packageName)
            if (id != 0) return@run res.getXml(id)
            runCatching {
                val stream = res.assets.open("appfilter.xml")
                XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, "UTF-8") }
            }.getOrNull()
        }
        parser ?: return null

        runCatching {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (comp != null && drawable != null) {
                        // Formato: ComponentInfo{pacchetto/classe}
                        val inner = comp.substringAfter("ComponentInfo{", "").substringBefore("}")
                        if (inner.contains('/')) {
                            val cn = ComponentName.unflattenFromString(inner)
                            if (cn != null) {
                                map[cn.flattenToString()] = drawable
                                map.putIfAbsent(cn.packageName, drawable)
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }
        return if (map.isEmpty()) null else Loaded(res, packageName, map)
    }
}
