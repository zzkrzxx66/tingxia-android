package com.tingxia.app.data.policy

/** Main-thread request ownership. Late completions can never mutate a newer request. */
class LatestRequest<K> {
    data class Ticket<K>(val key: K, val generation: Long)
    private var generation = 0L
    private var current: Ticket<K>? = null
    fun begin(key: K): Ticket<K> = Ticket(key, ++generation).also { current = it }
    fun accepts(ticket: Ticket<K>): Boolean = current == ticket
    fun clear() { current = null; generation++ }
}
