package tel.schich.virtualscanner

import tel.schich.virtualscanner.Action.Do.Press
import tel.schich.virtualscanner.Action.Do.Release
import java.awt.event.KeyEvent.*
import java.lang.Character.*

sealed class Key
class Exact(val char: Char) : Key()
class Named(val name: String) : Key()
class Code(val num: Int) : Key()
object Content : Key()

data class Action(val key: Key, val children: List<Action>) {

    enum class Do {
        Release, Press
    }
}

data class Options(val normalizeLineBreaks: Boolean = true,
                   val allowNesting: Boolean = true,
                   val allowSpecial: Boolean = true,
                   val envelope: String? = null,
                   val envelopeKey: String = "CONTENT",
                   val envelopeOptions: Options? = null,
                   val keyboardLayout: KeyboardLayout = EmptyLayout)


fun compile(input: String, options: Options): List<Pair<Int, Action.Do>> {

    val actions = parseSequence(input, options)
    val finalActions = if (actions != null) {
        if (options.envelope != null) {
            val envelope = parseSequence(options.envelope, options.envelopeOptions ?: Options())
            replaceContent(envelope, actions)
        } else actions
    } else null

    return if (finalActions == null) emptyList()
    else generateSequence(finalActions, options.keyboardLayout)
}

fun replaceContent(envelope: List<Action>?, content: List<Action>): List<Action> {
    return if (envelope == null || envelope.isEmpty()) emptyList()
    else envelope.flatMap { action ->
        val result = if (action.key == Content) content
        else {
            if (action.children.isEmpty()) listOf(action)
            else {
                val newChildren = replaceContent(action.children, content)
                if (newChildren == action.children) listOf(action)
                else listOf(Action(action.key, newChildren))
            }
        }
        if (result == envelope) envelope
        else result
    }
}

fun generateSequence(actions: List<Action>, layout: KeyboardLayout): List<Pair<Int, Action.Do>> {
    return actions.flatMap { a ->
        val (prefix, suffix) = mapActionToAction(a, layout)
        prefix + generateSequence(a.children, layout) + suffix
    }
}

fun mapActionToAction(action: Action, layout: KeyboardLayout): List<Pair<Int, Action.Do>> {

    getExtendedKeyCodeForChar('$'.toInt())

    val k = action.key
    val actions: List<Action>? = when (k) {
        is Exact -> {
            layout.layout["" + k.char]
        }
        is Named -> {
            layout.layout[k.name]
        }
        is Code -> {
            listOf<Action>(Action(k, listOf()))
        }
        else -> {
            listOf()
        }
    }

    return null;

    return if (action.key.length == 1) {
        val c = action.key[0]
        val fallback = getExtendedKeyCodeForChar(c.toInt())
        when {
            isLetterOrDigit(c) -> parseLetter(c)
            charKeys.containsKey(c) -> pressAndRelease(charKeys.getValue(c))
            c == '$' -> pressAndRelease(VK_SHIFT, VK_4)
            fallback != VK_UNDEFINED -> pressAndRelease(fallback)
            else -> Pair(emptyList(), emptyList())
        }
    } else {
        val key = action.key.toUpperCase()
        when {
            fKeys.contains(key) -> parseFKey(key)
            special.containsKey(key) -> pressAndRelease(special.getValue(key))
            else -> Pair(emptyList(), emptyList())
        }
    }
}

fun key(c: Char): Int {
    return getExtendedKeyCodeForChar(c.toInt())
}

fun parseFKey(key: String): Pair<List<Pair<Int, Action.Do>>, List<Pair<Int, Action.Do>>> {
    val num = key.substring(1).toInt()
    return if (num < 1 || num > 24) Pair(emptyList(), emptyList())
    else {
        if (num <= 12) pressAndRelease(VK_F1 + (num - 1))
        else pressAndRelease(VK_F13 + (num - 13))
    }
}

fun parseLetter(c: Char): Pair<List<Pair<Int, Action.Do>>, List<Pair<Int, Action.Do>>> {
    return when {
        isLowerCase(c) -> pressAndRelease(VK_A + (c - 'a'))
        isUpperCase(c) -> pressAndReleaseShifting(VK_A + (c - 'A'))
        else -> pressAndRelease(VK_0 + (c - '0'))
    }
}

fun pressAndRelease(vararg key: Int): Pair<List<Pair<Int, Action.Do>>, List<Pair<Int, Action.Do>>> {
    return pressAndRelease(key.toTypedArray())
}

fun pressAndRelease(key: Array<Int>): Pair<List<Pair<Int, Action.Do>>, List<Pair<Int, Action.Do>>> {
    val press = key.map { k -> Pair(k, Press) }
    val release = key.reversed().map { k -> Pair(k, Release) }
    return Pair(press, release)
}

fun pressAndReleaseShifting(key: Int): Pair<List<Pair<Int, Action.Do>>, List<Pair<Int, Action.Do>>> {
    val (pre, post) = pressAndRelease(key)

    return Pair(listOf(Pair(VK_SHIFT, Press)) + pre, post + Pair(VK_SHIFT, Release))
}

fun parseSequence(s: String, options: Options): List<Action>? {
    val normalized = if (options.normalizeLineBreaks) s.replace("\r\n", "\n")
                     else s

    val (next, actions) = parseActions(normalized, 0, emptyList(), options)

    return if (next == normalized.length) actions
    else null
}

tailrec fun parseActions(s: String, offset: Int, actions: List<Action>, options: Options): Pair<Int, List<Action>> {
    val (next, action) = parseAction(s, offset, options)
    val newActions = actions + action
    return when {
        next >= s.length -> Pair(next, newActions)
        s[next] == ')' -> Pair(next, newActions)
        else -> parseActions(s, next, newActions, options)
    }
}

fun parseAction(s: String, offset: Int, options: Options): Pair<Int, Action> {
    val (next, key) = parseKey(s, offset, options)
    return when {
        options.allowNesting && s[next] == '(' -> {
            val (afterNested, nested) = parseNested(s, next, options)
            Pair(afterNested, Action(key, nested))
        }
        else -> Pair(next, Action(key, emptyList()))
    }
}

fun parseKey(s: String, offset: Int, options: Options): Pair<Int, Key> {
    return if (options.allowSpecial && s[offset] == '{') {
        val (next, key) = specialKey(s, offset, options)
        Pair(next, key)
    } else {
        val (next, key) = simpleKey(s, offset)
        Pair(next, key)
    }
}

fun parseNested(s: String, offset: Int, options: Options): Pair<Int, List<Action>> {
    val (next, actions) = parseActions(s, offset + 1, emptyList(), options)
    return if (next >= s.length) Pair(next, actions)
    else Pair(next + 1, actions)
}

fun specialKey(s: String, offset: Int, options: Options): Pair<Int, Key> {
    val close = s.indexOf('}', offset + 1)
    return if (close == -1) Pair(offset + 1, Exact('{'))
    else {
        val name = s.substring(offset + 1, close)
        val key = when {
            name == options.envelopeKey -> Content
            name.all { c -> c.isDigit() } -> Code(name.toInt())
            else -> Named(name)
        }
        Pair(close + 1, key)
    }
}

fun simpleKey(s: String, offset: Int): Pair<Int, Key> {
    return when {
        offset == s.length - 1 -> Pair(s.length, Exact(s[offset]))
        s[offset] == '\\' -> Pair(offset + 2, Exact(mapEscape(s[offset + 1])))
        else -> Pair(offset + 1, Exact(s[offset]))
    }
}

fun mapEscape(c: Char): Char {
    return when (c) {
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'

        else -> c
    }
}