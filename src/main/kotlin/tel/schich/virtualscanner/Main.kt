package tel.schich.virtualscanner

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.StringUtils
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.multi.MultipleBarcodeReader
import notify.Notify
import java.awt.Image
import java.awt.Robot
import java.awt.im.InputContext
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*

const val ApplicationName = "VirtualScanner"

typealias Actions = List<Action>
typealias KeyLayout = Map<Char, Actions>

@JsonIgnoreProperties(ignoreUnknown = true)
data class Config(val layout: String = "de_DE_ISO.json",
                  val encodingHint: String = DefaultEncodingHint,
                  val normalizeLinebreaks: Boolean = true,
                  val prefix: Actions = listOf(),
                  val suffix: Actions = listOf(),
                  val delay: Long = 1000,
                  val charset: String = "")

fun main(args: Array<String>) {

    InputContext.getInstance().selectInputMethod(Locale.ENGLISH)
    val mode = args.getOrElse(0) { "screen" }.toLowerCase()

    val json = jacksonObjectMapper()
        .enable(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)
        .enable(SerializationFeature.INDENT_OUTPUT)

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
            System.exit(1)
        }
    }
}


fun reader(encodingHint: String): (Image) -> Array<Result> {
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

fun handleResults(robot: Robot, options: Options, results: Array<Result>, delay: Long): Boolean {
    return when {
        results.size == 1 -> {
            val result = results.first()
            val content = guessEncodingAndReencode(result.text)
            println("Detected: '$content'")
            val actions = compile(content, options)
            Notify.info(ApplicationName, "Detected barcode!")
            return if (actions == null) {
                System.err.println("Failed to parse code! Is the keyboard layout incomplete?")
                false
            } else {
                println(actions)
                Thread.sleep(delay)
                act(robot, actions)
                true
            }
        }
        results.isEmpty() -> {
            System.err.println("No barcodes detected!")
            false
        }
        results.size > 1 -> {
            System.err.println("Multiple barcodes sound, which one should I use?")
            false
        }
        else -> {
            System.err.println("Did not find any barcodes to scan!")
            false
        }
    }
}

fun guessEncodingAndReencode(code: String): String {
    val bytes = code.toByteArray(StandardCharsets.ISO_8859_1)

    if (bytes.size > 4) {
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() && bytes[2] == 0.toByte() && bytes[3] == 0.toByte()) {
            return String(bytes, Charset.forName("UTF_32"))
        }
        if (bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0xFE.toByte() && bytes[3] == 0xFF.toByte()) {
            return String(bytes, Charset.forName("UTF_32"))
        }
    }

    if (bytes.size > 2) {
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, StandardCharsets.UTF_16)
        }
        if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, StandardCharsets.UTF_16)
        }
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

inline fun <reified T : Any> load(json: ObjectMapper, path: String): T? {
    val fromFile = read<T>(json, File(path))
    if (fromFile != null) {
        return fromFile
    }

    val fromJar = read<T>(json, Thread.currentThread().contextClassLoader.getResourceAsStream(path))
    if (fromJar != null) {
        return fromJar
    }

    return null
}

inline fun <reified T : Any> read(json: ObjectMapper, file: File): T? {
    return if (!file.canRead()) null
    else maybe(file) { json.readValue<T>(it) }
}

inline fun <reified T : Any> read(json: ObjectMapper, inStream: InputStream?): T? {
    return maybe(inStream) { json.readValue<T>(it) }
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

fun generateLayoutFile(json: ObjectMapper, path: String, table: Map<Char, List<Action>>) {
    val writer = Files.newBufferedWriter(Paths.get(path), Charsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.SYNC, StandardOpenOption.DSYNC, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
    writer.use {
        json.writeValue(it, table)
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
