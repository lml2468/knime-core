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
 *   May 15, 2020 (hornm): created
 */
package org.knime.core.node.workflow.virtual.parchunk;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.knime.core.node.ExecutionContext;
import org.knime.core.node.exec.dataexchange.PortObjectRepository;
import org.knime.core.node.workflow.FlowScopeContext;
import org.knime.core.node.workflow.NativeNodeContainer;

/**
 * Marks a virtual scope, i.e. a scope (a set of nodes) that is not permanently present and deleted after the execution
 * of all contained nodes.
 *
 * A virtual scope is marked by the {@link VirtualParallelizedChunkPortObjectInNodeModel} and
 * {@link VirtualParallelizedChunkPortObjectOutNodeModel}.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 *
 * @noreference This class is not intended to be referenced by clients.
 * @noinstantiate This class is not intended to be instantiated by clients.
 */
public final class FlowVirtualScopeContext extends FlowScopeContext {

    private Consumer<Function<ExecutionContext, UUID>> m_callback;

    private NativeNodeContainer m_nc;

    /**
     * Sets the callback to get informed about the ids of port objects that have been put into the
     * {@link PortObjectRepository}.
     *
     * To be able to store a port object in the port object repository some port object implementations need to
     * be copied. Hence the {@link ExecutionContext} that needs to be provided in order to get the final id back.
     *
     * @param callback a function to get the id by providing an execution context (mainly needed to copy the port
     *            objects)
     */
    public void setPortObjectIDCallback(final Consumer<Function<ExecutionContext, UUID>> callback) {
        m_callback = callback;
    }

    /**
     * @see #setPortObjectIDCallback(Consumer)
     * @return the callback
     */
    public Consumer<Function<ExecutionContext, UUID>> getPortObjectIDCallback() {
        return m_callback;
    }

    /**
     * Sets the node container that is (indirectly) responsible for the creation of this virtual scope. The file
     * handlers of this node, e.g., will be used. Must be set before the scope can be executed.
     *
     * This node container does not need to be provided if the start node of this virtual scope is connected (upstream)
     * to another loop start (as it is the case with the parallel chunk loop start node). In all other case it must be
     * set.
     *
     * @param nc the node
     */
    public void setNodeContainer(final NativeNodeContainer nc) {
        m_nc= nc;
    }

    /**
     * @return the node associated with this virtual scope (see {@link #setNodeContainer(NativeNodeContainer)}) or an
     *         empty optional if there is no node associated.
     */
    public Optional<NativeNodeContainer> getNodeContainer() {
        return Optional.ofNullable(m_nc);
    }

}
