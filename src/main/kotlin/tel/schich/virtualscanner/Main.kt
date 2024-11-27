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

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.StringUtils
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.multi.MultipleBarcodeReader
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import notify.Notify
import java.awt.Image
import java.awt.Robot
import java.awt.im.InputContext
import java.awt.image.BufferedImage
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.*
import java.util.Locale
import kotlin.system.exitProcess
import com.google.zxing.Result as ZxingResult

const val ApplicationName = "VirtualScanner"

typealias Actions = List<Action>
typealias KeyLayout = Map<Char, Actions>

@Serializable
data class Config(val layout: String = "de_DE_ISO.json",
                  val encodingHint: String = DefaultEncodingHint,
                  val normalizeLinebreaks: Boolean = true,
                  val prefix: Actions = listOf(),
                  val suffix: Actions = listOf(),
                  val delay: Long = 1000,
                  val charset: String = "")

fun main(args: Array<String>) {

    InputContext.getInstance().selectInputMethod(Locale.ENGLISH)
    val mode = args.getOrElse(0) { "screen" }.lowercase()

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    val config = load(json, args.getOrElse(1) {"config.json"}) ?: Config()
    val layout: KeyLayout = load(json, config.layout) ?: mapOf()
    val options = Options(
            encodingHint = config.encodingHint,
            envelope = Pair(config.prefix, config.suffix),
            keyboardLayout = layout,
            normalizeLinebreaks = config.normalizeLinebreaks)
    val robot = Robot()

    when (mode) {
        "screen" -> scanScreen(options, robot, config.delay)
        "clipboard" -> monitorClipboard(options, robot, config.delay)
        "layout" -> createLayout(json, layout, config.charset, config.layout)
        else -> {
            System.err.println("available modes: screen, clipboard, layout")
            exitProcess(1)
        }
    }
}


fun reader(encodingHint: String): (Image) -> Array<ZxingResult> {
    val reader: MultipleBarcodeReader = GenericMultipleBarcodeReader(MultiFormatReader())
    val hints = mapOf(
            Pair(DecodeHintType.CHARACTER_SET, encodingHint)
    )

    fun bufferImage(img: Image): BufferedImage {
        return if (img is BufferedImage) {
            img
        } else {
            val buf = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB)
            val g = buf.graphics
            g.drawImage(img, 0, 0, null)
            g.dispose()
            buf
        }
    }

    return { image: Image ->
        try {
            reader.decodeMultiple(BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(bufferImage(image)))), hints)
        } catch (e: NotFoundException) {
            emptyArray()
        }
    }
}

fun handleResults(robot: Robot, options: Options, results: Array<ZxingResult>, delay: Long): Boolean = when {
    results.size == 1 -> {
        val result = results.first()
        val content = guessEncodingAndReencode(result.text)
        val actions = compile(content, options)
        Notify.info(ApplicationName, "Detected barcode!")
        if (actions == null) {
            notify("Failed to parse code! Is the keyboard layout incomplete?")
            false
        } else {
            println(actions)
            Thread.sleep(delay)
            act(robot, actions)
            true
        }
    }
    results.size > 1 -> {
        notify("Multiple barcodes found, which one should I use?")
        false
    }
    else -> {
        notify("No barcodes detected!")
        false
    }
}


fun guessEncodingAndReencode(code: String): String {
    val bytes = code.toByteArray(Charsets.ISO_8859_1)

    val ff = 0xFF.toByte()
    val fe = 0xFE.toByte()
    val ze = 0.toByte()

    fun hasUTF32BOM(b: ByteArray): Boolean {
        return (b[0] == ff && b[1] == fe && b[2] == ze && b[3] == ze) ||
               (b[0] == ze && b[1] == ze && b[2] == fe && b[3] == ff)
    }

    fun hasUTF16BOM(b: ByteArray): Boolean {
        return (b[0] == ff && b[1] == fe) ||
               (b[0] == fe && b[1] == ff)
    }

    if (bytes.size > 4 && hasUTF32BOM(bytes)) {
        return String(bytes, Charsets.UTF_32)
    }

    if (bytes.size > 2 && hasUTF16BOM(bytes)) {
        return String(bytes, Charsets.UTF_16)
    }

    val zxingGuess = StringUtils.guessEncoding(bytes, null)
    return String(bytes, Charset.forName(zxingGuess))
}

fun act(r: Robot, actions: List<Action>) {
    for (action in actions) {
        act(r, action)
        Thread.sleep(10)
    }
}

fun act(r: Robot, action: Action) {
    try {
        when (action.state) {
            State.Released -> r.keyRelease(action.key)
            State.Pressed -> r.keyPress(action.key)
        }
    } catch (e: IllegalArgumentException) {
        throw RuntimeException("Unable to emit key stroke (${action.key}, ${action.state}): ${e.message}", e)
    }
}

inline fun <reified T : Any> load(json: Json, path: String): T? {
    val fromFile = read<T>(json, Paths.get(path))
    if (fromFile != null) {
        return fromFile
    }

    val fromJar = read<T>(json, Thread.currentThread().contextClassLoader.getResourceAsStream(path))
    if (fromJar != null) {
        return fromJar
    }

    return null
}

inline fun <reified T : Any> read(json: Json, file: Path): T? {
    return if (!Files.isReadable(file)) null
    else maybe(file) { json.decodeFromString(Files.readString(it)) }
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T : Any> read(json: Json, inStream: InputStream?): T? {
    return maybe(inStream) { json.decodeFromStream(it) }
}

inline fun <I : Any, O : Any> maybe(input: I?, f: (I) -> O): O? {
    return if (input == null) null
    else try {
        f(input)
    } catch (ex: Exception) {
        ex.printStackTrace(System.err)
        null
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun generateLayoutFile(json: Json, path: String, table: Map<Char, List<Action>>) {
    Files.newOutputStream(Paths.get(path), WRITE, SYNC, DSYNC, TRUNCATE_EXISTING, CREATE).use {
        json.encodeToStream(table, it)
    }
}

fun stringifyActions(actions: List<Action>): String {

    fun stateChar(state: State): Char {
        return when (state) {
            State.Pressed -> '+'
            State.Released -> '-'
        }
    }

    fun normalizeActions(input: List<Action>, i: Int = 0, output: List<Pair<Int, Char>> = listOf()): List<Pair<Int, Char>> {
        return if (i >= input.size) output
        else {
            if (i + 1 < input.size && input[i].key == input[i + 1].key && input[i].state == State.Pressed && input[i + 1].state == State.Released) {
                normalizeActions(input, i + 2, output + Pair(input[i].key, '~'))
            } else {
                normalizeActions(input, i + 1, output + Pair(input[i].key, stateChar(input[i].state)))
            }
        }
    }

    return normalizeActions(actions).joinToString("") { (c, s) -> "$s$c"}
}

fun notify(message: String) {
    println(message)
    Notify.notify(ApplicationName, message)
}