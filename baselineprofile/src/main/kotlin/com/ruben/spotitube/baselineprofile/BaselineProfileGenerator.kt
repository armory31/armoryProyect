package com.ruben.spotitube.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "com.ruben.spotitube",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            // Esperar hasta 10 s a que Home muestre contenido (texto "Selecciones rápidas")
            device.wait(Until.hasObject(By.text("Selecciones rápidas")), 10_000)

            // Scroll abajo y arriba en Home
            val homeScrollable = device.findObject(By.scrollable(true))
            if (homeScrollable != null) {
                homeScrollable.scroll(Direction.DOWN, 0.8f)
                device.waitForIdle()
                homeScrollable.scroll(Direction.UP, 0.8f)
                device.waitForIdle()
            } else {
                val width = device.displayWidth
                val height = device.displayHeight
                device.swipe(width / 2, (height * 0.8).toInt(), width / 2, (height * 0.2).toInt(), 10)
                device.waitForIdle()
                device.swipe(width / 2, (height * 0.2).toInt(), width / 2, (height * 0.8).toInt(), 10)
                device.waitForIdle()
            }

            // Recorrer las 4 pestañas principales (Explorar, Géneros, Biblioteca, Inicio) dos vueltas
            val tabs = listOf("Explorar", "Géneros", "Biblioteca", "Inicio")
            repeat(2) {
                tabs.forEach { label ->
                    val tabObject = device.findObject(By.text(label)) ?: device.findObject(By.desc(label))
                    if (tabObject != null) {
                        tabObject.click()
                    } else {
                        // Fallback con swipe horizontal si no se encuentra el botón
                        val width = device.displayWidth
                        val height = device.displayHeight
                        device.swipe((width * 0.8).toInt(), height / 2, (width * 0.2).toInt(), height / 2, 10)
                    }
                    device.waitForIdle()

                    if (label == "Biblioteca") {
                        val libScrollable = device.findObject(By.scrollable(true))
                        if (libScrollable != null) {
                            libScrollable.scroll(Direction.DOWN, 0.5f)
                            device.waitForIdle()
                        } else {
                            val width = device.displayWidth
                            val height = device.displayHeight
                            device.swipe(width / 2, (height * 0.7).toInt(), width / 2, (height * 0.3).toInt(), 10)
                            device.waitForIdle()
                        }
                    }
                }
            }
        }
    }
}
