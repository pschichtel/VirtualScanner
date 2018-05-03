package tel.schich.virtualscanner

import java.awt.GraphicsEnvironment
import java.awt.Robot

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