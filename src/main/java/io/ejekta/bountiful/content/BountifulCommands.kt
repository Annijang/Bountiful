package io.ejekta.bountiful.content

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType.getInteger
import io.ejekta.bountiful.Bountiful
import io.ejekta.bountiful.bounty.BountyData
import io.ejekta.bountiful.bounty.BountyRarity
import io.ejekta.bountiful.config.*
import io.ejekta.bountiful.data.PoolEntry
import io.ejekta.kambrik.Kambrik
import io.ejekta.kambrik.api.command.*
import io.ejekta.kambrik.ext.identifier
import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.command.argument.NumberRangeArgumentType
import net.minecraft.item.ItemStack
import net.minecraft.network.MessageType
import net.minecraft.network.PacketByteBuf
import net.minecraft.predicate.NumberRange
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.*
import net.minecraft.util.Formatting
import java.io.File


object BountifulCommands : CommandRegistrationCallback {

    override fun register(dispatcher: CommandDispatcher<ServerCommandSource>, dedicated: Boolean) {

        println("Adding serverside commands..")

        dispatcher.addCommand("bo") {
            requires(Kambrik.Command::hasBasicCreativePermission)

            val pools = suggestionListTooltipped {
                BountifulContent.Pools.map { pool ->
                    var trans = pool.usedInDecrees.map { it.translation }
                    val translation = if (trans.isEmpty()) {
                        LiteralText("None")
                    } else {
                        trans.reduce { acc, decree ->
                            acc.append(", ").append(decree)
                        }
                    }
                    pool.id to translation
                }
            }

            val decrees = suggestionListTooltipped {
                BountifulContent.Decrees.map { decree ->
                    decree.id to decree.translation
                }
            }

            // /bo hand
            // /bo hand complete
            "hand" {
                this runs hand()
                "complete" runs complete()
            }

            // /bo gen decree (decType)
            // /bo gen bounty (rep_level)
            "gen" {
                "decree" {
                    argString("decType", items = decrees) runs playerCommand { player ->
                        val decId = getString("decType")
                        val stack = DecreeItem.create(decId)
                        player.giveItemStack(stack)
                        1
                    }
                }

                "bounty" {
                    argInt("rep", -30..30) runs genBounty()
                }
            }



            // /bo pool [poolName] add hand
            // /bo pool [poolName] add tag [#tag]
            // /bo pool [poolName]
            "pool" {

                argString("poolName", items = pools) {
                    "add" {
                        "hand" {
                            this runs addHandToPool()

                            argIntRange("amount") {
                                argInt("unit_worth") runs playerCommand { player ->
                                    val amt = NumberRangeArgumentType.IntRangeArgumentType.getRangeArgument(this, "amount")
                                    if (amt.min == null || amt.max == null) {
                                        player.sendMessage(LiteralText("Amount Range must have a minimum and maximum value!"), false)
                                        return@playerCommand 0
                                    }

                                    val worth = getInt("unit_worth")

                                    addHandToPool(amt.min!!..amt.max!!, worth).run(this)

                                    1
                                }
                            }

                        }

                    }
                }

            }

            "util" {
                "debug" {
                    "weights" { argInt("rep", -30..30) runs weights() }
                    "dump" runs dumpData()
                }

                "verify" {
                    "pools" runs verifyPools()
                    "hand" runs verifyBounty()
                }
            }

        }
    }

