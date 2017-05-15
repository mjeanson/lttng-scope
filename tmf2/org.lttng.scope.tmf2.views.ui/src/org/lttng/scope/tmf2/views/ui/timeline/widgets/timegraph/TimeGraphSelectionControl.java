/*
 * Copyright (C) 2017 EfficiOS Inc., Alexandre Montplaisir <alexmonthy@efficios.com>
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.lttng.scope.tmf2.views.ui.timeline.widgets.timegraph;

import static java.util.Objects.requireNonNull;

import java.util.function.Predicate;
import java.util.stream.Stream;

import org.lttng.scope.tmf2.views.core.TimeRange;
import org.lttng.scope.tmf2.views.ui.jfx.JfxUtils;

import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;

/**
 * Sub-control of the time graph widget to handle the selection layer, which
 * displays the current and ongoing selections.
 *
 * It also sends the corresponding selection update whenever a new selection is
 * made.
 *
 * @author Alexandre Montplaisir
 */
public class TimeGraphSelectionControl {

    /* Style settings. TODO Move to debug options? */
    private static final double SELECTION_STROKE_WIDTH = 1;
    private static final Color SELECTION_STROKE_COLOR = requireNonNull(Color.BLUE);
    private static final Color SELECTION_FILL_COLOR = requireNonNull(Color.LIGHTBLUE.deriveColor(0, 1.2, 1, 0.4));

    /**
     * These events are to be ignored by the time graph pane, they should
     * "bubble up" to the scrollpane to be used for panning.
     */
    private static final Predicate<MouseEvent> MOUSE_EVENT_IGNORED = e -> {
        return (e.getButton() == MouseButton.SECONDARY
                || e.getButton() == MouseButton.MIDDLE
                || e.isControlDown());
    };

    private final TimeGraphWidget fWidget;

    private final Rectangle fSelectionRect = new Rectangle();
    private final Rectangle fOngoingSelectionRect = new Rectangle();

    private final SelectionContext fSelectionCtx = new SelectionContext();

    /**
     * Constructor
     *
     * @param widget
     *            The corresponding time graph widget
     * @param paintTarget
     *            The Group to which the selection rectangles should be added
     */
    public TimeGraphSelectionControl(TimeGraphWidget widget, Group paintTarget) {
        fWidget = widget;

        final Pane timeGraphPane = fWidget.getTimeGraphPane();

        Stream.of(fSelectionRect, fOngoingSelectionRect).forEach(rect -> {
            rect.setMouseTransparent(true);

            rect.setStroke(SELECTION_STROKE_COLOR);
            rect.setStrokeWidth(SELECTION_STROKE_WIDTH);
            rect.setStrokeLineCap(StrokeLineCap.ROUND);
            rect.setFill(SELECTION_FILL_COLOR);

            rect.yProperty().bind(JfxUtils.ZERO_PROPERTY);
            rect.heightProperty().bind(timeGraphPane.heightProperty());
        });

        /*
         * Note, unlike most other controls, we will not add/remove children to
         * the target group, we will add them once then toggle their 'visible'
         * property.
         */
        fSelectionRect.setVisible(true);
        fOngoingSelectionRect.setVisible(false);
        paintTarget.getChildren().addAll(fSelectionRect, fOngoingSelectionRect);

        timeGraphPane.addEventHandler(MouseEvent.MOUSE_PRESSED, fSelectionCtx.fMousePressedEventHandler);
        timeGraphPane.addEventHandler(MouseEvent.MOUSE_DRAGGED, fSelectionCtx.fMouseDraggedEventHandler);
        timeGraphPane.addEventHandler(MouseEvent.MOUSE_RELEASED, fSelectionCtx.fMouseReleasedEventHandler);
    }

    /**
     * Get the rectangle object representing the current selection range.
     *
     * @return The current selection rectangle
     */
    public Rectangle getSelectionRectangle() {
        return fSelectionRect;
    }

    /**
     * Get the rectangle object representing the ongoing selection. It is
     * displayed while the user holds the mouse down and drags to make a
     * selection, but before the mouse is released. The "real" selection is only
     * applied on mouse release.
     *
     * @return The ongoing selection rectangle
     */
    public Rectangle getOngoingSelectionRectangle() {
        return fOngoingSelectionRect;
    }

    /**
     * Draw a new "current" selection. For times where the selection is updated
     * elsewhere in the framework.
     *
     * @param selectionRange
     *            The time range of the new selection
     */
    public void drawSelection(TimeRange selectionRange) {
        double xStart = fWidget.timestampToPaneXPos(selectionRange.getStart());
        double xEnd = fWidget.timestampToPaneXPos(selectionRange.getEnd());
        double xWidth = xEnd - xStart;

        fSelectionRect.setX(xStart);
        fSelectionRect.setWidth(xWidth);

        fSelectionRect.setVisible(true);
    }

    /**
     * Class encapsulating the time range selection, related drawing and
     * listeners.
     */
    private class SelectionContext {

        private boolean fOngoingSelection;
        private double fMouseOriginX;

        public final EventHandler<MouseEvent> fMousePressedEventHandler = e -> {
            if (MOUSE_EVENT_IGNORED.test(e)) {
                return;
            }

            if (fOngoingSelection) {
                return;
            }

            /* Remove the current selection, if there is one */
            fSelectionRect.setVisible(false);

            fMouseOriginX = e.getX();

            fOngoingSelectionRect.setX(fMouseOriginX);
            fOngoingSelectionRect.setWidth(0);

            fOngoingSelectionRect.setVisible(true);

            e.consume();

            fOngoingSelection = true;
        };

        public final EventHandler<MouseEvent> fMouseDraggedEventHandler = e -> {
            if (MOUSE_EVENT_IGNORED.test(e)) {
                return;
            }

            double newX = e.getX();
            double offsetX = newX - fMouseOriginX;

            if (offsetX > 0) {
                fOngoingSelectionRect.setX(fMouseOriginX);
                fOngoingSelectionRect.setWidth(offsetX);
            } else {
                fOngoingSelectionRect.setX(newX);
                fOngoingSelectionRect.setWidth(-offsetX);
            }

            e.consume();
        };

        public final EventHandler<MouseEvent> fMouseReleasedEventHandler = e -> {
            if (MOUSE_EVENT_IGNORED.test(e)) {
                return;
            }

            fOngoingSelectionRect.setVisible(false);

            e.consume();

            /* Send a time range selection signal for the currently selected time range */
            double startX = Math.max(0, fOngoingSelectionRect.getX());
            // FIXME Possible glitch when selecting backwards outside of the window
            double endX = Math.min(fWidget.getTimeGraphPane().getWidth(), startX + fOngoingSelectionRect.getWidth());
            long tsStart = fWidget.paneXPosToTimestamp(startX);
            long tsEnd = fWidget.paneXPosToTimestamp(endX);

            fWidget.getControl().updateTimeRangeSelection(TimeRange.of(tsStart, tsEnd));

            fOngoingSelection = false;
        };
    }

}