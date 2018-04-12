package tel.schich.virtualscanner

enum class State { Release, Press }

data class Action(val key: Int, val state: State)

data class Options(val normalizeLineBreaks: Boolean = true,
                   val envelope: Pair<List<Action>, List<Action>>? = null,
                   val keyboardLayout: Map<Char, List<Action>>)


fun compile(input: String, options: Options): List<Action>? {

    return if (input.any { c -> !options.keyboardLayout.containsKey(c) }) null
    else {
        val raw = input.flatMap { c -> options.keyboardLayout[c] ?: listOf() }
        if (options.envelope == null) raw
        else options.envelope.first + raw + options.envelope.second
    }
}