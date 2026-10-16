// ManulLibs Map — Copyright (C) 2026 ManulCraft. GPL-3.0-or-later, see LICENSE.
package ru.manulcraft.manullibs.map

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

/** Ввод имени метки. Enter — готово, Esc — назад на карту. */
class MarkNameScreen(
    private val parent: Screen?,
    title: Text,
    private val onDone: (String) -> Unit,
) : Screen(title) {

    private lateinit var field: TextFieldWidget
    private lateinit var doneButton: ButtonWidget

    override fun init() {
        val cx = width / 2
        val cy = height / 2
        field = TextFieldWidget(textRenderer, cx - 99, cy - 10, 198, 20, Text.translatable("manullibs.map.mark_name.field"))
        field.setMaxLength(32)
        field.setChangedListener { doneButton.active = it.isNotBlank() }
        addDrawableChild(field)
        doneButton = addDrawableChild(
            ButtonWidget.builder(ScreenTexts.DONE) { finish() }.dimensions(cx - 100, cy + 16, 98, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(ScreenTexts.CANCEL) { close() }.dimensions(cx + 2, cy + 16, 98, 20).build()
        )
        doneButton.active = false
        setInitialFocus(field)
    }

    private fun finish() {
        val name = field.text.trim()
        if (name.isEmpty()) return
        onDone(name)
        close()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finish()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 30, 0xFFFFFF)
    }

    override fun close() {
        client?.setScreen(parent)
    }
}
