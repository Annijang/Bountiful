package io.ejekta.kambrik.gui

import com.mojang.blaze3d.systems.RenderSystem
import io.ejekta.bountiful.client.BoardScreen
import net.minecraft.client.gui.DrawableHelper
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier

open class KambrikSpriteGrid(val location: Identifier, val texWidth: Int, val texHeight: Int) {

    inner class KambrikSprite(
        val u: Float = 0f,
        val v: Float = 0f,
        val width: Int,
        val height: Int
    ) {

        val grid: KambrikSpriteGrid
            get() = this@KambrikSpriteGrid

        fun draw(screen: Screen, matrixStack: MatrixStack, x: Int, y: Int, w: Int = width, h: Int = height) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader)
            RenderSystem.setShaderTexture(0, location)
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
            DrawableHelper.drawTexture(matrixStack, x, y, screen.zOffset, u, v, w, h, texHeight, texWidth)
        }
    }

}