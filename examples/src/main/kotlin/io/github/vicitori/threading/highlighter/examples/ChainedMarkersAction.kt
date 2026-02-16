package io.github.vicitori.threading.highlighter.examples

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages

class ChainedMarkersAction : AnAction("Chained Markers Example") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        processDataFromEdt()

        ApplicationManager.getApplication().executeOnPooledThread {
            processDataFromBackground()
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            complexOperation()
        }

        Messages.showInfoMessage(
            project,
            "Started chained operations. Check gutter icons for markers!",
            "Chained Markers"
        )
    }

    private fun processDataFromEdt() {
        println("[EDT] Processing data from EDT thread")
        val result = performCalculation(42)
        println("[EDT] Result: $result")
    }

    /**
     * Вызывается из фонового потока.
     * Вызывает функцию с NON-EDT маркером.
     */
    private fun processDataFromBackground() {
        println("[Background] Processing data from background thread")
        val result = performCalculation(100)
        println("[Background] Result: $result")
    }

    private fun complexOperation() {
        println("[Complex] Starting complex operation")

        val calculationResult = performCalculation(256)
        println("[Complex] Calculation result: $calculationResult")

        slowEdtOperation()
        
        println("[Complex] Complex operation completed")
    }

    private fun performCalculation(value: Int): Int {
        ApplicationManager.getApplication().assertIsNonDispatchThread()
        Thread.sleep(50)
        return value * 2
    }

    private fun slowEdtOperation() {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().assertIsDispatchThread()
            Thread.sleep(50)
            println("[EDT] Slow EDT operation completed")
        }
    }
}
