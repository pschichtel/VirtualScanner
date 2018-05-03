package tel.schich.virtualscanner

import java.awt.*
import java.awt.datatransfer.*
import javax.imageio.ImageIO


fun monitorClipboard(options: Options, robot: Robot, delay: Long) {
    val trayIcon = if (SystemTray.isSupported()) {
        val tray = SystemTray.getSystemTray()
        val menu = PopupMenu()
        val item = MenuItem("Close")
        item.addActionListener {
            System.exit(0)
        }
        menu.add(item)
        val icon = TrayIcon(ImageIO.read(ClassLoader.getSystemResource("logo.png")), "VirtualScanner")
        icon.isImageAutoSize = true
        icon.popupMenu = menu;
        tray.add(icon)
        icon
    } else null

    Thread {
        val reader = reader()
        val sysClipboard = Toolkit.getDefaultToolkit().systemClipboard

        val owner = object : ClipboardOwner, FlavorListener {
            override fun lostOwnership(a: Clipboard?, b: Transferable?) {
                Thread.sleep(250)
                val contents = sysClipboard.getContents(null)
                if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    val image = contents.getTransferData(DataFlavor.imageFlavor) as Image
                    trayIcon?.displayMessage(ApplicationName, "Detected barcode!", TrayIcon.MessageType.INFO)
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

    trayIcon?.displayMessage(ApplicationName, "Running in background!", TrayIcon.MessageType.INFO)
}