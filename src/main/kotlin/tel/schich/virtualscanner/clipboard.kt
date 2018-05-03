package tel.schich.virtualscanner

import dorkbox.systemTray.SystemTray
import notify.Notify
import java.awt.Image
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.*


fun monitorClipboard(options: Options, robot: Robot, delay: Long) {
    val sysTray: SystemTray? = SystemTray.get()
    if (sysTray != null) {
        sysTray.setImage(ClassLoader.getSystemResource("logo.png"))
        sysTray.setTooltip(ApplicationName)
        sysTray.menu.add(dorkbox.systemTray.MenuItem("Exit", {
            sysTray.shutdown()
        }))
    }

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
    Notify.info(ApplicationName, "Running in background!")
}