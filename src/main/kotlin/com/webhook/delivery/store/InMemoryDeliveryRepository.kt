package com.webhook.delivery.store

import com.webhook.delivery.domain.Delivery
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
class InMemoryDeliveryRepository : DeliveryRepository {

    private val byId = ConcurrentHashMap<UUID, Delivery>()
    private val idByIdempotencyKey = ConcurrentHashMap<String, UUID>()

    // Synchronized so the key check and the insert happen together. Simple and correct
    // for a single instance; a database would use a unique constraint instead.
    @Synchronized
    override fun createIfAbsent(delivery: Delivery): Delivery {
        val key = delivery.idempotencyKey
        if (key != null) {
            idByIdempotencyKey[key]?.let { existingId -> return byId.getValue(existingId) }
            idByIdempotencyKey[key] = delivery.id
        }
        byId[delivery.id] = delivery
        return delivery
    }

    override fun save(delivery: Delivery): Delivery {
        byId[delivery.id] = delivery
        return delivery
    }

    override fun findById(id: UUID): Delivery? = byId[id]

    override fun findDue(now: Instant, limit: Int): List<Delivery> =
        byId.values
            .filter { it.isDue(now) }
            .sortedBy { it.nextAttemptAt }
            .take(limit)
}