package com.luc4n3x.levyra.widget

object LevyraWidgetBridge {
    @Volatile
    var onToggle: (() -> Boolean)? = null

    @Volatile
    var onNext: (() -> Boolean)? = null

    @Volatile
    var onPrevious: (() -> Boolean)? = null

    fun clear() {
        onToggle = null
        onNext = null
        onPrevious = null
    }
}
