package `in`.xnnyygn.bittorrent.process

interface ProcessName {
}

class StringProcessName(val value: String) : ProcessName

class ProcessLocation(val parts: List<ProcessName>) {
    val name: ProcessName
        get() = parts.last()

    fun append(name: ProcessName): ProcessLocation {
        val newNames = mutableListOf<ProcessName>()
        newNames.addAll(parts)
        newNames.add(name)
        return ProcessLocation(newNames)
    }
}

object ProcessLocationResolver {
    fun fromString(string: String): ProcessLocation {
        return ProcessLocation(emptyList())
    }
}