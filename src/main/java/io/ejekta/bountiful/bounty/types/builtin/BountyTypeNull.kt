package io.ejekta.bountiful.bounty.types.builtin

import io.ejekta.bountiful.bounty.BountyDataEntry
import io.ejekta.bountiful.bounty.types.IBountyExchangeable
import io.ejekta.bountiful.bounty.types.IBountyType
import io.ejekta.bountiful.bounty.types.Progress
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class BountyTypeNull : IBountyExchangeable {

    override val id: Identifier = Identifier("null")

    private fun logicUsageError(): Exception {
        return Exception("Cannot interact with a null logic object!")
    }

    override fun verifyValidity(entry: BountyDataEntry, player: PlayerEntity): MutableText {
        throw logicUsageError()
    }

    override fun textSummary(entry: BountyDataEntry, isObj: Boolean, player: PlayerEntity): MutableText {
        throw logicUsageError()
    }

    override fun textBoard(entry: BountyDataEntry, player: PlayerEntity): List<Text> {
        throw logicUsageError()
    }

    override fun getProgress(entry: BountyDataEntry, player: PlayerEntity): Progress {
        throw logicUsageError()
    }

    override fun tryFinishObjective(entry: BountyDataEntry, player: PlayerEntity): Boolean {
        throw logicUsageError()
    }

    override fun giveReward(entry: BountyDataEntry, player: PlayerEntity): Boolean {
        throw logicUsageError()
    }

}