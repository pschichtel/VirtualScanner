package tel.schich.virtualscanner

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Paths
import java.lang.Thread.currentThread

fun loadLayout(file: String): Map<Char, List<Action>>? {
    val path = Paths.get(file)

    val lines = when {
        Files.isReadable(path) -> Files.readAllLines(path, UTF_8)
        else -> currentThread().contextClassLoader.getResourceAsStream(file)?.bufferedReader(UTF_8)?.readLines()
    }
    return lines?.fold(mapOf())  { mapping, line ->
        val trimmed = line.trim()
        val sepPos = trimmed.indexOf('=', 1)
        if (sepPos != 1) mapping
        else {
            val key = trimmed[0]
            val actions = parseActionSpec(trimmed.substring(sepPos + 1))
            mapping + Pair(key, actions)
        }
    }
}

fun parseActionSpec(spec: String): List<Action> {
    return parseActions(spec, 0, listOf())
}

tailrec fun parseActions(spec: String, offset: Int, acc: List<Action>): List<Action> {
    return if (offset < spec.length) {
        val states = when (spec[offset]) {
            '+' -> listOf(State.Press)
            '-' -> listOf(State.Release)
            '~' -> listOf(State.Press, State.Release)
            else -> listOf()
        }
        val (code, finalOffset) = parseCode(spec, offset + 1)
        val actions = states.map { s -> Action(code, s) }
        parseActions(spec, finalOffset, acc + actions)
    } else acc
}

fun parseCode(spec: String, offset: Int): Pair<Int, Int> {
    tailrec fun parse(offset: Int, acc: String): Pair<Int, Int> {
        return if (offset < spec.length) {
            val current = spec[offset]
            when {
                Character.isDigit(current) -> parse(offset + 1, acc + current)
                else -> Pair(acc.toInt(), offset)
            }
        } else {
            if (acc.isEmpty()) Pair(offset, 0)
            else Pair(acc.toInt(), offset)
        }
    }

    return parse(offset, "")
}
