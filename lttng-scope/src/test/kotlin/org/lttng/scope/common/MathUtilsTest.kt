/*
 * Copyright (C) 2017-2018 EfficiOS Inc., Alexandre Montplaisir <alexmonthy@efficios.com>
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.lttng.scope.common

import org.hamcrest.core.Is.`is`
import org.junit.Assert.assertThat
import org.junit.Test

/**
 * Tests for MathUtils.
 */
class MathUtilsTest {

    /**
     * Test the {@link MathUtils#roundToClosestHigherMultiple(long, long)}
     * method.
     */
    @Test
    fun testRoundToClosestHigherMultiple() {
        assertThat(12L.roundToClosestHigherMultiple(10L), `is`(20L))
        assertThat(20L.roundToClosestHigherMultiple(20L), `is`(20L))
    }
}