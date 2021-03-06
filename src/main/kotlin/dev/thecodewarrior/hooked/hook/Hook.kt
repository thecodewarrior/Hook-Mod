package dev.thecodewarrior.hooked.hook

import com.teamwizardry.librarianlib.math.plus
import com.teamwizardry.librarianlib.math.times
import ll.dev.thecodewarrior.prism.annotation.Refract
import ll.dev.thecodewarrior.prism.annotation.RefractClass
import ll.dev.thecodewarrior.prism.annotation.RefractConstructor
import net.minecraft.util.SoundEvent
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.vector.Vector3d
import net.minecraft.world.World
import java.util.*

@RefractClass
data class Hook @RefractConstructor constructor(
    /**
     * The hook's unique ID
     */
    @Refract val uuid: UUID,
    /**
     * The type of the hook
     */
    @Refract val type: HookType,
    /**
     * The position of the tail of the hook
     */
    @Refract var pos: Vector3d,
    /**
     * The current state.
     */
    @Refract var state: State,
    /**
     * The (normalized) direction the hook is pointing
     */
    @Refract var direction: Vector3d,
    /**
     * The block the hook is attached to. Should be (0,0,0) unless [state] is [State.PLANTED]
     */
    @Refract var block: BlockPos,
    /**
     * A controller-defined tag value
     */
    @Refract var tag: Int
) {
    /**
     * The position of the tail of the hook last tick
     */
    var posLastTick: Vector3d = pos

    /**
     * The position of the tip of the hook, as computed from the pos and direction
     */
    val tipPos: Vector3d
        get() = pos + direction * type.hookLength

    enum class State {
        EXTENDING, PLANTED, RETRACTING, REMOVED
    }

    companion object {
        fun hitSound(world: World, pos: BlockPos): SoundEvent {
            return world.getBlockState(pos).soundType.hitSound
        }
    }
}