    private fun hand() = playerCommand { player ->

        println("hand")

        val held = player.mainHandStack

        val newPoolEntry = PoolEntry.create().apply {
            content = held.identifier.toString()
            nbt = if (player.mainHandStack == ItemStack.EMPTY) null else held.nbt
        }

        try {
            val saved = newPoolEntry.save(JsonFormats.Hand)

            println(saved)
            player.sendMessage(LiteralText(saved), MessageType.CHAT, player.uuid)

            val packet = PacketByteBufs.create()
            packet.writeString(saved)
            ServerPlayNetworking.send(player, Bountiful.id("copydata"), packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        1
    }

    private fun MutableText.fileOpenerText(file: File): Text {
        return styled {
            it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_FILE, file.absolutePath))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, LiteralText("Click to open file '${file.name}'")))
        }
    }

    private fun addHandToPool(inAmount: IntRange? = null, inUnitWorth: Int? = null) = playerCommand { player ->
        val poolName = getString("poolName")
        val held = player.mainHandStack

        val newPoolEntry = PoolEntry.create().apply {
            content = held.identifier.toString()
            nbt = held.nbt
            if (inAmount != null) {
                amount = PoolEntry.EntryRange(inAmount.first, inAmount.last)
            }
            if (inUnitWorth != null) {
                unitWorth = inUnitWorth.toDouble()
            }
        }

        if (poolName.trim() != "") {

            val file = BountifulIO.getPoolFile(poolName).apply {
                ensureExistence()
                edit { content.add(newPoolEntry) }
            }

            player.sendMessage(LiteralText("Item added."), MessageType.CHAT, player.uuid)
            player.sendMessage(LiteralText("Edit §6'config/bountiful/bounty_pools/$poolName.json'§r to edit details.").fileOpenerText(file.getOrCreateFile()), MessageType.CHAT, player.uuid)
        } else {
            player.sendMessage(LiteralText("Invalid pool name!"), MessageType.CHAT, player.uuid)
        }

        1
    }

    private fun complete() = playerCommand { player ->
        val held = player.mainHandStack
        val data = BountyData[held]

        try {
            data.tryCashIn(player, held)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        1
    }

    private fun genBounty() = playerCommand { player ->
        try {
            val rep = getInt("rep")
            val bd = BountyCreator.create(BountifulContent.Decrees.toSet(), rep, player.world.time)
            player.giveItemStack(BountyItem.create(bd))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        1
    }

    private fun weights() = Command<ServerCommandSource> { ctx ->
        try {
            val rep = getInteger(ctx, "rep")

            println("RARITY WEIGHTS:")
            BountyRarity.values().forEach { rarity ->
                println("${rarity.name}\t ${rarity.weightAdjustedFor(rep)}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        1
    }

    private fun verifyBounty() = playerCommand { player ->

        val held = player.mainHandStack

        if (held.item is BountyItem) {
            if (!BountyData[held].verifyValidity(player)) {
                player.sendMessage(
                    LiteralText("Please report this to the modpack author (or the mod author, if this is not part of a modpack)")
                        .formatted(Formatting.DARK_RED).formatted(Formatting.BOLD),
                    false
                )
            }
        }

        1
    }

    private fun dumpData() = playerCommand { player ->

        for (decree in BountifulContent.Decrees.sortedBy { it.id }) {
            Bountiful.LOGGER.info("Decree: ${decree.id}")
            for (obj in decree.objectives.sortedBy { it }) {
                Bountiful.LOGGER.info("    * [OBJ] $obj")
            }
            for (rew in decree.rewards.sortedBy { it }) {
                Bountiful.LOGGER.info("    * [REW] $rew")
            }
        }

        for (pool in BountifulContent.Pools.sortedBy { it.id }) {
            Bountiful.LOGGER.info("Pool: ${pool.id}")
            for (item in pool.content.sortedBy { it.content }) {
                Bountiful.LOGGER.info("    * [${item.type}] ${item.content}")
            }
        }

        1
    }

    private fun verifyPools() = playerCommand { player ->

        var errors = false

        for (pool in BountifulContent.Pools) {
            for (poolEntry in pool) {
                val dummy = BountyData()
                val data = poolEntry.toEntry()
                data.let {
                    when {
                        it.type.isObj -> dummy.objectives.add(it)
                        it.type.isReward -> dummy.rewards.add(it)
                        else -> throw Exception("Pool Data was neither an entry nor a reward!: '${poolEntry.type.name}', '${poolEntry.content}'")
                    }
                }

                if (!dummy.verifyValidity(player)) {
                    player.sendMessage(LiteralText("    - Source Pool: '${pool.id}'").formatted(Formatting.RED).formatted(Formatting.ITALIC), false)
                    errors = true
                }
            }
        }


        if (errors) {
            player.sendMessage(
                LiteralText("Some items are invalid. See above for details")
                    .formatted(Formatting.DARK_RED)
                    .formatted(Formatting.BOLD),
                false
            )
        } else {
            player.sendMessage(
                LiteralText("All Pool data has been verified successfully.")
                    .formatted(Formatting.GOLD),
                false
            )
        }

        1
    }



}