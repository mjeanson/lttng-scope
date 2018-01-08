/*
 * Copyright (C) 2017 EfficiOS Inc., Alexandre Montplaisir <alexmonthy@efficios.com>
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.lttng.scope.views.timeline.widgets.xychart

import com.efficios.jabberwocky.common.TimeRange
import com.efficios.jabberwocky.views.xychart.control.XYChartControl
import com.efficios.jabberwocky.views.xychart.view.XYChartView
import javafx.scene.chart.XYChart

abstract class XYChartWidget(override val control: XYChartControl) : XYChartView {

    abstract val chart: XYChart<Number, Number>

    abstract val selectionLayer: XYChartSelectionLayer

    override fun drawSelection(selectionRange: TimeRange) {
        selectionLayer.drawSelection(selectionRange)
    }
}