package dev.thecodewarrior.hooked.client.renderer

import com.mojang.blaze3d.systems.RenderSystem
import com.teamwizardry.librarianlib.core.bridge.IRenderTypeState
import com.teamwizardry.librarianlib.core.util.*
import com.teamwizardry.librarianlib.core.util.kotlin.color
import com.teamwizardry.librarianlib.core.util.kotlin.loc
import com.teamwizardry.librarianlib.core.util.kotlin.normal
import com.teamwizardry.librarianlib.core.util.kotlin.pos
import com.teamwizardry.librarianlib.math.*
import de.javagl.obj.*
import dev.thecodewarrior.hooked.capability.HookedPlayerData
import dev.thecodewarrior.hooked.hook.processor.Hook
import dev.thecodewarrior.hooked.hook.type.BasicHookPlayerController
import dev.thecodewarrior.hooked.util.getWaistPos
import net.minecraft.client.renderer.IRenderTypeBuffer
import net.minecraft.client.renderer.RenderState
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.WorldRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.profiler.IProfiler
import net.minecraft.resources.IResourceManager
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.io.IOException

class BasicHookRenderer(val id: ResourceLocation):
    HookRenderer<BasicHookPlayerController>(), ISimpleReloadListener<BasicHookRenderer.ReloadData> {

    private var model: Obj = Objs.create()
    private var modelVertexIndices: IntArray = IntArray(0)
    private val modelLocation = loc(id.namespace, "models/hook/${id.path}.obj")
    private val hookRenderType = createHookRenderType(loc(id.namespace, "textures/hook/${id.path}/hook.png"))
    private val chain1RenderType = createChainRenderType(loc(id.namespace, "textures/hook/${id.path}/chain1.png"))
    private val chain2RenderType = createChainRenderType(loc(id.namespace, "textures/hook/${id.path}/chain2.png"))

    init {
        Client.resourceReloadHandler.register(this)
        apply(
            prepare(Client.resourceManager, Client.minecraft.profiler),
            Client.resourceManager, Client.minecraft.profiler
        )
    }

    override fun render(
        player: PlayerEntity,
        matrix: Matrix4dStack,
        partialTicks: Float,
        data: HookedPlayerData,
        controller: BasicHookPlayerController
    ) {
        data.hooks.forEach {
            renderHook(player, matrix, partialTicks, it)
        }
    }

    private fun renderHook(player: PlayerEntity, matrix: Matrix4dStack, partialTicks: Float, hook: Hook) {
        val waist = player.getWaistPos(partialTicks)
        val hookPos = hook.posLastTick + (hook.pos - hook.posLastTick) * partialTicks
        val chainLength = waist.distanceTo(hookPos)
        val chainDirection = (hookPos - waist) / chainLength

        matrix.push()
        matrix.translate(waist)
        matrix.rotate(Quaternion.fromRotationTo(vec(0, 1, 0), hookPos - waist))

        drawHalfChain(matrix, chain1RenderType, player.world, waist, chainDirection, chainLength, 0.5, 0.0)
        drawHalfChain(matrix, chain2RenderType, player.world, waist, chainDirection, chainLength, 0.0, 0.5)

        matrix.pop()

        matrix.push()

        matrix.translate(hook.pos)
        matrix.rotate(Quaternion.fromRotationTo(vec(0, 1, 0), hook.direction))

        val buffer = IRenderTypeBuffer.getImpl(Client.tessellator.buffer)
        val vb = buffer.getBuffer(hookRenderType)
        val lightmap = getBrightnessForRender(player.world, BlockPos(hookPos))
        modelVertexIndices.forEach { index ->
            val vertex = model.getVertex(index)
            val tex = model.getTexCoord(index)
            val normal = model.getNormal(index)
            vb.pos(matrix, vertex.x, vertex.y, vertex.z).color(Color.WHITE).tex(tex.x, 1 - tex.y)
                .overlay(0, 0)
                .lightmap(lightmap)
                .normal(
                    matrix.transformDelta(vec(normal.x, normal.y, normal.z)).normalize()
                )
                .endVertex()
        }
        buffer.finish()

        matrix.pop()
    }

    /**
     * Draws half of a chain. The deltaX and deltaZ parameters dictate whether to draw the ±X or ±Z part of the chain.
     */
    fun drawHalfChain(
        matrix: Matrix4d,
        renderType: RenderType,
        world: World,
        waist: Vec3d,
        chainDirection: Vec3d,
        chainLength: Double,
        deltaX: Double,
        deltaZ: Double
    ) {
        val chainSegments = floorInt(chainLength)
        val firstSegmentLength = chainLength - chainSegments

        val buffer = IRenderTypeBuffer.getImpl(Client.tessellator.buffer)

        val normal = matrix.transformDelta(vec(deltaZ, 0, deltaX)).normalize()
        val rnormal = -normal

        val vb = buffer.getBuffer(renderType)
        if (firstSegmentLength > chainLengthEpsilon) {
            val lightPos = BlockPos(waist + chainDirection * (firstSegmentLength / 2))
            val lightmap = getBrightnessForRender(world, lightPos)

            val minV = 1 - firstSegmentLength.toFloat()
            val len = firstSegmentLength

            vb.pos(matrix, -deltaX, 0, -deltaZ).color(Color.WHITE).tex(0f, minV)
                .overlay(0, 0).lightmap(lightmap).normal(normal).endVertex()
            vb.pos(matrix, deltaX, 0, deltaZ).color(Color.WHITE).tex(1f, minV)
                .overlay(0, 0).lightmap(lightmap).normal(normal).endVertex()
            vb.pos(matrix, deltaX, len, deltaZ).color(Color.WHITE).tex(1f, 1f)
                .overlay(0, 0).lightmap(lightmap).normal(normal).endVertex()
            vb.pos(matrix, -deltaX, len, -deltaZ).color(Color.WHITE).tex(0f, 1f)
                .overlay(0, 0).lightmap(lightmap).normal(normal).endVertex()

            vb.pos(matrix, -deltaX, len, -deltaZ).color(Color.WHITE).tex(0f, 1f)
                .overlay(0, 0).lightmap(lightmap).normal(rnormal).endVertex()
            vb.pos(matrix, deltaX, len, deltaZ).color(Color.WHITE).tex(1f, 1f)
                .overlay(0, 0).lightmap(lightmap).normal(rnormal).endVertex()
            vb.pos(matrix, deltaX, 0, deltaZ).color(Color.WHITE).tex(1f, minV)
                .overlay(0, 0).lightmap(lightmap).normal(rnormal).endVertex()
            vb.pos(matrix, -deltaX, 0, -deltaZ).color(Color.WHITE).tex(0f, minV)
                .overlay(0, 0).lightmap(lightmap).normal(rnormal).endVertex()
        }
        for (i in 0 until chainSegments) {
            val yPos = firstSegmentLength + i

            val lightPos = BlockPos(waist + chainDirection * (yPos + 0.5))
            val lightmap = getBrightnessForRender(world, lightPos)

            vb.pos(matrix, -deltaX, yPos, -deltaZ).color(Color.WHITE).tex(0f, 0f)
                .overlay(0, 0).lightmap(lightmap).normal(normal).endVertex()
            vb.pos(matrix, deltaX, yPos, deltaZ).color(Color.WHITE).tex(1f, 0f)
                .overlay(0, 0).lightmap(lightmap).normal(normal).endVertex()
            vb.pos(matrix, deltaX, yPos + 1, deltaZ).color(Color.WHITE).tex(1f, 1f)
                .overlay(0, 0).lightmap(lightmap).normal(normal).endVertex()
            vb.pos(matrix, -deltaX, yPos + 1, -deltaZ).color(Color.WHITE).tex(0f, 1f)
                .overlay(0, 0).lightmap(lightmap).normal(normal).endVertex()

            vb.pos(matrix, -deltaX, yPos + 1, -deltaZ).color(Color.WHITE).tex(0f, 1f)
                .overlay(0, 0).lightmap(lightmap).normal(rnormal).endVertex()
            vb.pos(matrix, deltaX, yPos + 1, deltaZ).color(Color.WHITE).tex(1f, 1f)
                .overlay(0, 0).lightmap(lightmap).normal(rnormal).endVertex()
            vb.pos(matrix, deltaX, yPos, deltaZ).color(Color.WHITE).tex(1f, 0f)
                .overlay(0, 0).lightmap(lightmap).normal(rnormal).endVertex()
            vb.pos(matrix, -deltaX, yPos, -deltaZ).color(Color.WHITE).tex(0f, 0f)
                .overlay(0, 0).lightmap(lightmap).normal(rnormal).endVertex()
        }
        buffer.finish()
    }

    private fun createHookRenderType(texture: ResourceLocation): RenderType {
        val stateBuilder = RenderType.State.getBuilder()
            .texture(RenderState.TextureState(texture, false, false))
            .alpha(DefaultRenderStates.DEFAULT_ALPHA)
            .depthTest(DefaultRenderStates.DEPTH_LEQUAL)
            .transparency(DefaultRenderStates.TRANSLUCENT_TRANSPARENCY)
            .cull(DefaultRenderStates.CULL_DISABLED)
            .lightmap(DefaultRenderStates.LIGHTMAP_ENABLED)
            .diffuseLighting(DefaultRenderStates.DIFFUSE_LIGHTING_ENABLED)
        val state = stateBuilder.build(true)

        return SimpleRenderTypes.makeType(
            "hooked_hook",
            DefaultVertexFormats.ENTITY, GL11.GL_TRIANGLES, 256, false, false, state
        )
    }

    private fun createChainRenderType(texture: ResourceLocation): RenderType {
        val stateBuilder = RenderType.State.getBuilder()
            .texture(RenderState.TextureState(texture, false, false))
            .alpha(DefaultRenderStates.DEFAULT_ALPHA)
            .depthTest(DefaultRenderStates.DEPTH_LEQUAL)
            .transparency(DefaultRenderStates.TRANSLUCENT_TRANSPARENCY)
            .lightmap(DefaultRenderStates.LIGHTMAP_ENABLED)
            .diffuseLighting(DefaultRenderStates.DIFFUSE_LIGHTING_ENABLED)
        val state = stateBuilder.build(true)

        return SimpleRenderTypes.makeType(
            "hooked_chain",
            DefaultVertexFormats.ENTITY, GL11.GL_QUADS, 256, false, false, state
        )
    }

    private fun getBrightnessForRender(world: World, pos: BlockPos): Int {
        return if (world.isBlockLoaded(pos)) WorldRenderer.getCombinedLight(world, pos) else 0
    }

    data class ReloadData(val model: Obj)

    override fun apply(result: ReloadData, resourceManager: IResourceManager, profiler: IProfiler) {
        this.model = result.model
        this.modelVertexIndices = ObjData.getFaceVertexIndicesArray(model)
    }

    override fun prepare(resourceManager: IResourceManager, profiler: IProfiler): ReloadData {
        var model = try {
            resourceManager.getResource(modelLocation).use {
                ObjReader.read(it.inputStream)
            }
        } catch (e: IOException) {
            Objs.create()
        }

        model = ObjUtils.convertToRenderable(model)

        return ReloadData(model)
    }

    companion object {
        val chainLengthEpsilon = 1 / 16.0
    }
}