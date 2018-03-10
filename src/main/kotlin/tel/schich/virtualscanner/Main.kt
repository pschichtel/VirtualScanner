package tel.schich.virtualscanner

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.multi.MultipleBarcodeReader
import java.awt.*
import java.awt.datatransfer.*
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities

fun main(args: Array<String>) {

//    println("abc:     ${parseSequence("abc")}")
//    println("a(b)c:   ${parseSequence("a(b)c")}")
//    println("a(bc):   ${parseSequence("a(bc)")}")
//    println("a(b(c)): ${parseSequence("a(b(c))")}")
//    println("{F4}a(b(c)): ${generateSequence(parseSequence("{F4}a(b(c))") ?: emptyList())}")
//    System.exit(1)

    if (args.isNotEmpty()) {
        when(args[0].toLowerCase()) {
            "screen" -> {
                if (args.size >= 3) {
                    val prefixString = args[1]
                    val suffixString = args[2]
                    scanScreen(typeOut(prefixString), typeOut(suffixString), Robot())
                } else {
                    System.err.println("usage: screen <prefix> <suffix>")
                    System.exit(1)
                }
            }
            "clipboard" -> {
                if (args.size >= 3) {
                    val prefixString = args[1]
                    val suffixString = args[2]
                    monitorClipboard(typeOut(prefixString), typeOut(suffixString), Robot())
                } else {
                    System.err.println("usage: clipboard <prefix> <suffix>")
                    System.exit(1)
                }
            }
            else -> {
                System.err.println("usage: screen|clipboard ....")
                System.exit(1)
            }
        }
    } else {
        System.err.println("usage: screen|clipboard ....")
        System.exit(1)
    }
}

fun monitorClipboard(prefix: List<Pair<Int, KeyAction>>, suffix: List<Pair<Int, KeyAction>>, robot: Robot) {

    Thread {
        val reader = reader()
        val sysClipboard = Toolkit.getDefaultToolkit().systemClipboard

        val owner = object : ClipboardOwner, FlavorListener {
            override fun lostOwnership(a: Clipboard?, b: Transferable?) {
                Thread.sleep(250)
                val contents = sysClipboard.getContents(null)
                if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    val image = contents.getTransferData(DataFlavor.imageFlavor) as Image
                    handleResults(robot, prefix, suffix, reader(image))
                }
                SwingUtilities.invokeLater {
                    sysClipboard.setContents(sysClipboard.getContents(null), this)
                }
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

fun scanScreen(prefix: List<Pair<Int, KeyAction>>, suffix: List<Pair<Int, KeyAction>>, robot: Robot) {
    val toolkit = Toolkit.getDefaultToolkit()

    //val screen = loadImage("https://api.qrserver.com/v1/create-qr-code/?data=test&size=220x220&margin=0")
    val screen = robot.createScreenCapture(Rectangle(toolkit.screenSize))
    val graphicsEnv = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val reader = reader()


    for (device in graphicsEnv.screenDevices) {
        val bounds = device.defaultConfiguration.bounds
        val monitorScreen = screen.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height)


        val window = JFrame("Debug")
        window.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        window.layout = FlowLayout()
        window.bounds = Rectangle(1200, 1200)

        val image = JLabel(ImageIcon(monitorScreen.getScaledInstance(window.width, window.height * monitorScreen.height / monitorScreen.width, Image.SCALE_FAST)))
        window.add(image)

        window.isVisible = true
        Thread.sleep(2000)
        window.dispose()

        handleResults(robot, prefix, suffix, reader(monitorScreen))

    }
}

fun handleResults(robot: Robot, prefix: List<Pair<Int, KeyAction>>, suffix: List<Pair<Int, KeyAction>>, results: Array<Result>): Boolean {
    return if (results.isNotEmpty()) {
        for (result in results) {
            val code = result.text
            println("Found: $code")
            act(robot, prefix + typeOut(code) + suffix)
        }
        true
    } else {
        System.err.println("Did not find any barcodes to scan!")
        false
    }
}

fun loadImage(url: String): BufferedImage {
    return ImageIO.read(URL(url))
}

fun stroke(code: Int): List<Pair<Int, KeyAction>> {
    return if (code == KeyEvent.BUTTON1_DOWN_MASK || code == KeyEvent.BUTTON2_DOWN_MASK || code == KeyEvent.BUTTON3_DOWN_MASK) {
        listOf(Pair(code, KeyAction.MouseDown), Pair(code, KeyAction.MouseUp))
    } else {
        listOf(Pair(code, KeyAction.Press), Pair(code, KeyAction.Release))
    }
}

fun typeOut(s: String): List<Pair<Int, KeyAction>> {
    return s.flatMap { c ->
        when (c) {
            '\n' -> stroke(KeyEvent.VK_ENTER)
            ' ' -> stroke(KeyEvent.VK_SPACE)
            else -> charToAction(c)
        }
    }
}

fun act(r: Robot, actions: List<Pair<Int, KeyAction>>) {
    for ((code, action) in actions) {
        act(r, code, action)
        Thread.sleep(20)
    }
}

fun act(r: Robot, code: Int, action: KeyAction) {
    when (action) {
        KeyAction.Release -> r.keyRelease(code)
        KeyAction.Press -> r.keyPress(code)
        KeyAction.MouseUp -> r.mouseRelease(code)
        KeyAction.MouseDown -> r.mousePress(code)
    }
}

enum class KeyAction {
    Release, Press, MouseUp, MouseDown
}

fun charToAction(c: Char): List<Pair<Int, KeyAction>> {
    return when (c) {
        in 'a'..'z' -> {
            val code = KeyEvent.VK_A + (c - 'a')
            listOf(Pair(code, KeyAction.Press), Pair(code, KeyAction.Release))
        }
        in 'A'..'Z' -> {
            val code = KeyEvent.VK_A + (c - 'A')
            listOf(
                    Pair(KeyEvent.VK_SHIFT, KeyAction.Press),
                    Pair(code, KeyAction.Press),
                    Pair(code, KeyAction.Release),
                    Pair(KeyEvent.VK_SHIFT, KeyAction.Release)
            )
        }
        in '0'..'9' -> {
            val code = KeyEvent.VK_0 + (c - '0')
            listOf(Pair(code, KeyAction.Press), Pair(code, KeyAction.Release))
        }
        else -> emptyList()
    }
}
