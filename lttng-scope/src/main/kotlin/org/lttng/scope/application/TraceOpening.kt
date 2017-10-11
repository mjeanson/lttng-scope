/*
 * Copyright (C) 2017 EfficiOS Inc., Alexandre Montplaisir <alexmonthy@efficios.com>
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.lttng.scope.application

import com.efficios.jabberwocky.ctf.trace.generic.GenericCtfTrace
import com.efficios.jabberwocky.lttng.kernel.trace.LttngKernelTrace
import com.efficios.jabberwocky.lttng.ust.trace.LttngUstTrace
import com.efficios.jabberwocky.project.TraceProject
import com.efficios.jabberwocky.trace.Trace
import com.efficios.jabberwocky.trace.TraceInitializationException
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.layout.Region
import javafx.stage.DirectoryChooser
import org.lttng.scope.ScopePaths
import org.lttng.scope.views.context.ViewGroupContextManager
import org.lttng.scope.views.jfx.JfxUtils
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

private const val DIRECTORY_CHOOSER_TITLE = "Select CTF Trace Directory to Open"

private const val ERROR_OPENING_ALERT_TITLE = "Error opening trace"
private const val ERROR_OPENING_ALERT_TEXT = "The selected directory does not look like a CTF trace.\n" +
        "Make sure you select the directory with the file named 'metadata'."
private const val ERROR_OPENING_ALERT_WIDTH = 500.0

private var lastUsedDirectory: Path? = ScopePaths.homeDir

/**
 * Ask the user for a directory from which to open a trace.
 *
 * @param refNode Reference Node, user-facing dialogs should be placed close (at least on the same screen) as this node.
 */
fun openTraceAction(refNode: Node?) {
    val tracePath = showTraceSelectionDialog(lastUsedDirectory, refNode) ?: return
    lastUsedDirectory = tracePath

    // TODO Support the user passing the 'index' subdirectory
    val trace = try {
        // TODO We might be able to do away with the whole class hierarchy (GenericCtf, LttngUst, LttngKernel),
        // and have analyses only look into the metadata/event layouts.
        val genericTrace = GenericCtfTrace(tracePath)
        when (genericTrace.environment["domain"]) {
            "\"kernel\"" -> LttngKernelTrace(tracePath)
            "\"ust\"" -> LttngUstTrace(tracePath)
            else -> genericTrace
        }
    } catch (e: TraceInitializationException) {
        with(Alert(Alert.AlertType.ERROR)) {
            title = ERROR_OPENING_ALERT_TITLE
            contentText = ERROR_OPENING_ALERT_TEXT
            with(dialogPane) {
                minHeight = Region.USE_PREF_SIZE
                minWidth = ERROR_OPENING_ALERT_WIDTH
            }
            show()
            if (refNode != null) JfxUtils.centerDialogOnScreen(this, refNode)
        }
        return
    }

    val traceName = tracePath.last().toString()
    val projectName = traceName + trace.hash(traceName)
    val projectPath = ScopePaths.projectsDir.resolve(projectName)
    if (!Files.exists(projectPath)) Files.createDirectories(projectPath)

    val project = TraceProject.ofSingleTrace(projectName, projectPath, trace)

    // Only one view context for now
    val viewCtx = ViewGroupContextManager.getCurrent()
    viewCtx.currentTraceProject = project
}

private fun showTraceSelectionDialog(initialDir: Path?, refNode: Node?): Path? {
    return with(DirectoryChooser()) {
        title = DIRECTORY_CHOOSER_TITLE
        if (initialDir != null) initialDirectory = initialDir.toFile()
        showDialog(refNode?.scene?.window)
    }?.toPath()
}

/**
 * Get a unique project name (string) for a given trace.
 *
 * TODO Ideally we should add the number of events into this hash.
 */
private fun Trace<*>.hash(traceName: String): String = Objects.hash(traceName, startTime, endTime).toString()