package tel.schich.virtualscanner

import com.beust.klaxon.Klaxon
import java.io.File

fun loadLayout(json: Klaxon, path: String): Map<Char, List<Action>>? {

    val file = File(path)
    return if (!file.canRead()) null
    else {
        val layoutMap = json.parse<Map<String, List<Action>>>(file)
        layoutMap?.map { (k, v) -> Pair(k.first(), v) }?.toMap()
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
