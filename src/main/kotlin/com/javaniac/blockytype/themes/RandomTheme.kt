package com.javaniac.blockytype.themes

import kotlin.random.Random

/** The `random-theme` setting: which theme a player gets when they join. */
object RandomTheme {
    /** Perceived brightness 0..1 of a theme's background. */
    fun brightness(theme: AbstractTheme): Double {
        val c = theme.bgColor
        return (0.299 * c.red() + 0.587 * c.green() + 0.114 * c.blue()) / 255.0
    }

    fun isLight(theme: AbstractTheme) = brightness(theme) > 0.5

    /** Name of a theme for [mode] (`on`, `light`, `dark`), or null when the mode is off or no theme fits. */
    fun pick(mode: String, themes: List<AbstractTheme>, random: Random = Random.Default): String? {
        val pool = when (mode) {
            "on" -> themes
            "light" -> themes.filter(::isLight)
            "dark" -> themes.filterNot(::isLight)
            else -> return null
        }
        return pool.takeIf { it.isNotEmpty() }?.random(random)?.name?.lowercase()
    }
}
