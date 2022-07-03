/*
 * virtual-scanner - Scan barcodes from your screen and emit the content as key strokes.
 * Copyright © 2018 Phillip Schichtel (phillip@schich.tel)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package tel.schich.virtualscanner

val DefaultEncodingHint: String = Charsets.UTF_8.name()

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
