package tel.schich.virtualscanner

import tel.schich.virtualscanner.Action.Do.Press
import tel.schich.virtualscanner.Action.Do.Release
import java.awt.event.KeyEvent.*
import java.lang.Character.*

data class Action(val key: String, val children: List<Action>) {

    enum class Do {
        Release, Press
    }
}

data class Options(val normalizeLineBreaks: Boolean = true,
                   val allowNesting: Boolean = true,
                   val allowSpecial: Boolean = true,
                   val envelope: String? = null,
                   val envelopeKey: String = "CONTENT",
                   val envelopeOptions: Options? = null)


fun compile(input: String, options: Options): List<Pair<Int, Action.Do>> {

    val actions = parseSequence(input, options)
    val finalActions = if (actions != null) {
        if (options.envelope != null) {
            val envelope = parseSequence(options.envelope, options.envelopeOptions ?: Options())
            replaceContent(envelope, actions, options.envelopeKey)
        } else actions
    } else null

    return if (finalActions == null) emptyList()
    else generateSequence(finalActions)
}

fun replaceContent(envelope: List<Action>?, content: List<Action>, key: String): List<Action> {
    return if (envelope == null || envelope.isEmpty()) emptyList()
    else envelope.flatMap { action ->
        val result = if (action.key == key) content
        else {
            if (action.children.isEmpty()) listOf(action)
            else {
                val newChildren = replaceContent(action.children, content, key)
                if (newChildren == action.children) listOf(action)
                else listOf(Action(action.key, newChildren))
            }
        }
        if (result == envelope) envelope
        else result
    }
}

fun generateSequence(actions: List<Action>): List<Pair<Int, Action.Do>> {
    return actions.flatMap { a ->
        val (prefix, suffix) = mapActionToPrefixSuffix(a)
        prefix + generateSequence(a.children) + suffix
    }
}

fun mapActionToPrefixSuffix(action: Action): Pair<List<Pair<Int, Action.Do>>, List<Pair<Int, Action.Do>>> {

    val fKeys = (1..24).map { i -> "F$i" }.toSet()
    val special = mapOf(
            Pair("ENTER",     VK_ENTER),
            Pair("RETURN",    VK_ENTER),
            Pair("BACKSPACE", VK_BACK_SPACE),
            Pair("SPACE",     VK_SPACE),
            Pair("CTRL",      VK_CONTROL),
            Pair("SHIFT",     VK_SHIFT),
            Pair("ALT",       VK_ALT),
            Pair("ALTGR",     VK_ALT_GRAPH),
            Pair("CONTEXT",   VK_CONTEXT_MENU),
            Pair("WIN",       VK_META),
            Pair("TAB",       VK_TAB)
    )
    val charKeys = mapOf(
            Pair('\\', arrayOf(key('\\'))),
            Pair('|',  arrayOf(VK_SHIFT, key('|'))),
            Pair('/',  arrayOf(VK_SHIFT, key('/'))),
            //Pair('?',  arrayOf()),
            Pair('!',  arrayOf(VK_SHIFT, key('!'))),
            Pair(' ',  arrayOf(key(' '))),
            Pair('.',  arrayOf(key('.'))),
            Pair(':',  arrayOf(VK_SHIFT, key(':'))),
            Pair(';',  arrayOf(VK_SHIFT, key(';'))),
            Pair('-',  arrayOf(key('-'))),
            Pair('_',  arrayOf(VK_SHIFT, key('_'))),
            Pair('@',  arrayOf(VK_SHIFT, key('@'))),
            Pair('#',  arrayOf(VK_SHIFT, key('#'))),
            Pair('^',  arrayOf(VK_SHIFT, key('^'))),
            Pair('=',  arrayOf(key('='))),
            Pair('+',  arrayOf(VK_SHIFT, key('+'))),
            Pair('$',  arrayOf(VK_SHIFT, key('$'))),
            Pair('&',  arrayOf(VK_SHIFT, key('&'))),
            Pair('*',  arrayOf(VK_SHIFT, key('*'))),
            Pair('\'', arrayOf(VK_QUOTE)),
            Pair('"',  arrayOf(VK_SHIFT, VK_QUOTE)),
            Pair('.',  arrayOf(key('.'))),
            Pair('>',  arrayOf(VK_SHIFT, key('>'))),
            Pair(',',  arrayOf(key(','))),
            Pair('<',  arrayOf(VK_SHIFT, key('<'))),
            Pair('(',  arrayOf(VK_SHIFT, key('('))),
            Pair(')',  arrayOf(VK_SHIFT, key(')'))),
            Pair('[',  arrayOf(key('['))),
            Pair('{',  arrayOf(VK_SHIFT, key('{'))),
            Pair(']',  arrayOf(key(']'))),
            Pair('}',  arrayOf(VK_SHIFT, key('}'))),
            Pair('%',  arrayOf(VK_SHIFT, key('%'))),
            Pair('\t', arrayOf(VK_TAB)),
            Pair('\n', arrayOf(VK_ENTER)),
            Pair('\r', arrayOf(VK_ENTER))
    )

    getExtendedKeyCodeForChar('$'.toInt())

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
        next >= s.length -> Pair(next, Action(key, emptyList()))
        options.allowNesting && s[next] == '(' -> {
            val (afterNested, nested) = parseNested(s, next, options)
            Pair(afterNested, Action(key, nested))
        }
        else -> Pair(next, Action(key, emptyList()))
    }
}

fun parseKey(s: String, offset: Int, options: Options): Pair<Int, String> {
    return if (options.allowSpecial && s[offset] == '{') {
        val (next, key) = specialKey(s, offset)
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

fun specialKey(s: String, offset: Int): Pair<Int, String> {
    val close = s.indexOf('}', offset + 1)
    return if (close == -1) Pair(offset + 1, "{")
    else Pair(close + 1, s.substring(offset + 1, close))
}

fun simpleKey(s: String, offset: Int): Pair<Int, String> {
    return when {
        offset == s.length - 1 -> Pair(s.length, "" + s[offset])
        s[offset] == '\\' -> Pair(offset + 2, "" + mapEscape(s[offset + 1]))
        else -> Pair(offset + 1, "" + s[offset])
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