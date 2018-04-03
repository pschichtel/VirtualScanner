package tel.schich.virtualscanner

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class KeyboardLayout(val layout: Map<String, List<Action>>)

val EmptyLayout = KeyboardLayout(mapOf())

fun loadLayout(file: Path, base: Options): Options {
    return if (Files.isReadable(file)) {
        val lines = Files.readAllLines(file, StandardCharsets.UTF_8)

        lines.fold(base)  { options, line ->
            val trimmed = line.trim()
            val sepPos = trimmed.indexOf('=', 1)
            if (sepPos == -1) options
            else {
                val key = trimmed.substring(0, sepPos)
                val actions = parseSequence(trimmed.substring(sepPos + 1), options)
                if (actions != null) {
                    val newLayout = options.keyboardLayout.layout + Pair(key, actions)
                    options.copy(keyboardLayout = KeyboardLayout(newLayout))
                } else base
            }
        }

    } else base
}

