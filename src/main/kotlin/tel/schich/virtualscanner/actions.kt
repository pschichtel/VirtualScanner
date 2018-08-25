package tel.schich.virtualscanner

import java.nio.charset.StandardCharsets

val DefaultEncodingHint: String = StandardCharsets.UTF_8.name()

enum class State { Released, Pressed }

data class Action(val key: Int, val state: State)

data class Options(val encodingHint: String = DefaultEncodingHint,
                   val normalizeLinebreaks: Boolean = true,
                   val envelope: Pair<List<Action>, List<Action>>? = null,
                   val keyboardLayout: Map<Char, List<Action>>)


fun compile(input: String, options: Options): List<Action>? {

    val processedInput =
            if (options.normalizeLinebreaks) input.replace("(\r\n|\r)".toRegex(), "\n")
            else input

    val missingMappings = processedInput.filter { c -> !options.keyboardLayout.containsKey(c) }.toSet()
    return if (missingMappings.isNotEmpty()) {
        println("Missing character mappings: $missingMappings")
        null
    } else {
        val raw = processedInput.flatMap { c -> options.keyboardLayout[c] ?: listOf() }
        if (options.envelope == null) raw
        else options.envelope.first + raw + options.envelope.second
    }
}
