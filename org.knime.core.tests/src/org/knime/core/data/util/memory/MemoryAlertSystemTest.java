/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   17.03.2015 (thor): created
 */
package org.knime.core.data.util.memory;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.knime.core.node.NodeLogger;

/**
 * Testcase for {@link MemoryAlertSystem}.
 *
 * @author Thorsten Meinl, KNIME AG, Zurich, Switzerland
 * @author Marc Bux, KNIME GmbH, Berlin, Germany
 */
public class MemoryAlertSystemTest {

    // some JVMs even throw an "java.lang.OutOfMemoryError: Requested array size exceeds VM limit" for values below
    // Integer.MAX_VALUE
    private static final int MAX_ARRAY_LENGTH = 1000000000;

    private MemoryAlertSystem m_memSystem;

    /**
     * Checks that enough memory is available before each test.
     *
     * @throws Exception if an error occurs
     */
    @Before
    public void checkAvailableMemory() throws Exception {
        for (MemoryAlertSystem mas : new LinkedHashSet<>(Arrays.asList(
            MemoryAlertSystem.getInstance(), MemoryAlertSystem.getInstanceUncollected()))) {
            mas.sendMemoryAlert();
        }
        Thread.sleep(1000);
        forceGC();
        for (int i = 0; i < 10 && MemoryAlertSystem.getInstance().isMemoryLow(); i++) {
            forceGC();
            Thread.sleep(1000);
        }
        assertThat("Cannot test because memory usage is already above threshold: " + MemoryAlertSystem.getUsage(),
            MemoryAlertSystem.getInstance().isMemoryLow(), is(false));
        m_memSystem = MemoryAlertSystem.getInstance();
        NodeLogger.getLogger(getClass()).debug("Memory usage: " + MemoryAlertSystem.getUsedMemory() + "/"
            + MemoryAlertSystem.getMaximumMemory() + " => " + MemoryAlertSystem.getUsage());
        forceGC();
    }

    private static List<Integer> determineReserveSizeSplits() {
        final long max = MemoryAlertSystem.getMaximumMemory();
        final long used = MemoryAlertSystem.getUsedMemory();
        long reserveSize = (long)(MemoryAlertSystem.DEFAULT_USAGE_THRESHOLD * (max - used));
        final List<Integer> reserveSizeSplits = new ArrayList<>();
        while (reserveSize > MAX_ARRAY_LENGTH) {
            reserveSizeSplits.add(MAX_ARRAY_LENGTH);
            reserveSize -= MAX_ARRAY_LENGTH;
        }
        reserveSizeSplits.add((int)reserveSize);
        return reserveSizeSplits;
    }

    /**
     * Checks whether listeners are notified correctly.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testListener() throws Exception {
        final List<Integer> reserveSizeSplits = determineReserveSizeSplits();

        final AtomicBoolean listenerCalled = new AtomicBoolean();
        MemoryAlertListener listener = new MemoryAlertListener() {
            @Override
            protected boolean memoryAlert(final MemoryAlert alert) {
                NodeLogger.getLogger(MemoryAlertSystemTest.class)
                    .debug("Memory listener called, current usage is " + MemoryAlertSystem.getUsage());
                listenerCalled.set(true);
                return false;
            }
        };

        m_memSystem.addListener(listener);
        try {
            forceGC();
            Thread.sleep(1000);
            assertThat("Alert listener called although usage is below threshold: " + MemoryAlertSystem.getUsage(),
                listenerCalled.get(), is(false));

            final byte[][] bufs = new byte[reserveSizeSplits.size()][];
            for (int i = 0; i < reserveSizeSplits.size(); i++) {
                bufs[i] = new byte[reserveSizeSplits.get(i)];
            }
            forceGC();
            Thread.sleep(1000);
            assertThat("Alert listener not called although usage is above threshold: " + MemoryAlertSystem.getUsage(),
                listenerCalled.get(), is(true));
        } finally {
            m_memSystem.removeListener(listener);
        }
    }

    /**
     * Checks whether listeners are removed automatically.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testAutoRemoveListener() throws Exception {
        final List<Integer> reserveSizeSplits = determineReserveSizeSplits();

        final AtomicBoolean listenerCalled = new AtomicBoolean();
        MemoryAlertListener listener = new MemoryAlertListener() {
            @Override
            protected boolean memoryAlert(final MemoryAlert alert) {
                listenerCalled.set(true);
                return true;
            }
        };

        m_memSystem.addListener(listener);
        try {
            forceGC();
            Thread.sleep(1000);
            assertThat("Alert listener called although usage is below threshold: " + MemoryAlertSystem.getUsage(),
                listenerCalled.get(), is(false));

            final byte[][] bufs = new byte[reserveSizeSplits.size()][];
            for (int i = 0; i < reserveSizeSplits.size(); i++) {
                bufs[i] = new byte[reserveSizeSplits.get(i)];
            }
            forceGC();
            Thread.sleep(1000);
            assertThat("Alert listener not called although usage is above threshold: " + MemoryAlertSystem.getUsage(),
                listenerCalled.getAndSet(false), is(true));

            boolean removed = m_memSystem.removeListener(listener);
            assertThat("Listener was not removed automatically", removed, is(false));
        } finally {
            m_memSystem.removeListener(listener);
        }
    }

    /**
     * Forces a GC run. By using weak reference {@link System#gc()} is called until the weak reference has been cleared.
     *
     * @throws InterruptedException if the thread is interrupted
     */
    public static void forceGC() throws InterruptedException {
        Object obj = new Object();
        final WeakReference<Object> ref = new WeakReference<>(obj);
        obj = null;
        int max = 10;
        while ((ref.get() != null) && (max-- > 0) && !Thread.currentThread().isInterrupted()) {
            System.gc();
            Thread.sleep(50);
        }

        NodeLogger.getLogger(MemoryAlertSystemTest.class)
            .debug("Called System.gc, memory usage is now " + MemoryAlertSystem.getUsage());
    }
}
