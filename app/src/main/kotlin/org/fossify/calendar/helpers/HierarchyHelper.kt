package org.fossify.calendar.helpers

data class HierarchyInfo(val level: Int, val tags: List<String>)

object HierarchyHelper {
    fun extractHierarchy(description: String?): HierarchyInfo? {
        if (description == null) return null
        
        // Match start of string, one or more #, then content, then at least one #
        val match = Regex("^(#+)(.+)(#+)").find(description) ?: return null
        val leadingHashes = match.groupValues[1]
        val content = match.groupValues[2]
        
        val level = leadingHashes.length
        // Split content by '#' to extract multiple tags, filtering out empty ones
        val tags = content.split('#').map { it.trim() }.filter { it.isNotEmpty() }
        
        return if (tags.isNotEmpty()) {
            HierarchyInfo(level, tags)
        } else {
            null
        }
    }

    /**
     * Logic for finding parent according to the "Nearest Parent Principle":
     * - Strict Matching: Sub L_n tags[0..n-2] == Parent L_{n-1} tags[0..n-2]
     * - Suffix Matching: If L_n lacks prefix tags, its last tag must match parent L_{n-1} last tag.
     */
    fun isParentOf(parent: HierarchyInfo, sub: HierarchyInfo): Boolean {
        if (sub.level != parent.level + 1) return false
        
        // Strict matching for multi-tag paths
        if (sub.tags.size >= parent.level && parent.tags.size >= parent.level) {
            val subPrefix = sub.tags.take(parent.level - 1)
            val parentPrefix = parent.tags.take(parent.level - 1)
            if (subPrefix == parentPrefix && sub.tags[parent.level - 1] == parent.tags.last()) {
                return true
            }
        }
        
        // Fallback: Suffix matching (Nearest Parent principle)
        return sub.tags.last() == parent.tags.last()
    }
}
