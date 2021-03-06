/*******************************************************************************
 * Copyright (c) 2014 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Matthew Khouzam - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.ctf.core.event.scope;

import org.jetbrains.annotations.Nullable;

/**
 * A lttng specific speedup node (the packet header with ID and V) of a lexical
 * scope
 *
 * @author Matthew Khouzam
 */
public final class EventHeaderScope extends LexicalScope {


    /**
     * The scope constructor
     *
     * @param parent
     *            The parent node, can be null, but shouldn't
     * @param name
     *            the name of the field
     */
    EventHeaderScope(ILexicalScope parent, String name) {
        super(parent, name);
    }

    @Override
    public @Nullable ILexicalScope getChild(String name) {
        if (name.equals(EVENT_HEADER_ID.getName())) {
            return EVENT_HEADER_ID;
        }
        if (name.equals(EVENT_HEADER_V.getName())) {
            return EVENT_HEADER_V;
        }
        return super.getChild(name);
    }

    @Override
    public String getPath() {
        return "event.header"; //$NON-NLS-1$
    }

}
