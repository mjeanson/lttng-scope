/*******************************************************************************
 * Copyright (c) 2015 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package com.efficios.jabberwocky.lttng.ust.trace.layout;

/**
 * Definitions used in LTTng-UST for versions 2.0 up to 2.6.
 *
 * @author Alexandre Montplaisir
 */
@SuppressWarnings("nls")
public class LttngUst20EventLayout implements ILttngUstEventLayout {

    /**
     * Constructor
     */
    protected LttngUst20EventLayout() {}

    private static final LttngUst20EventLayout INSTANCE = new LttngUst20EventLayout();

    /**
     * Get a singleton instance.
     *
     * @return The instance
     */
    public static LttngUst20EventLayout getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------------
    // Event names used in liblttng-ust-libc-wrapper
    // ------------------------------------------------------------------------

    @Override
    public String eventLibcMalloc() {
        return "ust_libc:malloc";
    }

    @Override
    public String eventLibcCalloc() {
        return "ust_libc:calloc";
    }

    @Override
    public String eventLibcRealloc() {
        return "ust_libc:realloc";
    }

    @Override
    public String eventLibcFree() {
        return "ust_libc:free";
    }

    @Override
    public String eventLibcMemalign() {
        return "ust_libc:memalign";
    }

    @Override
    public String eventLibcPosixMemalign() {
        return "ust_libc:posix_memalign";
    }

    // ------------------------------------------------------------------------
    // Event names used in liblttng-cyg-profile
    // ------------------------------------------------------------------------

    @Override
    public String eventCygProfileFuncEntry() {
        return "lttng_ust_cyg_profile:func_entry";
    }

    @Override
    public String eventCygProfileFastFuncEntry() {
        return "lttng_ust_cyg_profile_fast:func_entry";
    }

    @Override
    public String eventCygProfileFuncExit() {
        return "lttng_ust_cyg_profile:func_exit";
    }

    @Override
    public String eventCygProfileFastFuncExit() {
        return "lttng_ust_cyg_profile_fast:func_exit";
    }

    // ------------------------------------------------------------------------
    // Event names used in liblttng-ust-dl
    // ------------------------------------------------------------------------

    @Override
    public String eventDlOpen() {
        return "lttng_ust_dl:dlopen";
    }

    @Override
    public String eventDlClose() {
        return "lttng_ust_dl:dlclose";
    }

    // ------------------------------------------------------------------------
    // Field names
    // ------------------------------------------------------------------------

    @Override
    public String fieldPtr() {
        return "ptr";
    }

    @Override
    public String fieldNmemb() {
        return "nmemb";
    }

    @Override
    public String fieldSize() {
        return "size";
    }

    @Override
    public String fieldOutPtr() {
        return "out_ptr";
    }

    @Override
    public String fieldInPtr() {
        return "in_ptr";
    }

    @Override
    public String fieldAddr() {
        return "addr";
    }

    // ------------------------------------------------------------------------
    // Context field names
    // ------------------------------------------------------------------------

    @Override
    public String contextVpid() {
        return "context.vpid";
    }

    @Override
    public String contextVtid() {
        return "context.vtid";
    }

    @Override
    public String contextProcname() {
        return "context.procname";
    }

    @Override
    public String contextIp() {
        return "context.ip";
    }
}
