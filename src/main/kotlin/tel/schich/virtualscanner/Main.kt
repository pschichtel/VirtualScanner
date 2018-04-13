package tel.schich.virtualscanner

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.multi.MultipleBarcodeReader
import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.fromFile
import com.natpryce.konfig.ConfigurationProperties.Companion.fromResource
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import java.awt.*
import java.awt.datatransfer.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.im.InputContext
import java.awt.image.BufferedImage
import java.io.File
import java.lang.IllegalArgumentException
import java.util.*
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel

object options : PropertyGroup() {
    val layout by stringType
    val prefix by stringType
    val suffix by stringType
    val delay by intType
    val charset by stringType
}

fun main(args: Array<String>) {

    InputContext.getInstance().selectInputMethod(Locale.ENGLISH)

    if (args.isNotEmpty()) {
        val mode = args[0].toLowerCase()


        val base = systemProperties() overriding
                EnvironmentVariables()

        val conf = (if (args.size > 1) {
            base overriding fromFile(File(args[1]))
        } else base) overriding fromResource("defaults.properties")

        val envelope = Pair(parseActionSpec(conf[options.prefix]), parseActionSpec(conf[options.suffix]))

        val layout = loadLayout(conf[options.layout]) ?: mapOf()
        val charset = conf[options.charset]
        val delay = conf[options.delay]
        val options = Options(envelope = envelope, keyboardLayout = layout)
        val robot = Robot()

        when(mode) {
            "screen" -> scanScreen(options, robot, delay)
            "clipboard" -> monitorClipboard(options, robot, delay)
            "layout" -> createLayout(charset)
            else -> {
                System.err.println("available modes: screen, clipboard")
                System.exit(1)
            }
        }
    } else {
        System.err.println("usage: screen|clipboard <envelope> ....")
        System.exit(1)
    }
}

fun createLayout(charset: String) {
    val frame = JFrame("Layout Creator")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.bounds = Rectangle(0, 0, 400, 400)
    frame.setLocationRelativeTo(null)

    frame.addKeyListener(object : KeyListener {
        override fun keyTyped(e: KeyEvent?) {}

        override fun keyPressed(e: KeyEvent?) {
            println("+" + e?.keyCode)
        }

        override fun keyReleased(e: KeyEvent?) {
            println("-" + e?.keyCode)
        }
    })

    val layout = GridLayout(3, 3)
    val panel = JPanel(layout)
    val children = Array(layout.columns * layout.rows, { Panel() })
    children.forEach { c -> panel.add(c) }

    val label = JLabel()
    label.font = Font.getFont("Verdana")
    label.text = "A"
    children[4].add(label)
    frame.add(panel)

    frame.isVisible = true
}

fun monitorClipboard(options: Options, robot: Robot, delay: Int) {

    Thread {
        val reader = reader()
        val sysClipboard = Toolkit.getDefaultToolkit().systemClipboard

        val owner = object : ClipboardOwner, FlavorListener {
            override fun lostOwnership(a: Clipboard?, b: Transferable?) {
                Thread.sleep(250)
                val contents = sysClipboard.getContents(null)
                if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    val image = contents.getTransferData(DataFlavor.imageFlavor) as Image
                    handleResults(robot, options, reader(image))
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

fun scanScreen(options: Options, robot: Robot, delay: Int) {
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

        handleResults(robot, options, reader(monitorScreen))

    }
}

fun handleResults(robot: Robot, options: Options, results: Array<Result>): Boolean {
    return if (results.isNotEmpty()) {
        for (result in results) {
            val code = result.text
            println("Found: >$code<")
            val actions = compile(code, options)
            if (actions == null) {
                println("Failed to parse code! Is the keyboard layout incomplete?")
            } else {
                println(actions)
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
            State.Release -> r.keyRelease(action.key)
            State.Press -> r.keyPress(action.key)
        }
    } catch (e: IllegalArgumentException) {
        throw RuntimeException("Unable to emit key stroke (${action.key}, ${action.state}): ${e.message}", e)
    }
}
