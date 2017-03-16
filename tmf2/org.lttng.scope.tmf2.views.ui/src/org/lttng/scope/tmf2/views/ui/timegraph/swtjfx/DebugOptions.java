/*
 * Copyright (C) 2017 EfficiOS Inc., Alexandre Montplaisir <alexmonthy@efficios.com>
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.lttng.scope.tmf2.views.ui.timegraph.swtjfx;

/**
 * Debug options for the {@link SwtJfxTimeGraphViewer}. Advanced users or unit
 * tests might want to modify these.
 *
 * @author Alexandre Montplaisir
 */
class DebugOptions {

    private int fEntryPadding = 5;
    private double fRenderRangePadding = 0.1;
    private int fUIUpdateDelay = 250;
    private boolean fScrollingListenersEnabled = true;

    /**
     * Constructor using the default options
     */
    public DebugOptions() {
    }

    /**
     * Number of tree elements to print above *and* below the visible range
     *
     * @return The number of entries
     */
    public int getEntryPadding() {
        return fEntryPadding;
    }

    /**
     * How much "padding" around the current visible window, on the left and
     * right, should be pre-rendered. Expressed as a fraction of the current
     * window (for example, 1.0 would render one "page" on each side).
     *
     * @return The fraction of padding on each side
     */
    public double getRenderRangePadding() {
        return fRenderRangePadding;
    }

    /**
     * Time between UI updates, in milliseconds
     *
     * @return The delay in milliseconds
     */
    public int getUIUpdateDelay() {
        return fUIUpdateDelay;
    }

    /**
     * Whether the view should respond to vertical or horizontal scrolling
     * actions.
     *
     * @return If scrolling listeners are enabled
     */
    public boolean isScrollingListenersEnabled() {
        return fScrollingListenersEnabled;
    }

    void setScrollingListenersEnabled(boolean bool) {
        fScrollingListenersEnabled = bool;
    }

}