/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.lttng.scope.tmf2.views.ui.timegraph.swtjfx;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.lttng.scope.tmf2.views.core.timegraph.control.TimeGraphModelControl;
import org.lttng.scope.tmf2.views.core.timegraph.model.provider.ITimeGraphModelRenderProvider;
import org.lttng.scope.tmf2.views.core.timegraph.model.render.states.TimeGraphStateRender;
import org.lttng.scope.tmf2.views.core.timegraph.model.render.tree.TimeGraphTreeElement;
import org.lttng.scope.tmf2.views.core.timegraph.model.render.tree.TimeGraphTreeRender;
import org.lttng.scope.tmf2.views.core.timegraph.view.TimeGraphModelView;
import org.lttng.scope.tmf2.views.ui.timegraph.swtjfx.Position.HorizontalPosition;
import org.lttng.scope.tmf2.views.ui.timegraph.swtjfx.Position.VerticalPosition;

import com.google.common.annotations.VisibleForTesting;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.embed.swt.FXCanvas;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

/**
 * Viewer for the {@link SwtJfxTimeGraphView}, encapsulating all the view's
 * controls.
 *
 * Its contents consist of:
 *
 * TODO update this to its final form
 * <pre>
 * SashForm fBaseControl (parent is passed from the view)
 *  + FXCanvas
 *  |   + ScrollPane
 *  |       + TreeView (?), contains the list of threads
 *  + FXCanvas
 *      + ScrollPane, will contain the time graph area
 *          + Pane, gets resized to very large horizontal size to represent the whole trace range
 *             + Canvas, canvas children are tiled on the Pane to show the content of one Render each
 *             + Canvas
 *             +  ...
 * </pre>
 *
 * Both ScrolledPanes's vertical scrollbars are bound together, so that they
 * scroll together.
 *
 * @author Alexandre Montplaisir
 */
public class SwtJfxTimeGraphViewer extends TimeGraphModelView {

    // ------------------------------------------------------------------------
    // Style definitions
    // (Could eventually be moved to separate .css file?)
    // ------------------------------------------------------------------------

    private static final Color BACKGROUD_LINES_COLOR = requireNonNull(Color.LIGHTBLUE);
    private static final String BACKGROUND_STYLE = "-fx-background-color: rgba(255, 255, 255, 255);"; //$NON-NLS-1$

    private static final double SELECTION_STROKE_WIDTH = 1;
    private static final Color SELECTION_STROKE_COLOR = requireNonNull(Color.BLUE);
    private static final Color SELECTION_FILL_COLOR = requireNonNull(Color.LIGHTBLUE.deriveColor(0, 1.2, 1, 0.4));

    private static final int LABEL_SIDE_MARGIN = 10;

    /**
     * Height of individual entries (text + states), including padding.
     *
     * TODO Make this configurable (vertical zoom feature)
     */
    public static final double ENTRY_HEIGHT = 20;

    // ------------------------------------------------------------------------
    // Instance fields
    // ------------------------------------------------------------------------

    private final DebugOptions fDebugOptions = new DebugOptions();

    private final SelectionContext fSelectionCtx = new SelectionContext();
    private final ScrollingContext fScrollingCtx = new ScrollingContext();
    private final ZoomActions fZoomActions = new ZoomActions();

    private final LatestTaskExecutor fTaskExecutor = new LatestTaskExecutor();

    private final FXCanvas fBaseCanvas;
    private final SplitPane fBaseSplitPane;

    private final Pane fTreePane;
    private final ScrollPane fTreeScrollPane;
    private final Pane fTimeGraphPane;
    private final ScrollPane fTimeGraphScrollPane;

    /*
     * Children of the time graph pane are split into groups, so we can easily
     * redraw or add only some of them.
     */
    private final Group fTimeGraphBackgroundLayer;
    private final Group fTimeGraphStatesLayer;
    private final Group fTimeGraphTextLabelsLayer;
    // TODO Layers for markers, arrows
    private final Group fTimeGraphSelectionLayer;
    private final Group fTimeGraphLoadingOverlayLayer;

    private final Rectangle fSelectionRect;
    private final Rectangle fOngoingSelectionRect;
    private final LoadingOverlay fTimeGraphLoadingOverlay;


    private final Timer fUiUpdateTimer = new Timer();
    private final TimerTask fUiUpdateTimerTask = new PeriodicRedrawTask(this);

    private volatile TimeGraphTreeRender fLatestTreeRender = TimeGraphTreeRender.EMPTY_RENDER;

    /** Current zoom level */
    private double fNanosPerPixel = 1.0;

