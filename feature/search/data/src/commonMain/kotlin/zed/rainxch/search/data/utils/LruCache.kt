package zed.rainxch.search.data.utils

class LruCache<K, V>(
    private val maxSize: Int,
) {
    private val map = LinkedHashMap<K, V?>()
    private val order = ArrayDeque<K>()

    fun get(key: K): V? {
        val value = map[key]
        if (value != null || map.containsKey(key)) {
            order.remove(key)
            order.addLast(key)
        }
        return value
    }

    fun put(
        key: K,
        value: V?,
    ) {
        map[key] = value
        order.remove(key)
        order.addLast(key)
        while (order.size > maxSize) {
            val oldest = order.removeFirst()
            map.remove(oldest)
        }
    }

    fun contains(key: K): Boolean = map.containsKey(key)
}
