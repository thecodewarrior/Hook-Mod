package thecodewarrior.hooked.common

import com.teamwizardry.librarianlib.features.kotlin.ifCap
import com.teamwizardry.librarianlib.features.methodhandles.MethodHandleHelper
import com.teamwizardry.librarianlib.features.network.PacketHandler
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import thecodewarrior.hooked.HookedMod
import thecodewarrior.hooked.common.capability.HooksCap
import thecodewarrior.hooked.common.capability.HooksCapProvider
import thecodewarrior.hooked.common.network.PacketHookCapSync

/**
 * Created by TheCodeWarrior
 */
object HookTickHandler {
    init {
        MinecraftForge.EVENT_BUS.register(this)
    }

    val rl = ResourceLocation(HookedMod.MODID, "playerHookWielder")

    @SubscribeEvent
    fun playerAttach(e: AttachCapabilitiesEvent<Entity>) {
        (e.`object` as? EntityPlayer)?.let { player ->
            e.addCapability(rl, HooksCapProvider(player))
        }
    }

    @SubscribeEvent
    fun track(e: PlayerEvent.StartTracking) {
        if (e.entityPlayer.world.isRemote)
            return
        val target = e.target
        target.ifCap(HooksCap.CAPABILITY, null) {
            PacketHandler.NETWORK.sendTo(PacketHookCapSync(target), e.entityPlayer as EntityPlayerMP)
        }
    }

    @SubscribeEvent
    fun join(e: EntityJoinWorldEvent) {
        val entity = e.entity
        if (entity is EntityPlayer && !entity.world.isRemote) {
            PacketHandler.NETWORK.sendTo(PacketHookCapSync(entity), entity as EntityPlayerMP)
        }
    }

    @SubscribeEvent
    fun breakSpeed(e: PlayerEvent.BreakSpeed) {
        if (!e.entity.onGround) {
            e.entity.ifCap(HooksCap.CAPABILITY, null) { cap ->
                e.newSpeed = cap.controller?.modifyBreakSpeed(e.newSpeed) ?: e.newSpeed
            }
        }
    }

    @SubscribeEvent
    fun entityTick(e: LivingEvent.LivingUpdateEvent) {
        val entity = e.entity as? EntityPlayer ?: return

        HookedMod.PROXY.setAutoJump(e.entityLiving, true)
        if (!entity.hasCapability(HooksCap.CAPABILITY, null)) {
            return
        }
        val cap = entity.getCapability(HooksCap.CAPABILITY, null)!!

        cap.updateController()
        cap.controller?.tick()
    }
}

private var EntityLivingBase.jumpTicks by MethodHandleHelper.delegateForReadWrite<EntityLivingBase, Int>(EntityLivingBase::class.java, "jumpTicks", "field_70773_bE")
