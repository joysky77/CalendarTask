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

    fun generateId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..4)
            .map { chars.random() }
            .joinToString("")
    }

    /**
     * Prepares the hierarchy strings for creating a sub-item.
     * returns a Pair:
     * - first: The updated parent description if it needed "perfecting" (null if no change).
     * - second: The pre-filled description prefix for the new child item.
     */
    fun prepareHierarchyForSubItem(parentDescription: String?): Pair<String?, String> {
        val description = parentDescription ?: ""
        val match = Regex("^(#+)(.+)(#+)").find(description)
        
        val hierarchy = if (match != null) {
            val level = match.groupValues[1].length
            val content = match.groupValues[2]
            val tags = content.split('#').map { it.trim() }.filter { it.isNotEmpty() }
            if (tags.isNotEmpty()) HierarchyInfo(level, tags) else null
        } else null

        val n = hierarchy?.level ?: 1
        val tags = hierarchy?.tags ?: emptyList()
        val m = tags.size
        
        val remainder = if (match != null) description.substring(match.value.length) else description

        var updatedParentDescription: String? = null
        val currentTags = tags.toMutableList()

        if (hierarchy == null) {
            // Case 1: No hierarchy or doesn't match pattern
            val newId = generateId()
            currentTags.clear()
            currentTags.add(newId)
            updatedParentDescription = ("#$newId# $description").trim()
        } else if (m < n) {
            // Case 2: Incomplete hierarchy (e.g., Level 2 but has only 1 tag)
            val needed = n - m
            repeat(needed) {
                currentTags.add(generateId())
            }
            updatedParentDescription = ("#".repeat(n) + currentTags.joinToString("#") + "#" + remainder).trim()
        }

        // Inheritance and generation for the child
        val childLevel = (hierarchy?.level ?: 1) + 1
        val childId = generateId()
        val childTags = currentTags.toMutableList()
        childTags.add(childId)
        val childDescriptionPrefix = "#".repeat(childLevel) + childTags.joinToString("#") + "#"

        return updatedParentDescription to childDescriptionPrefix
    }
}
