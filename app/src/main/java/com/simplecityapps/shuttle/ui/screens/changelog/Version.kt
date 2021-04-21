Npackage com.simplecityapps.shuttle.ui.screens.changelog

class Version(val version: String) : Comparable<Version?> {

    init {
        require(version.matches(pattern)) { "Invalid version format" }
    }

    override fun compareTo(other: Version?): Int {
        if (other == null) return 1
        val parts = version.split('.').toTypedArray()
        val otherParts = other.version.split('.').toTypedArray()
        val length = parts.size.coerceAtLeast(otherParts.size)
        for (i in 0 until length) {
            val part = if (i < parts.size) parts[i].toInt() else 0
            val otherPart = if (i < otherParts.size) otherParts[i].toInt() else 0
            if (part < otherPart) return -1
            if (part > otherPart) return 1
        }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Version

        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        return version.hashCode()
    }

    companion object {
        val pattern = Regex("[0-9]+(\\.[0-9]+)*")
    }
}