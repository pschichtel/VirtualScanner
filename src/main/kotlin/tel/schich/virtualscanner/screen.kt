/**
 * virtual-scanner - Scan barcodes from your screen and emit the content as key strokes.
 * Copyright © 2018 Phillip Schichtel (${email})
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

import java.awt.GraphicsEnvironment
import java.awt.Robot

fun scanScreen(options: Options, robot: Robot, delay: Long) {
    val graphicsEnv = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val reader = reader(options.encodingHint)

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