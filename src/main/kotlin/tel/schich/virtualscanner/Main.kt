package tel.schich.virtualscanner

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.multi.MultipleBarcodeReader
import java.awt.*
import java.awt.datatransfer.*
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.im.InputContext
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel

@JsonIgnoreProperties(ignoreUnknown = true)
data class Config(val layout: String = "de_DE_ISO.json",
                  val prefix: List<Action> = listOf(),
                  val suffix: List<Action> = listOf(),
                  val delay: Long = 1000,
                  val charset: String = "")

fun main(args: Array<String>) {

    InputContext.getInstance().selectInputMethod(Locale.ENGLISH)
    val mode = args.getOrElse(0, { "screen" }).toLowerCase()

    val json = jacksonObjectMapper()
        .enable(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)
        .enable(SerializationFeature.INDENT_OUTPUT)

    val config = load(json, listOf(args.getOrElse(1, {"config.json"}), "defaults.json"), Config())
    val layout = load(json, listOf(config.layout, "default-layout.json"), mapOf<Char, List<Action>>())
    val options = Options(envelope = Pair(config.prefix, config.suffix), keyboardLayout = layout)
    val robot = Robot()

    when (mode) {
        "screen" -> scanScreen(options, robot, config.delay)
        "clipboard" -> monitorClipboard(options, robot, config.delay)
        "layout" -> createLayout(json, config.charset, config.layout)
        else -> {
            System.err.println("available modes: screen, clipboard, layout")
            System.exit(1)
        }
    }
}

fun createLayout(json: ObjectMapper, charset: String, filePath: String) {
    System.setProperty("awt.useSystemAAFontSettings","on")
    System.setProperty("swing.aatext", "true")

    val frame = JFrame("Layout Creator")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.bounds = Rectangle(0, 0, 400, 400)
    frame.setLocationRelativeTo(null)

    val charKeyTable = mutableMapOf<Char, List<Action>>()
    val currentRecording = mutableListOf<Action>()
    val currentlyPressed = mutableSetOf<Int>()

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { e ->
        if (e.id == KeyEvent.KEY_PRESSED && !currentlyPressed.contains(e.keyCode) || e.id == KeyEvent.KEY_RELEASED) {
            val state = if (e.id == KeyEvent.KEY_PRESSED) {
                currentlyPressed.add(e.keyCode)
                State.Pressed
            } else {
                currentlyPressed.remove(e.keyCode)
                State.Released
            }

            currentRecording.add(Action(e.keyCode, state))
        }
        false
    }

    val layout = GridLayout(3, 3)
    val panel = JPanel(layout)
    val children = Array(layout.columns * layout.rows, { Panel() })
    children.forEach { c -> panel.add(c) }

    val label = JLabel()

    var charsetPosition = 0
    var currentChar: Char = charset[charsetPosition]

    label.font = Font("Verdana", Font.BOLD, 40)
    label.text = stringifyChar(currentChar)
    children[4].add(label)

    val resetButton = JButton()
    resetButton.text = "Reset"
    resetButton.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
            if (e?.button == MouseEvent.BUTTON1) {
                currentRecording.clear()
            }
        }
    })
    children[6].add(resetButton)

    val nextButton = JButton()
    nextButton.text = "Next"
    nextButton.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
            if (e?.button == MouseEvent.BUTTON1) {
                charsetPosition++
                charKeyTable[currentChar] = currentRecording.toList()
                currentRecording.clear()

                if (charsetPosition >= charset.length) {
                    frame.dispose()
                    println(charKeyTable)
                    generateLayoutFile(json, filePath, charKeyTable)
                } else {
                    currentChar = charset[charsetPosition]
                    label.text = stringifyChar(currentChar)
                    if (charsetPosition + 1 >= charset.length) {
                        nextButton.text = "Complete"
                    }
                }
            }
        }
    })
    children[8].add(nextButton)

    frame.add(panel)
    frame.isVisible = true
}

fun stringifyChar(c: Char): String {
    return when (c) {
        '\n' -> "Linebreak"
        '\t' -> "Tab"
        ' ' -> "Space"
        else -> c.toString()
    }
}

