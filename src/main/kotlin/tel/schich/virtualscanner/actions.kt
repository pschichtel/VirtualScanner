package tel.schich.virtualscanner

import java.awt.event.KeyEvent
import java.lang.Character.*

data class Action(val key: String, val children: List<Action>)

fun generateSequence(actions: List<Action>): List<Pair<Int, KeyAction>> {
    return actions.flatMap { a ->
        val (prefix, suffix) = mapActionToPrefixSuffix(a)
        prefix + generateSequence(a.children) + suffix
    }
}

fun mapActionToPrefixSuffix(action: Action): Pair<List<Pair<Int, KeyAction>>, List<Pair<Int, KeyAction>>> {

    val fKeys = (1..24).map { i -> "F$i" }.toSet()

    val key = action.key.toUpperCase()
    return when {
        fKeys.contains(key) -> parseFKey(key)
        key == "ENTER" -> pressAndRelease(KeyEvent.VK_ENTER)
        key == "BACKSPACE" -> pressAndRelease(KeyEvent.VK_BACK_SPACE)
        key == "SPACE" -> pressAndRelease(KeyEvent.VK_SPACE)
        key == "CTRL" -> pressAndRelease(KeyEvent.VK_CONTROL)
        key == "SHIFT" -> pressAndRelease(KeyEvent.VK_SHIFT)
        key == "ALT" -> pressAndRelease(KeyEvent.VK_ALT)
        key == "ALTGR" -> pressAndRelease(KeyEvent.VK_ALT_GRAPH)
        key == "CONTEXT" -> pressAndRelease(KeyEvent.VK_CONTEXT_MENU)
        key.length == 1 -> {
            val c = key[0]
            if (isLetterOrDigit(c)) parseLetter(c)
            else when (c) {
                '\\' -> pressAndRelease(KeyEvent.VK_BACK_SLASH)
                '/' -> pressAndRelease(KeyEvent.VK_SLASH)
                '!' -> pressAndRelease(KeyEvent.VK_EXCLAMATION_MARK)
                ' ' -> pressAndRelease(KeyEvent.VK_SPACE)
                else -> Pair(emptyList(), emptyList())
            }
        }
        else -> {
            Pair(emptyList(), emptyList())
        }
    }
}

fun parseFKey(key: String): Pair<List<Pair<Int, KeyAction>>, List<Pair<Int, KeyAction>>> {
    val num = key.substring(1).toInt()
    return if (num < 1 || num > 24) Pair(emptyList(), emptyList())
    else {
        if (num <= 12) pressAndRelease(KeyEvent.VK_F1 + (num - 1))
        else pressAndRelease(KeyEvent.VK_F13 + (num - 13))
    }
}

fun parseLetter(c: Char): Pair<List<Pair<Int, KeyAction>>, List<Pair<Int, KeyAction>>> {
    return when {
        isLowerCase(c) -> pressAndRelease(KeyEvent.VK_A + (c - 'a'))
        isUpperCase(c) -> pressAndReleaseShifting(KeyEvent.VK_A + (c - 'A'))
        else -> pressAndRelease(KeyEvent.VK_0 + (c - '0'))
    }
}

fun pressAndRelease(key: Int): Pair<List<Pair<Int, KeyAction>>, List<Pair<Int, KeyAction>>> {
    return Pair(listOf(Pair(key, KeyAction.Press)), listOf(Pair(key, KeyAction.Release)))
}

fun pressAndReleaseShifting(key: Int): Pair<List<Pair<Int, KeyAction>>, List<Pair<Int, KeyAction>>> {
    val (pre, post) = pressAndRelease(key)

    return Pair(listOf(Pair(KeyEvent.VK_SHIFT, KeyAction.Press)) + pre, post + Pair(KeyEvent.VK_SHIFT, KeyAction.Release))
}

fun parseSequence(s: String): List<Action>? {
    val (next, actions) = parseActions(s, 0, emptyList())

    return if (next == s.length) {
        actions
    } else {
        null
    }
}

tailrec fun parseActions(s: String, offset: Int, actions: List<Action>): Pair<Int, List<Action>> {
    val (next, action) = parseAction(s, offset)
    val newActions = actions + action
    return when {
        next >= s.length -> Pair(next, newActions)
        s[next] == ')' -> Pair(next, newActions)
        else -> parseActions(s, next, newActions)
    }
}

fun parseAction(s: String, offset: Int): Pair<Int, Action> {
    val (next, key) = parseKey(s, offset)
    return when {
        next >= s.length -> Pair(next, Action(key, emptyList()))
        s[next] == '(' -> {
            val (afterNested, nested) = parseNested(s, next)
            Pair(afterNested, Action(key, nested))
        }
        else -> Pair(next, Action(key, emptyList()))
    }
}

fun parseKey(s: String, offset: Int): Pair<Int, String> {
    return if (s[offset] == '{') {
        val (next, key) = specialKey(s, offset)
        Pair(next, key)
    } else {
        val (next, key) = simpleKey(s, offset)
        Pair(next, key)
    }
}

fun parseNested(s: String, offset: Int): Pair<Int, List<Action>> {
    val (next, actions) = parseActions(s, offset + 1, emptyList())
    return if (next >= s.length) Pair(next, actions)
    else Pair(next + 1, actions)
}

fun specialKey(s: String, offset: Int): Pair<Int, String> {
    val close = s.indexOf('}', offset + 1)
    return if (close == -1) Pair(offset + 1, "{")
    else Pair(close + 1, s.substring(offset + 1, close))
}

fun simpleKey(s: String, offset: Int): Pair<Int, String> {
    return when {
        offset == s.length - 1 -> Pair(s.length, "" + s[offset])
        s[offset] == '\\' -> Pair(offset + 2, "" + s[offset + 1])
        else -> Pair(offset + 1, "" + s[offset])
    }
}