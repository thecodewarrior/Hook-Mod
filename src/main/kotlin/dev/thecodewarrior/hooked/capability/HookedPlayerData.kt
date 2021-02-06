package dev.thecodewarrior.hooked.capability

import com.teamwizardry.librarianlib.foundation.capability.BaseCapability
import com.teamwizardry.librarianlib.prism.Save
import dev.thecodewarrior.hooked.hook.Hook
import dev.thecodewarrior.hooked.hook.HookPlayerController
import dev.thecodewarrior.hooked.hook.HookType
import dev.thecodewarrior.hooked.util.FadeTimer
import ll.dev.thecodewarrior.prism.annotation.Refract
import ll.dev.thecodewarrior.prism.annotation.RefractClass
import ll.dev.thecodewarrior.prism.annotation.RefractConstructor
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.CompoundNBT
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.CapabilityInject
import java.util.*

/**
 * A capability holding all the data and logic required for actually running the hooks.
 */
class HookedPlayerData(val player: PlayerEntity): BaseCapability() {
    @Save
    var type: HookType = HookType.NONE
        set(value) {
            if (value != field) {
                controller.remove()
                controller = value.createController(player)
            }
            field = value
        }

    @Save
    val hooks: LinkedList<Hook> = LinkedList()

    @Save
    var cooldownCounter: Int = 0

    var controller: HookPlayerController = HookPlayerController.NONE

    @RefractClass
    data class JumpState @RefractConstructor constructor(
        @Refract val doubleJump: Boolean,
        @Refract val sneaking: Boolean
    )

    var jumpState: JumpState? = null

    /**
     * State that is only ever used on the *logical* server. This includes things like syncing status.
     */
    class ServerState {
        val dirtyHooks: MutableSet<Hook> = mutableSetOf()
        var forceFullSyncToClient: Boolean = false
        var forceFullSyncToOthers: Boolean = false
    }

    var serverState: ServerState = ServerState()

    override fun deserializeNBT(nbt: CompoundNBT) {
        val oldType = type
        super.deserializeNBT(nbt)
        // the SimpleSerializer doesn't use the setter, so we have to update it here
        if (type != oldType) {
            controller.remove()
            controller = type.createController(player)
        }
        controller.deserializeNBT(nbt.getCompound("controller"))
    }

    override fun serializeNBT(): CompoundNBT {
        val nbt = super.serializeNBT()
        nbt.put("controller", controller.serializeNBT())
        return nbt
    }

    companion object {
        @JvmStatic
        @CapabilityInject(HookedPlayerData::class)
        lateinit var CAPABILITY: Capability<HookedPlayerData>
            @JvmSynthetic get // the raw field seems to be accessible
            @JvmSynthetic set
    }
}