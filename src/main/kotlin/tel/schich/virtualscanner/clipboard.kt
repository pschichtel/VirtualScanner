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

import dorkbox.systemTray.SystemTray
import java.awt.Image
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.*
import kotlin.system.exitProcess

fun monitorClipboard(options: Options, robot: Robot, delay: Long) {
    val sysTray: SystemTray? = SystemTray.get()
    if (sysTray != null) {
        sysTray.setImage(ClassLoader.getSystemResource("logo.png"))
        sysTray.setTooltip(ApplicationName)
        sysTray.menu.add(dorkbox.systemTray.MenuItem("Exit") {
            try {
                sysTray.shutdown()
            } finally {
                exitProcess(0)
            }
        })
    }

    val reader = reader(options.encodingHint)
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
    notify("Running in background!")
}