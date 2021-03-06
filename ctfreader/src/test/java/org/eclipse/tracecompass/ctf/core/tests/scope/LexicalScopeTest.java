/*******************************************************************************
 * Copyright (c) 2014 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Matthew Khouzam - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.ctf.core.tests.scope;

import org.eclipse.tracecompass.ctf.core.event.scope.ILexicalScope;
import org.eclipse.tracecompass.ctf.core.event.scope.LexicalScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lexical test
 *
 * @author Matthew Khouzam
 */
class LexicalScopeTest {

    /**
     * Root test
     */
    @Test
    void testRoot() {
        ILexicalScope scope = ILexicalScope.ROOT;
        assertNotNull(scope);
    }

    /**
     * Test a more complex node
     */
    @Test
    void testComplexNode() {
        ILexicalScope scope = ILexicalScope.STREAM_EVENT_CONTEXT;
        assertEquals("context", scope.getName());
        assertEquals("stream.event.context", scope.getPath());
    }

    /**
     * Test that getChild returns the same items for event headers
     */
    @Test
    void testEventHeaders() {
        ILexicalScope child = ILexicalScope.ROOT.getChild("event");
        assertNotNull(child);
        ILexicalScope scope2 = child.getChild("header");
        ILexicalScope scope3 = ILexicalScope.ROOT.getChild("event.header");
        assertEquals(ILexicalScope.EVENT_HEADER, scope2);
        assertEquals(ILexicalScope.EVENT_HEADER, scope3);
        // they should be the same
        assert (ILexicalScope.EVENT_HEADER == scope2);

        assertNotNull(scope2);
        ILexicalScope id = scope2.getChild("id");
        assertNotNull(id);
        assert (ILexicalScope.EVENT_HEADER_ID == id);
        ILexicalScope ts = scope2.getChild("v.timestamp");
        ILexicalScope v = scope2.getChild("v");
//        assert (ILexicalScope.EVENT_HEADER_V_TIMESTAMP == ts);
        assert (ILexicalScope.EVENT_HEADER_V == v);
        assertNotNull(v);
        ILexicalScope ts2 = v.getChild("timestamp");
        assert (ILexicalScope.EVENT_HEADER_V_TIMESTAMP == ts2);
        assertNotNull(v);
        id = v.getChild("id");
        assert (ILexicalScope.EVENT_HEADER_V_ID == id);
        assertNotNull(v);
        ILexicalScope other = v.getChild("other");
        assertNull(other);
    }

    /**
     * Test that getChild returns the same items for event headers
     */
    @Test
    void testFields() {
        ILexicalScope child = ILexicalScope.ROOT.getChild("fields");
        assertNotNull(child);
        ILexicalScope scope2 = child.getChild("_ret");
        ILexicalScope scope3 = child.getChild("_tid");
        ILexicalScope empty = child.getChild("other");

        assertEquals(ILexicalScope.FIELDS_RET, scope2);
        // they should be the same
        assert (ILexicalScope.FIELDS_RET == scope2);

        assertEquals(ILexicalScope.FIELDS_TID, scope3);
        // they should be the same
//        assert (ILexicalScope.FIELDS_TID == scope2);

        assertNull(empty);
    }

    /**
     * Check contexts are not equals
     */
    @Test
    void testNotEquals() {
        assertNotEquals(ILexicalScope.CONTEXT, ILexicalScope.EVENT);
        LexicalScope context = new LexicalScope(ILexicalScope.CONTEXT, "context");
        LexicalScope otherContext = new LexicalScope(ILexicalScope.CONTEXT, "context2");
        assertNotEquals(context, otherContext);
        assertNotEquals(context, null);
    }

    /**
     * Test to strings
     */
    @Test
    void testGetPath() {
        ILexicalScope child = ILexicalScope.ROOT.getChild("fields");
        assertNotNull(child);
        ILexicalScope scope2 = child.getChild("_ret");
        assertNotNull(scope2);
        assertEquals("fields._ret", scope2.getPath());
    }
}
