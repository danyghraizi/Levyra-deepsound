package com.luc4n3x.levyra.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

internal fun MacrobenchmarkScope.launchAndExerciseLevyra() {
    pressHome()
    startActivityAndWait()
    device.waitForIdle()
    dismissOnboardingIfPresent()
    device.waitForIdle()
    repeat(3) {
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 4 / 5,
            device.displayWidth / 2,
            device.displayHeight / 3,
            12
        )
        device.waitForIdle()
    }
    device.findObject(By.descContains("Libr"))?.click()
        ?: device.findObject(By.textContains("Libr"))?.click()
        ?: device.findObject(By.descContains("Library"))?.click()
        ?: device.findObject(By.textContains("Library"))?.click()
    device.waitForIdle()
    device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.6f)
    device.waitForIdle()
    device.findObject(By.desc("Home"))?.click()
        ?: device.findObject(By.text("Home"))?.click()
    device.waitForIdle()
    startPlaybackAndOpenPlayer()
    exerciseTabSwitches()
}

private fun MacrobenchmarkScope.startPlaybackAndOpenPlayer() {
    device.findObject(By.text("Play all"))?.click()
        ?: device.findObject(By.desc("Play all"))?.click()
    device.waitForIdle()
    device.wait(Until.hasObject(By.desc("Pause")), 8_000L)
    device.waitForIdle()
    device.click(device.displayWidth / 2, (device.displayHeight * 0.86f).toInt())
    device.wait(Until.hasObject(By.descContains("Queue")), 5_000L)
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.exerciseTabSwitches() {
    listOf("Search", "Explore", "Library", "Home").forEach { label ->
        val tab = device.findObject(By.desc(label)) ?: device.findObject(By.text(label))
        if (tab == null) return@forEach
        runCatching { tab.click() }
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.dismissOnboardingIfPresent() {
    val labels = listOf("Continua", "Avanti", "Inizia", "Fatto", "Continue", "Next", "Start", "Done", "Salta", "Skip")
    repeat(6) {
        val button = labels.asSequence()
            .mapNotNull { label -> device.findObject(By.textContains(label)) }
            .firstOrNull()
            ?: return
        button.click()
        device.wait(Until.gone(By.text(button.text.orEmpty())), 1_000L)
        device.waitForIdle()
    }
}