    /**
     * Constructor
     *
     * @param parent
     *            Parent SWT composite
     */
    public SwtJfxTimeGraphViewer(Composite parent, TimeGraphModelControl control) {
        super(control);
        Platform.setImplicitExit(false);

        fBaseCanvas = new FXCanvas(parent, SWT.NONE);

        // --------------------------------------------------------------------
        // Prepare the tree part's scene graph
        // --------------------------------------------------------------------

        fTreePane = new Pane();

        fTreeScrollPane = new ScrollPane(fTreePane);
        /* We only show the time graph's vertical scrollbar */
        fTreeScrollPane.setVbarPolicy(ScrollBarPolicy.NEVER);
        fTreeScrollPane.setHbarPolicy(ScrollBarPolicy.ALWAYS);

        // --------------------------------------------------------------------
        // Prepare the time graph's part scene graph
        // --------------------------------------------------------------------

        fTimeGraphLoadingOverlay = new LoadingOverlay();

        fSelectionRect = new Rectangle();
        fOngoingSelectionRect = new Rectangle();

        Stream.of(fSelectionRect, fOngoingSelectionRect).forEach(rect -> {
            rect.setStroke(SELECTION_STROKE_COLOR);
            rect.setStrokeWidth(SELECTION_STROKE_WIDTH);
            rect.setStrokeLineCap(StrokeLineCap.ROUND);
            rect.setFill(SELECTION_FILL_COLOR);
        });

        fTimeGraphBackgroundLayer = new Group();
        fTimeGraphStatesLayer = new Group();
        fTimeGraphTextLabelsLayer = new Group();
        fTimeGraphSelectionLayer = new Group(fSelectionRect, fOngoingSelectionRect);
        fTimeGraphLoadingOverlayLayer = new Group(fTimeGraphLoadingOverlay);

        /*
         * The order of the layers is important here, it will go from back to
         * front.
         */
        fTimeGraphPane = new Pane(fTimeGraphBackgroundLayer,
                fTimeGraphStatesLayer,
                fTimeGraphTextLabelsLayer,
                fTimeGraphSelectionLayer,
                fTimeGraphLoadingOverlayLayer);
        fTimeGraphPane.setStyle(BACKGROUND_STYLE);
        fTimeGraphPane.addEventHandler(MouseEvent.MOUSE_PRESSED, fSelectionCtx.fMousePressedEventHandler);
        fTimeGraphPane.addEventHandler(MouseEvent.MOUSE_DRAGGED, fSelectionCtx.fMouseDraggedEventHandler);
        fTimeGraphPane.addEventHandler(MouseEvent.MOUSE_RELEASED, fSelectionCtx.fMouseReleasedEventHandler);

        /*
         * We control the width of the time graph pane programmatically, so
         * ensure that calls to setPrefWidth set the actual width right away.
         */
        fTimeGraphPane.minWidthProperty().bind(fTimeGraphPane.prefWidthProperty());
        fTimeGraphPane.maxWidthProperty().bind(fTimeGraphPane.prefWidthProperty());

        /*
         * Ensure the time graph pane is always exactly the same vertical size
         * as the tree pane, so they remain aligned.
         */
        fTimeGraphPane.minHeightProperty().bind(fTreePane.heightProperty());
        fTimeGraphPane.prefHeightProperty().bind(fTreePane.heightProperty());
        fTimeGraphPane.maxHeightProperty().bind(fTreePane.heightProperty());

        /*
         * Set the loading overlay's size to always follow the size of the pane.
         */
        fTimeGraphLoadingOverlay.widthProperty().bind(fTimeGraphPane.widthProperty());
        fTimeGraphLoadingOverlay.heightProperty().bind(fTimeGraphPane.heightProperty());

        fTimeGraphScrollPane = new ScrollPane(fTimeGraphPane);
        fTimeGraphScrollPane.setVbarPolicy(ScrollBarPolicy.ALWAYS);
        fTimeGraphScrollPane.setHbarPolicy(ScrollBarPolicy.ALWAYS);
        fTimeGraphScrollPane.setFitToHeight(true);
        fTimeGraphScrollPane.setFitToWidth(true);
        fTimeGraphScrollPane.setPannable(true);

        /* Attach the mouse/scrollbar listeners */
        fTimeGraphScrollPane.setOnMouseEntered(fScrollingCtx.fMouseEnteredEventHandler);
        fTimeGraphScrollPane.setOnMouseExited(fScrollingCtx.fMouseExitedEventHandler);
        fTimeGraphScrollPane.hvalueProperty().addListener(fScrollingCtx.fHScrollChangeListener);

        /*
         * Mouse scroll handlers (for zooming) are attached to the time graph
         * itself: events let through will be used by the scrollpane as normal
         * scroll actions.
         */
        fTimeGraphPane.setOnScroll(fMouseScrollListener);

        /* Synchronize the two scrollpanes' vertical scroll bars together */
        fTreeScrollPane.vvalueProperty().bindBidirectional(fTimeGraphScrollPane.vvalueProperty());

        // --------------------------------------------------------------------
        // Prepare the top-level area
        // --------------------------------------------------------------------

        fBaseSplitPane = new SplitPane(fTreeScrollPane, fTimeGraphScrollPane);
        fBaseSplitPane.setOrientation(Orientation.HORIZONTAL);

        fBaseCanvas.setScene(new Scene(fBaseSplitPane));

        /*
         * setDividerPositions() needs to be called *after* the Stage/Scene is
         * initialized.
         */
        fBaseSplitPane.setDividerPositions(0.2);

        /*
         * Initially populate the viewer with the context of the current trace.
         */
        ITmfTrace trace = TmfTraceManager.getInstance().getActiveTrace();
        getControl().initializeForTrace(trace);

        /* Start the periodic redraw thread */
        long delay = fDebugOptions.getUIUpdateDelay();
        fUiUpdateTimer.schedule(fUiUpdateTimerTask, delay, delay);
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    @Override
    public void disposeImpl() {
        /* Stop/cleanup the redraw thread */
        fUiUpdateTimer.cancel();
        fUiUpdateTimer.purge();
    }

    @Override
    public void clear() {
        // TODO
    }

    @Override
    public void seekVisibleRange(long visibleWindowStartTime, long visibleWindowEndTime) {
        final long fullTimeGraphStart = getControl().getFullTimeGraphStartTime();
        final long fullTimeGraphEnd = getControl().getFullTimeGraphEndTime();

        /* Update the zoom level */
        long windowTimeRange = visibleWindowEndTime - visibleWindowStartTime;
        double timeGraphVisibleWidth = fTimeGraphScrollPane.getViewportBounds().getWidth();
        fNanosPerPixel = windowTimeRange / timeGraphVisibleWidth;

        double timeGraphTotalWidth = timestampToPaneXPos(fullTimeGraphEnd) - timestampToPaneXPos(fullTimeGraphStart);
        if (timeGraphTotalWidth < 1.0) {
            // FIXME
            return;
        }

        double newValue;
        if (visibleWindowStartTime == fullTimeGraphStart) {
            newValue = fTimeGraphScrollPane.getHmin();
        } else if (visibleWindowEndTime == fullTimeGraphEnd) {
            newValue = fTimeGraphScrollPane.getHmax();
        } else {
            /*
             * The "hvalue" is in reference to the beginning of the pane, not
             * the middle point as one could think.
             *
             * Also note that the "scrollable distance" is not simply
             * "timeGraphTotalWidth", it's
             * "timeGraphTotalWidth - timeGraphVisibleWidth". The view does not
             * allow scrolling the start and end edges up to the middle point
             * for example.
             *
             * See http://stackoverflow.com/a/23518314/4227853 for a great
             * explanation.
             */
            double startPos = timestampToPaneXPos(visibleWindowStartTime);
            newValue = startPos / (timeGraphTotalWidth - timeGraphVisibleWidth);
        }

        fScrollingCtx.fHListenerStatus.disable();
        try {

            fZoomActions.resetZoomFactor();
            fTimeGraphPane.getTransforms().clear();

            /*
             * Remember min/max are bound to the "pref" width, so this will
             * change the actual size right away.
             */
            fTimeGraphPane.setPrefWidth(timeGraphTotalWidth);
            /*
             * Since we potentially changed the size of a child of the
             * scrollpane, it's important to call layout() on it before
             * setHvalue(). If we don't, the setHvalue() will apply to the old
             * layout, and the upcoming pulse will simply revert our changes.
             */
            fTimeGraphScrollPane.layout();
            fTimeGraphScrollPane.setHvalue(newValue);

        } finally {
            fScrollingCtx.fHListenerStatus.enable();
        }
    }

    /**
     * The current horizontal position is tracked by the control. This method
     * just wraps it into a {@link HorizontalPosition}.
     */
    HorizontalPosition getCurrentHorizontalPosition() {
        long start = getControl().getVisibleTimeRangeStart();
        long end = getControl().getVisibleTimeRangeEnd();
        return new HorizontalPosition(start, end);
    }

    void paintArea(HorizontalPosition horizontalPos, VerticalPosition verticalPos, long taskSeqNb) {
        final long fullTimeGraphStart = getControl().getFullTimeGraphStartTime();
        final long fullTimeGraphEnd = getControl().getFullTimeGraphEndTime();
        final long windowStartTime = horizontalPos.fStartTime;
        final long windowEndTime = horizontalPos.fEndTime;
        final long windowTimeRange = windowEndTime - windowStartTime;

        final long treePaneWidth = Math.round(fTreeScrollPane.getWidth());

        /*
         * Request the needed renders and prepare the corresponding UI objects.
         * We may ask for some padding on each side, clamped by the trace's
         * start and end.
         */
        final long timeRangePadding = Math.round(windowTimeRange * fDebugOptions.getRenderRangePadding());
        final long renderingStartTime = Math.max(fullTimeGraphStart, windowStartTime - timeRangePadding);
        final long renderingEndTime = Math.min(fullTimeGraphEnd, windowEndTime + timeRangePadding);
        final long resolution = Math.max(1, Math.round(fNanosPerPixel));

        /*
         * Start a new repaint, display the "loading" overlay. The next
         * paint task to finish will put it back to non-visible.
         */
        if (getDebugOptions().isLoadingOverlayEnabled()) {
            fTimeGraphLoadingOverlay.fadeIn();
        }

        Task<@Nullable Void> task = new Task<@Nullable Void>() {
            @Override
            protected @Nullable Void call() {
                long start = System.nanoTime();
                System.err.println("Starting paint task #" + taskSeqNb);

                ITimeGraphModelRenderProvider renderProvider = getControl().getModelRenderProvider();
                TimeGraphTreeRender treeRender = renderProvider.getTreeRender();
                final List<TimeGraphTreeElement> allTreeElements = treeRender.getAllTreeElements();

                if (isCancelled()) {
                    System.err.println("task #" + taskSeqNb + " was cancelled before generating the states");
                    return null;
                }

                long afterTreeRender = System.nanoTime();

                final int nbElements = allTreeElements.size();

                int entriesToPrefetch = fDebugOptions.getEntryPadding();
                int topEntry = Math.max(0,
                        paneYPosToEntryListIndex(verticalPos.fTopPos, ENTRY_HEIGHT) - entriesToPrefetch);
                int bottomEntry = Math.min(nbElements,
                        paneYPosToEntryListIndex(verticalPos.fBottomPos, ENTRY_HEIGHT) + entriesToPrefetch);

                System.out.println("topEntry=" + topEntry +", bottomEntry=" + bottomEntry);

                List<TimeGraphStateRender> stateRenders = allTreeElements.subList(topEntry, bottomEntry).stream()
                        .map(treeElem -> renderProvider.getStateRender(treeElem, renderingStartTime, renderingEndTime, resolution, this))
                        .collect(Collectors.toList());

                if (isCancelled()) {
                    System.err.println("task #" + taskSeqNb + " was cancelled before generating the contents");
                    return null;
                }

                long afterStateRenders = System.nanoTime();

                /* Prepare the tree part, if needed */
                @Nullable Node treeContents;
                if (treeRender.equals(fLatestTreeRender)) {
                    treeContents = null;
                } else {
                    fLatestTreeRender = treeRender;
                    treeContents = prepareTreeContents(treeRender, treePaneWidth);
                }

                /* Prepare the time graph part */
                Collection<StateRectangle> stateRectangles = prepareStateRectangles(stateRenders, topEntry);
                Node statesLayerContents = prepareTimeGraphStatesContents(stateRectangles);
                Node labelsLayerContents = prepareTimeGrahLabelsContents(stateRectangles, horizontalPos);

                if (isCancelled()) {
                    System.err.println("task #" + taskSeqNb + " was cancelled before updating the view");
                    return null;
                }

                long afterJavaFXObjects = System.nanoTime();

                StringJoiner sj = new StringJoiner(", ", "Repaint breakdown (#" + taskSeqNb + "): ", "")
                        .add("Generating tree render=" + String.format("%,d", afterTreeRender - start) + " ns")
                        .add("Generating state renders= " + String.format("%,d", afterStateRenders - afterTreeRender) + " ns")
                        .add("Generating JavaFX objects=" + String.format("%,d", afterJavaFXObjects - afterStateRenders) + " ns");
                System.err.println(sj.toString());


                /* Update the view! */
                Platform.runLater(() -> {
                    long startUI = System.nanoTime();
                    if (treeContents != null) {
                        fTreePane.getChildren().clear();
                        fTreePane.getChildren().add(treeContents);
                    }

                    long afterTreeUI = System.nanoTime();

                    fTimeGraphStatesLayer.getChildren().clear();
                    fTimeGraphTextLabelsLayer.getChildren().clear();
                    fTimeGraphStatesLayer.getChildren().add(statesLayerContents);
                    fTimeGraphTextLabelsLayer.getChildren().add(labelsLayerContents);

                    fTimeGraphLoadingOverlay.fadeOut();

                    long endUI = System.nanoTime();
                    StringJoiner sjui = new StringJoiner(", ", "UI Update (#" + taskSeqNb +"): ", "")
                            .add("Drawing tree=" + String.format("%,d", afterTreeUI - startUI) + " ns")
                            .add("Drawing states= " + String.format("%,d", endUI - afterTreeUI) + " ns");
                    System.err.println(sjui.toString());
                });

                return null;
            }
        };

        System.err.println("Queueing task #" + taskSeqNb);

        /*
         * Attach a listener to the task to receive exceptions thrown within the
         * task.
         */
        task.exceptionProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                newVal.printStackTrace();
            }
        });

        fTaskExecutor.schedule(task);
    }

    void paintBackground(VerticalPosition vPos) {
        final int entriesToPrefetch = fDebugOptions.getEntryPadding();

        final double timeGraphWidth = fTimeGraphPane.getWidth();
        final double timeGraphHeight = fTimeGraphPane.getHeight();
        final double paintTopPos = Math.max(0.0, vPos.fTopPos - entriesToPrefetch * ENTRY_HEIGHT);
        final double paintBottomPos = Math.min(timeGraphHeight, vPos.fBottomPos + entriesToPrefetch * ENTRY_HEIGHT);

        List<Line> lines = new LinkedList<>();
        DoubleStream.iterate((ENTRY_HEIGHT / 2), y -> y + ENTRY_HEIGHT)
                // TODO Java 9 will allow using dropWhile()/takeWhile()/collect
                .filter(y -> y > paintTopPos)
                .peek(y -> {
                    Line line = new Line(0, y, timeGraphWidth, y);
                    line.setStroke(BACKGROUD_LINES_COLOR);
                    line.setStrokeWidth(1.0);

                    lines.add(line);
                })
                .allMatch(y -> y < paintBottomPos);

        Platform.runLater(() -> {
            fTimeGraphBackgroundLayer.getChildren().clear();
            fTimeGraphBackgroundLayer.getChildren().addAll(lines);
        });
    }

    @Override
    public void drawSelection(long selectionStartTime, long selectionEndTime) {
        double xStart = timestampToPaneXPos(selectionStartTime);
        double xEnd = timestampToPaneXPos(selectionEndTime);
        double xWidth = xEnd - xStart;

        fSelectionRect.setX(xStart);
        fSelectionRect.setY(0);
        fSelectionRect.setWidth(xWidth);
        fSelectionRect.setHeight(fTimeGraphPane.getHeight());

        fSelectionRect.setVisible(true);
    }

    // ------------------------------------------------------------------------
    // Methods related to the Tree area
    // ------------------------------------------------------------------------

    private static Node prepareTreeContents(TimeGraphTreeRender treeRender, double paneWidth) {
        /* Prepare the tree element objects */
        List<Label> treeElements = treeRender.getAllTreeElements().stream()
                // TODO Put as a real tree. TreeView ?
                .map(elem -> new Label(elem.getName()))
                .peek(label -> {
                    label.setPrefHeight(ENTRY_HEIGHT);
                    label.setPadding(new Insets(0, LABEL_SIDE_MARGIN, 0, LABEL_SIDE_MARGIN));
                    /*
                     * Re-set the solid background for the labels, so we do not
                     * see the background lines through.
                     */
                    label.setStyle(BACKGROUND_STYLE);
                })
                .collect(Collectors.toList());

        VBox treeElemsBox = new VBox(); // Change to TreeView eventually ?
        treeElemsBox.getChildren().addAll(treeElements);

        /* Prepare the background layer with the horizontal alignment lines */
        List<Line> lines = DoubleStream.iterate((ENTRY_HEIGHT / 2), y -> y + ENTRY_HEIGHT)
                .limit(treeElements.size())
                .mapToObj(y -> {
                    Line line = new Line(0, y, paneWidth, y);
                    line.setStroke(BACKGROUD_LINES_COLOR);
                    line.setStrokeWidth(1.0);
                    return line;
                })
                .collect(Collectors.toList());
        Pane background = new Pane();
        background.getChildren().addAll(lines);

        /* Put the background layer and the Tree View into their containers */
        StackPane stackPane = new StackPane(background, treeElemsBox);
        stackPane.setStyle(BACKGROUND_STYLE);
        return stackPane;
    }

    // ------------------------------------------------------------------------
    // Methods related to the Time Graph area
    // ------------------------------------------------------------------------

    private Collection<StateRectangle> prepareStateRectangles(
            List<TimeGraphStateRender> stateRenders, int topEntry) {
        /* Prepare the colored state rectangles */
        Collection<StateRectangle> rectangles = IntStream.range(0, stateRenders.size()).parallel()
                .mapToObj(idx -> getRectanglesForStateRender(stateRenders.get(idx), idx + topEntry))
                .flatMap(Function.identity())
                .collect(Collectors.toSet());
        return rectangles;
    }

    private Stream<StateRectangle> getRectanglesForStateRender(TimeGraphStateRender stateRender, int entryIndex) {
        return stateRender.getStateIntervals().stream()
                .map(interval -> new StateRectangle(this, interval, entryIndex));
    }

    private static Node prepareTimeGraphStatesContents(Collection<StateRectangle> stateRectangles) {
        Group group = new Group();
        group.getChildren().addAll(stateRectangles);
        return group;
    }

    private Node prepareTimeGrahLabelsContents(Collection<StateRectangle> stateRectangles,
            HorizontalPosition horizontalPos) {
        double minX = timestampToPaneXPos(horizontalPos.fStartTime);

        final String ellipsisStr = fDebugOptions.getEllipsisString();
        final Font textFont = fDebugOptions.getTextFont();
        final OverrunStyle overrunStyle = OverrunStyle.ELLIPSIS;
        final Color textColor = Color.WHITE;

        final double yOffset = ENTRY_HEIGHT / 2.0;
        Collection<Node> texts = stateRectangles.stream()
                /* Only try to annotate rectangles that are large enough */
                .filter(stateRect -> stateRect.getWidth() > fDebugOptions.getEllipsisWidth())
                .filter(stateRect -> stateRect.getStateInterval().getLabel() != null)
                .map(stateRect -> {
                    String labelText = requireNonNull(stateRect.getStateInterval().getLabel());
                    /* A small offset looks better here */
                    double textX = Math.max(minX, stateRect.getX()) + 4.0;
                    double textY = stateRect.getY() + yOffset;

                    double rectEndX = stateRect.getX() + stateRect.getWidth();
                    double minWidth = rectEndX - textX;

                    String ellipsedText = Utils.computeClippedText(textFont,
                            labelText,
                            minWidth,
                            overrunStyle,
                            ellipsisStr);

                    if (ellipsedText.equals(ellipsisStr)) {
                        return null;
                    }

                    Text text = new Text(textX, textY, ellipsedText);
                    text.setFont(textFont);
                    text.setFill(textColor);
                    return text;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new Group(texts);
    }

    // ------------------------------------------------------------------------
    // Mouse event listeners
    // ------------------------------------------------------------------------

    /**
     * These events are to be ignored by the time graph pane, they should
     * "bubble up" to the scrollpane to be used for panning.
     */
    private static final Predicate<MouseEvent> MOUSE_EVENT_IGNORED = e -> {
        return (e.isSecondaryButtonDown() ||e.isMiddleButtonDown() || e.isControlDown());
    };

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
            fOngoingSelectionRect.setY(0);
            fOngoingSelectionRect.setWidth(0);
            fOngoingSelectionRect.setHeight(fTimeGraphPane.getHeight());

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
            double endX = Math.min(fTimeGraphPane.getWidth(), startX + fOngoingSelectionRect.getWidth());
            long tsStart = paneXPosToTimestamp(startX);
            long tsEnd = paneXPosToTimestamp(endX);

            getControl().updateTimeRangeSelection(tsStart, tsEnd);

            fOngoingSelection = false;
        };
    }

    private static class ListenerStatus {

        private final AtomicInteger fDisabledCount = new AtomicInteger(0);

        public void disable() {
            fDisabledCount.incrementAndGet();
        }

        public void enable() {
            /* Decrement the count but only if it is currently above 0 */
            fDisabledCount.updateAndGet(value -> value > 0 ? value - 1 : 0);
        }

        public boolean isEnabled() {
            return (fDisabledCount.get() == 0);
        }
    }

    /**
     * Class encapsulating the scrolling operations of the time graph pane.
     *
     * The mouse entered/exited handlers ensure only the scrollpane being
     * interacted by the user is the one sending the synchronization signals.
     */
    private class ScrollingContext {

        /* Knobs to programmatically disable the scrolling listeners */
        public final ListenerStatus fHListenerStatus = new ListenerStatus();

        private boolean fUserActionOngoing = false;

        private final EventHandler<MouseEvent> fMouseEnteredEventHandler = e -> {
            fUserActionOngoing = true;
        };

        private final EventHandler<MouseEvent> fMouseExitedEventHandler = e -> {
            fUserActionOngoing = false;
        };

        /**
         * Listener for the horizontal scrollbar changes
         */
        private final ChangeListener<Number> fHScrollChangeListener = (observable, oldValue, newValue) -> {
            if (!fDebugOptions.isScrollingListenersEnabled()) {
                System.out.println("HScroll event ignored due to debug option");
                return;
            }
            if (!fUserActionOngoing || !fHListenerStatus.isEnabled()) {
                System.out.println("HScroll listener triggered but inactive");
                return;
            }

            System.out.println("HScroll change listener triggered, oldval=" + oldValue.toString() + ", newval=" + newValue.toString());

            /* We need to specify the new value here, or else the old one will be used */
            HorizontalPosition timeRange = getTimeGraphEdgeTimestamps(newValue.doubleValue());
            long tsStart = timeRange.fStartTime;
            long tsEnd = timeRange.fEndTime;

            System.out.printf("Sending visible range update: %,d to %,d%n", tsStart, tsEnd);

            getControl().updateVisibleTimeRange(tsStart, tsEnd, false);

            /*
             * We ask the control to not send this signal back to us (to avoid
             * jitter while scrolling), but the next UI update should refresh
             * the view accordingly.
             *
             * It is not our responsibility to update to this
             * HorizontalPosition. The control will update accordingly upon
             * managing the signal we just sent.
             */
        };
    }

    /**
     * The default scroll value seems to be in increments of 40.0 units.
     * Consider one (default) scroll to correspond to one zoom in/out action.
     */
    private static final double SCROLL_FACTOR = 40.0;

    /**
     * Event handler attached to the *time graph pane*, to execute zooming
     * operations when the control key is down (otherwise, it just lets the even
     * bubble to the ScrollPane, which will do a standard scroll).
     */
    private final EventHandler<ScrollEvent> fMouseScrollListener = e -> {
        if (!e.isControlDown()) {
            return;
        }
        double delta = e.getDeltaY();
        boolean zoomIn = (delta > 0.0); // false means a zoom-out
        int nbActions = (int) Math.abs(Math.round(delta / SCROLL_FACTOR));

        /*
         * getX() corresponds to the X position of the mouse on the time graph.
         * This is seriously awesome.
         */
        // TODO Support passing a pivotX to the zoom() method below
//        double originX = e.getX();

        for (int i = 0; i < nbActions; i++) {
            fZoomActions.zoom(null, zoomIn);
        }

        e.consume();
    };

    // ------------------------------------------------------------------------
    // View-specific actions
    // These do not come from the control, but from the view itself
    // ------------------------------------------------------------------------

    class ZoomActions {

        private double fCurrentTemporaryZoomFactor = 1.0;

        public void zoom(@Nullable Double pivotX, boolean zoomIn) {
            final double zoomStep = fDebugOptions.getZoomStep();
            final long zoomAnimationDuration = fDebugOptions.getZoomAnimationDuration();

            /*
             * Compute and add a new temporary transform on the pane, for the
             * zoom animation. It's fine to accumulate those here, the next
             * "real" UI update with clear() them, and resize the pane
             * accordingly.
             */
            double visibleXStart = -fTimeGraphScrollPane.getViewportBounds().getMinX();
            double pivotPos;
            if (pivotX == null) {
                double visibleWidth = fTimeGraphScrollPane.getWidth();
                pivotPos = visibleXStart + (visibleWidth / 2);
            } else {
                pivotPos = pivotX;
            }

            double initialScaleFactor = fCurrentTemporaryZoomFactor;
            double newScaleFactor = (zoomIn ?
                        initialScaleFactor * (1 + zoomStep) :
                        initialScaleFactor * (1 / (1 + zoomStep)));
            fCurrentTemporaryZoomFactor = newScaleFactor;

            Scale scale = new Scale();
            scale.setPivotX(pivotPos);
            scale.setX(initialScaleFactor);
            fTimeGraphPane.getTransforms().setAll(scale);

            Timeline timeline = new Timeline();
            /* Disable the HScroll listener during the animation */
            fScrollingCtx.fHListenerStatus.disable();
            timeline.setOnFinished(e -> fScrollingCtx.fHListenerStatus.enable());

            timeline.getKeyFrames().addAll(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(scale.xProperty(), initialScaleFactor)),
                    new KeyFrame(new Duration(zoomAnimationDuration),
                            new KeyValue(scale.xProperty(), newScaleFactor))
                    );
            timeline.play();

            // TODO Update the fTimeGraphScrollPane.hValue() so that the
            // scrollpane stays centered on the pivot. This is not done
            // automatically unfortunately.

            /* Send a corresponding window-range signal to the control */
            TimeGraphModelControl control = getControl();
            long curStart = control.getVisibleTimeRangeStart();
            long curEnd = control.getVisibleTimeRangeEnd();
            long curRange = curEnd - curStart;
            /* Shrink the time range by half the ZOOM_FACTOR on each side */
            double newRange = curRange * (1.0 / newScaleFactor);
            double diff = curRange - newRange;
            long newStart = curStart + Math.round(diff / 2.0);
            long newEnd = curEnd - Math.round(diff / 2.0);

            control.updateVisibleTimeRange(newStart, newEnd, true);
        }

        public void resetZoomFactor() {
            fCurrentTemporaryZoomFactor = 1.00;
        }

    }

    // ------------------------------------------------------------------------
    // Common utils
    // ------------------------------------------------------------------------

    /**
     * Determine the timestamps currently represented by the left and right
     * edges of the time graph pane. In other words, the current "visible range"
     * the view is showing.
     *
     * Note that this method gets its information from UI objects only, so there
     * might be discrepancies between this and the results of
     * {@link #getCurrentHorizontalPosition()}.
     *
     * @param newHValue
     *            The "hvalue" property of the horizontal scrollbar to use. If
     *            null, the current value will be retrieved from the scenegraph
     *            object. For example, a scrolling listener might want to pass
     *            its newValue here, since the scenegraph object will not have
     *            been updated yet.
     * @return The corresponding timestamps, wrapped in a
     *         {@link HorizontalPosition}.
     */
    HorizontalPosition getTimeGraphEdgeTimestamps(@Nullable Double newHValue) {
        double hvalue = (newHValue == null ? fTimeGraphScrollPane.getHvalue() : newHValue.doubleValue());

        /*
         * Determine the X positions represented by the edges.
         */
        double hmin = fTimeGraphScrollPane.getHmin();
        double hmax = fTimeGraphScrollPane.getHmax();
        double contentWidth = fTimeGraphPane.getLayoutBounds().getWidth();
        double viewportWidth = fTimeGraphScrollPane.getViewportBounds().getWidth();
        double hoffset = Math.max(0, contentWidth - viewportWidth) * (hvalue - hmin) / (hmax - hmin);

        /*
         * Convert the positions of the left and right edges to timestamps.
         */
        long tsStart = paneXPosToTimestamp(hoffset);
        long tsEnd = paneXPosToTimestamp(hoffset + viewportWidth);

        return new HorizontalPosition(tsStart, tsEnd);
    }

    double timestampToPaneXPos(long timestamp) {
        long fullTimeGraphStartTime = getControl().getFullTimeGraphStartTime();
        long fullTimeGraphEndTime = getControl().getFullTimeGraphEndTime();
        return timestampToPaneXPos(timestamp, fullTimeGraphStartTime, fullTimeGraphEndTime, fNanosPerPixel);
    }

    @VisibleForTesting
    static double timestampToPaneXPos(long timestamp, long start, long end, double nanosPerPixel) {
        if (timestamp < start) {
            throw new IllegalArgumentException(timestamp + " is smaller than trace start time " + start); //$NON-NLS-1$
        }
        if (timestamp > end) {
            throw new IllegalArgumentException(timestamp + " is greater than trace end time " + end); //$NON-NLS-1$
        }

        double traceTimeRange = end - start;
        double timeStampRatio = (timestamp - start) / traceTimeRange;

        long fullTraceWidthInPixels = (long) (traceTimeRange / nanosPerPixel);
        double xPos = fullTraceWidthInPixels * timeStampRatio;
        return Math.round(xPos);
    }

    long paneXPosToTimestamp(double x) {
        long fullTimeGraphStartTime = getControl().getFullTimeGraphStartTime();
        return paneXPosToTimestamp(x, fTimeGraphPane.getWidth(), fullTimeGraphStartTime, fNanosPerPixel);
    }

    @VisibleForTesting
    static long paneXPosToTimestamp(double x, double totalWidth, long startTimestamp, double nanosPerPixel) {
        if (x < 0.0 || totalWidth < 1.0 || x > totalWidth) {
            throw new IllegalArgumentException("Invalid position arguments: pos=" + x + ", width=" + totalWidth);
        }

        long ts = Math.round(x * nanosPerPixel);
        return ts + startTimestamp;
    }

    /**
     * Get the current vertical position of the timegraph.
     *
     * @return The corresponding VerticalPosition
     */
    VerticalPosition getCurrentVerticalPosition() {
        double vvalue = fTimeGraphScrollPane.getVvalue();

        /* Get the Y position of the top/bottom edges of the pane */
        double vmin = fTreeScrollPane.getVmin();
        double vmax = fTreeScrollPane.getVmax();
        double contentHeight = fTreePane.getLayoutBounds().getHeight();
        double viewportHeight = fTreeScrollPane.getViewportBounds().getHeight();

        double vtop = Math.max(0, contentHeight - viewportHeight) * (vvalue - vmin) / (vmax - vmin);
        double vbottom = vtop + viewportHeight;

        return new VerticalPosition(vtop, vbottom);
    }

    private static int paneYPosToEntryListIndex(double yPos, double entryHeight) {
        if (yPos < 0.0 || entryHeight < 0.0) {
            throw new IllegalArgumentException();
        }

        return (int) (yPos / entryHeight);
    }

    // ------------------------------------------------------------------------
    // Test accessors
    // ------------------------------------------------------------------------

    // could eventually be exposed to the user, as "advanced preferences"
    DebugOptions getDebugOptions() {
        return fDebugOptions;
    }

    @VisibleForTesting
    double getCurrentNanosPerPixel() {
        return fNanosPerPixel;
    }

    @VisibleForTesting
    Pane getTimeGraphPane() {
        return fTimeGraphPane;
    }

    @VisibleForTesting
    ScrollPane getTimeGraphScrollPane() {
        return fTimeGraphScrollPane;
    }

    @VisibleForTesting
    ZoomActions getZoomActions() {
        return fZoomActions;
    }
}
