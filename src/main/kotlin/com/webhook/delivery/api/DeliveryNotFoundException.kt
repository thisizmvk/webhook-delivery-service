package com.webhook.delivery.api

import java.util.UUID

class DeliveryNotFoundException(val id: UUID) : RuntimeException("Delivery $id not found")