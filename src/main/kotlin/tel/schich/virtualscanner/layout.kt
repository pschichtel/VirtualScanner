package tel.schich.virtualscanner

import com.fasterxml.jackson.databind.ObjectMapper
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.*
import javax.swing.*


fun createLayout(json: ObjectMapper, existingLayout: KeyLayout, charset: String, filePath: String) {
    System.setProperty("awt.useSystemAAFontSettings","on")
    System.setProperty("swing.aatext", "true")

    val frame = JFrame("Layout Creator")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.bounds = Rectangle(0, 0, 400, 400)
    frame.setLocationRelativeTo(null)

    val charKeyTable = mutableMapOf<Char, Actions>()
    val currentRecording = mutableListOf<Action>()
    val currentlyPressed = mutableSetOf<Int>()
    var charsetPosition = 0
    var currentChar: Char = charset[charsetPosition]
    currentRecording.addAll(existingLayout[currentChar] ?: listOf())

    fun store() {
        val completeLayout = (existingLayout + charKeyTable).filter { it.value.isNotEmpty() }
        if (existingLayout != completeLayout) {
            print("Writing changed layout...")
            generateLayoutFile(json, filePath, completeLayout)
            println("done!")
        } else {
            println("Nothing changed!")
        }
    }

    val layout = GridBagLayout()
    val panel = JPanel(layout)

    fun label(fontSize: Int): JLabel {
        val l = JLabel()
        l.font = Font("Verdana", Font.BOLD, fontSize)
        return l
    }

    fun constraint(x: Int, y: Int, width: Int = 1, height: Int = 1): GridBagConstraints {
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.CENTER
        c.weightx = 1.0
        c.weighty = 1.0
        c.gridx = x
        c.gridy = y
        c.gridwidth = width
        c.gridheight = height
        return c
    }

    val nameLabel = label(40)
    nameLabel.text = stringifyChar(currentChar)
    panel.add(nameLabel, constraint(0, 0, 3))

    val currentLabel = label(30)
    currentLabel.text = stringifyActions(currentRecording)
    panel.add(currentLabel, constraint(0, 1, 3))

    val nextButton = JButton()
    val charSelector = JComboBox<Char>(Vector(charset.toList()))
    val resetButton = JButton()

    resetButton.text = "Reset"
    resetButton.addMouseListener(clickHandler(MouseEvent.BUTTON1) {
        currentRecording.clear()
        currentLabel.text = ""
    })
    panel.add(resetButton, constraint(0, 2))

    charSelector.addActionListener {
        charsetPosition = charSelector.selectedIndex
        currentChar = charset[charsetPosition]
        currentRecording.clear()
        currentRecording.addAll(existingLayout[currentChar] ?: listOf())
        nameLabel.text = stringifyChar(currentChar)
        currentLabel.text = stringifyActions(currentRecording)
        if (charsetPosition + 1 >= charset.length) {
            nextButton.text = "Complete"
        }
    }
    panel.add(charSelector, constraint(1, 2))

    nextButton.text = "Accept"
    nextButton.addMouseListener(clickHandler(MouseEvent.BUTTON1) {
        charKeyTable[currentChar] = currentRecording.toList()
        store()

        if (charsetPosition + 1 >= charset.length) {
            frame.dispose()
        } else {
            // this will trigger an action on the combobox which in turn resets the state
            charSelector.selectedIndex++
        }
    })
    panel.add(nextButton, constraint(2, 2))

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
            currentLabel.text = stringifyActions(currentRecording)
        }
        true // the UI should not act on key events.
    }

    frame.add(panel)
    frame.isVisible = true
}

fun clickHandler(button: Int, f: (MouseEvent) -> Unit): MouseListener {
    return object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
            if (e != null && e.button == button) {
                f(e)
            }
        }
    }
}

fun stringifyChar(c: Char): String {
    return when (c) {
        '\n' -> "Linefeed"
        '\r' -> "Carriage Return"
        '\t' -> "Tab"
        ' ' -> "Space"
        else -> c.toString()
    }
}