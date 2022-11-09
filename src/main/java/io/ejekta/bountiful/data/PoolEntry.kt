package io.ejekta.bountiful.data

import io.ejekta.bountiful.Bountiful
import io.ejekta.bountiful.bounty.BountyDataEntry
import io.ejekta.bountiful.bounty.BountyRarity
import io.ejekta.bountiful.bounty.CriteriaData
import io.ejekta.bountiful.bounty.types.BountyTypeRegistry
import io.ejekta.bountiful.bounty.types.IBountyType
import io.ejekta.bountiful.config.JsonFormats
import io.ejekta.bountiful.util.getTagItemKey
import io.ejekta.bountiful.util.getTagItems
import io.ejekta.kambrik.ext.identifier
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import net.minecraft.item.Item
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Serializable
class PoolEntry private constructor() {
    var type: @Contextual Identifier = BountyTypeRegistry.NULL.id
    var rarity = BountyRarity.COMMON
    var content = "Nope"
    var name: String? = null
    var icon: @Contextual Identifier? = null // TODO allow custom icons, this works well for criterion
    var translation: String? = null
    var amount = EntryRange(-1, -1)
    var unitWorth = -1000.0
    var weightMult = 1.0
    var timeMult = 1.0
    var repRequired = 0.0
    private val forbids: MutableList<ForbiddenContent> = mutableListOf()

    @Transient val sources: MutableSet<String> = mutableSetOf()

    val typeLogic: IBountyType
        get() = BountyTypeRegistry[type] ?: BountyTypeRegistry.NULL

    val criteria: CriteriaData? = null

    var mystery: Boolean = false

    var nbt: @Contextual NbtCompound? = null

    val worthSteps: List<Double>
        get() = (amount.min..amount.max).map { it * unitWorth }

    fun save(format: Json = JsonFormats.DataPack) = format.encodeToString(serializer(), this)

    private fun getRelatedItems(world: ServerWorld): List<Item>? {
        return when (type) {
            BountyTypeRegistry.ITEM.id -> {
                val tagId = Identifier(content.substringAfter("#"))
                getTagItems(world, getTagItemKey(tagId))
            }
            BountyTypeRegistry.ITEM_TAG.id -> {
                val tagId = Identifier(content)
                getTagItems(world, getTagItemKey(tagId))
            }
            else -> null
        }
    }

    fun toEntry(world: ServerWorld, pos: BlockPos, worth: Double? = null): BountyDataEntry {
        val amt = amountAt(worth)

        val actualContent = if (type == BountyTypeRegistry.ITEM.id && content.startsWith("#")) {
            val tagId = Identifier(content.substringAfter("#"))
            val tags = getTagItems(world, getTagItemKey(tagId))
            if (tags.isEmpty()){
                Bountiful.LOGGER.warn("A pool entry tag has an empty list! $content")
                "minecraft:air"
            } else {
                val chosen = tags.random().identifier.toString()
                chosen
            }
        } else {
            content
        }

        return BountyDataEntry.of(
            world,
            pos,
            type,
            actualContent,
            amountAt(worth),
            amt * unitWorth,
            nbt,
            name,
            translation,
            isMystery = false,
            rarity = rarity,
            criteriaData = criteria
        )
    }

    private fun amountAt(worth: Double? = null): Int {
        var toGive = if (worth != null) {
            max(1, ceil(worth.toDouble() / unitWorth).toInt())
        } else {
            amount.pick()
        }.coerceIn(amount.min..amount.max) // Clamp amount into amount range
        return toGive
    }

    private val worthRange: Pair<Double, Double>
        get() = (amount.min * unitWorth) to (amount.max * unitWorth)

    fun worthDistanceFrom(value: Double): Int {
        val rnge = worthRange
        return if (value >= rnge.first && value <= rnge.second) {
            0
        } else {
            min(abs(rnge.first - value), abs(rnge.second - value)).toInt()
        }
    }

    fun forbids(world: ServerWorld, entry: PoolEntry): Boolean {
        val related = getRelatedItems(world)
        return forbids.any {
            it.type == entry.type && it.content == entry.content
        } || (related != null
                    && related.isNotEmpty()
                    && related.any { it.identifier.toString() == entry.content }
                )
    }

    fun forbidsAny(world: ServerWorld, entries: List<PoolEntry>): Boolean {
        return entries.any { forbids(world, it) }
    }

    @Serializable
    class EntryRange(val min: Int, val max: Int) {
        fun pick(): Int = (min..max).random()
        override fun toString() = "[$min - $max]"
    }

    @Serializable
    class ForbiddenContent(val type: @Contextual Identifier, val content: String)

    companion object {

        // With encodeDefaults = false, we need a separate constructor
        fun create() = PoolEntry().apply {
            type = BountyTypeRegistry.ITEM.id
            amount = EntryRange(1, 1)
            content = "NO_CONTENT"
            unitWorth = 100.0
        }

    }

}