fun monitorClipboard(options: Options, robot: Robot, delay: Long) {

    Thread {
        val reader = reader()
        val sysClipboard = Toolkit.getDefaultToolkit().systemClipboard

        val owner = object : ClipboardOwner, FlavorListener {
            override fun lostOwnership(a: Clipboard?, b: Transferable?) {
                Thread.sleep(250)
                val contents = sysClipboard.getContents(null)
                if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    val image = contents.getTransferData(DataFlavor.imageFlavor) as Image
                    handleResults(robot, options, reader(image), delay)
                }
                sysClipboard.setContents(sysClipboard.getContents(null), this)
            }

            override fun flavorsChanged(e: FlavorEvent?) {
                sysClipboard.removeFlavorListener(this)
                lostOwnership(sysClipboard, sysClipboard.getContents(null))
            }
        }

        sysClipboard.addFlavorListener(owner)
    }.start()

    halt()
}

fun halt() {
    Thread {
        // keep us alive.
        Thread.currentThread().join()
    }.start()
}

fun reader(): (Image) -> Array<Result> {
    val reader: MultipleBarcodeReader = GenericMultipleBarcodeReader(MultiFormatReader())
    val hints = mapOf(
            Pair(DecodeHintType.CHARACTER_SET, "UTF-8")
//            Pair(DecodeHintType.POSSIBLE_FORMATS, listOf(
//                    BarcodeFormat.QR_CODE,
//                    BarcodeFormat.CODE_39,
//                    BarcodeFormat.CODE_93,
//                    BarcodeFormat.CODE_128
//            ))
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

fun scanScreen(options: Options, robot: Robot, delay: Long) {
    val graphicsEnv = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val reader = reader()

    for (device in graphicsEnv.screenDevices) {
        val monitorScreen = robot.createScreenCapture(device.defaultConfiguration.bounds)

//        val window = JFrame("Debug")
//        window.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
//        window.layout = FlowLayout()
//        window.bounds = Rectangle(1200, 1200)
//
//        val image = JLabel(ImageIcon(monitorScreen.getScaledInstance(window.width, window.height * monitorScreen.height / monitorScreen.width, Image.SCALE_FAST)))
//        window.add(image)
//
//        window.isVisible = true
//        Thread.sleep(2000)
//        window.dispose()

        handleResults(robot, options, reader(monitorScreen), delay)

    }
}

fun handleResults(robot: Robot, options: Options, results: Array<Result>, delay: Long): Boolean {
    return if (results.isNotEmpty()) {
        for (result in results) {
            val code = result.text
            println("Found: >$code<")
            val actions = compile(code, options)
            if (actions == null) {
                println("Failed to parse code! Is the keyboard layout incomplete?")
            } else {
                println(actions)
                Thread.sleep(delay)
                act(robot, actions)
            }
        }
        true
    } else {
        System.err.println("Did not find any barcodes to scan!")
        false
    }
}

//fun loadImage(url: String): BufferedImage {
//    return ImageIO.read(URL(url))
//}

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

inline fun <reified T : Any> load(json: ObjectMapper, paths: List<String>, default: T): T {
    return paths.fold(default, { acc, path ->
        if (acc == default) load(json, path, default)
        else default
    })
}

inline fun <reified T : Any> load(json: ObjectMapper, path: String, default: T): T {
    val fromFile = read<T>(json, File(path))
    if (fromFile != null) {
        return fromFile
    }

    val fromJar = read<T>(json, Thread.currentThread().contextClassLoader.getResourceAsStream(path))
    if (fromJar != null) {
        return fromJar
    }

    return default
}

inline fun <reified T : Any> read(json: ObjectMapper, file: File): T? {
    return if (!file.canRead()) null
    else try {
        json.readValue<T>(file)
    } catch (ex: Exception) {
        ex.printStackTrace(System.err)
        null
    }
}

inline fun <reified T : Any> read(json: ObjectMapper, inStream: InputStream?): T? {
    return if (inStream == null) null
    else try {
        json.readValue<T>(inStream)
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
