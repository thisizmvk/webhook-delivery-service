package com.webhook.delivery.store

import com.webhook.delivery.domain.Delivery
import java.time.Instant
import java.util.UUID

interface DeliveryRepository {
    /**
     * Stores [delivery] unless another delivery already uses the same idempotency key,
     * in which case the existing delivery is returned instead. Must be atomic.
     */
    fun createIfAbsent(delivery: Delivery): Delivery

    fun save(delivery: Delivery): Delivery

    fun findById(id: UUID): Delivery?

    fun findDue(now: Instant, limit: Int): List<Delivery>
}
