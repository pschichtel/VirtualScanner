package tel.schich.virtualscanner

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.multi.MultipleBarcodeReader
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.*
import java.awt.im.InputContext
import java.awt.image.BufferedImage
import java.lang.IllegalArgumentException
import java.nio.file.Paths
import java.util.*

fun main(args: Array<String>) {

    InputContext.getInstance().selectInputMethod(Locale.ENGLISH)

    if (args.size >= 3) {
        val mode = args[0].toLowerCase()
        val layoutFile = args[1]
        val envelope = args[2]

        val options = loadLayout(Paths.get(layoutFile), Options(envelope = envelope))
        val robot = Robot()

        when(mode) {
            "screen" -> scanScreen(options, robot)
            "clipboard" -> monitorClipboard(options, robot)
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

fun monitorClipboard(options: Options, robot: Robot) {

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

fun scanScreen(options: Options, robot: Robot) {
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
            println(actions)
            act(robot, actions)
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

fun act(r: Robot, actions: List<Pair<Int, Action.Do>>) {
    for ((code, action) in actions) {
        act(r, code, action)
        Thread.sleep(10)
    }
}

fun act(r: Robot, code: Int, action: Action.Do) {
    try {
        when (action) {
            Action.Do.Release -> r.keyRelease(code)
            Action.Do.Press -> r.keyPress(code)
        }
    } catch (e: IllegalArgumentException) {
        throw RuntimeException("Unable to emit key stroke ($code, $action): ${e.message}", e)
    }
}
