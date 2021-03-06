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
 *   Mar 4, 2020 (hornm): created
 */
package org.knime.core.util.workflowsummary;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnDomain;
import org.knime.core.data.DataColumnProperties;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.internal.ReferencedFile;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.DynamicNodeFactory;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.config.Config;
import org.knime.core.node.config.base.AbstractConfigEntry;
import org.knime.core.node.config.base.ConfigEntries;
import org.knime.core.node.config.base.JSONConfig;
import org.knime.core.node.config.base.JSONConfig.WriterConfig;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.port.inactive.InactiveBranchPortObjectSpec;
import org.knime.core.node.util.NodeExecutionJobManagerPool;
import org.knime.core.node.workflow.ConnectionContainer;
import org.knime.core.node.workflow.FlowObjectStack;
import org.knime.core.node.workflow.FlowVariable.Scope;
import org.knime.core.node.workflow.MetaNodeTemplateInformation;
import org.knime.core.node.workflow.MetaNodeTemplateInformation.Role;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeContainerTemplate;
import org.knime.core.node.workflow.NodeExecutionJobManager;
import org.knime.core.node.workflow.NodeGraphAnnotation;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.NodeOutPort;
import org.knime.core.node.workflow.NodeTimer;
import org.knime.core.node.workflow.SingleNodeContainer;
import org.knime.core.node.workflow.SubNodeContainer;
import org.knime.core.node.workflow.WorkflowLock;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor;
import org.knime.core.node.workflow.metadata.MetaInfoFile;
import org.knime.core.node.workflow.metadata.MetadataXML;
import org.knime.core.util.workflowsummary.WorkflowSummaryConfiguration.SummaryFormat;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Helper class to generate a summary from a workflow.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 *
 * @noreference This class is not intended to be referenced by clients.
 * @noinstantiate This class is not intended to be instantiated by clients.
 */
public final class WorkflowSummaryGenerator {

    private static final String V_1_0_0 = "1.0.0";

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss Z";

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private static XmlMapper XML_MAPPER;

    private static JsonMapper JSON_MAPPER;

    private WorkflowSummaryGenerator() {
        // utility class
    }

    /**
     * Generates the summary.
     *
     * @param wfm the workflow manager to generate the summary for
     * @param out the stream to write the summary to
     * @param config the configuration for the summary generation
     * @throws IOException
     */
    public static void generate(final WorkflowManager wfm, final OutputStream out,
        final WorkflowSummaryConfiguration config) throws IOException {
        try (WorkflowLock lock = wfm.lock()) {
            if (config.m_format == SummaryFormat.XML) {
                getXmlMapper().writeValue(out, new WorkflowSummary(wfm, config));
            } else {
                getJsonMapper().writeValue(out, new WorkflowSummary(wfm, config));
            }
        }
    }

    private static XmlMapper getXmlMapper() {
        if (XML_MAPPER == null) {
            JacksonXmlModule xmlModule = new JacksonXmlModule();
            xmlModule.setDefaultUseWrapper(false);
            XML_MAPPER = new XmlMapper(xmlModule);
            //fixes some prefix problems
            XML_MAPPER.getFactory().getXMLOutputFactory().setProperty("javax.xml.stream.isRepairingNamespaces", false);
            XML_MAPPER.setSerializationInclusion(Include.NON_NULL);
        }
        return XML_MAPPER;

    }

    private static JsonMapper getJsonMapper() {
        if (JSON_MAPPER == null) {
            JSON_MAPPER = new JsonMapper();
            JSON_MAPPER.setSerializationInclusion(Include.NON_NULL);
        }
        return JSON_MAPPER;
    }

    private static Integer getGraphDepth(final NodeContainer nc) {
        Set<NodeGraphAnnotation> nga = nc.getParent().getNodeGraphAnnotation(nc.getID());
        if (!nga.isEmpty()) {
            return nga.iterator().next().getDepth();
        }
        return null;
    }

    @JacksonXmlRootElement
    @JsonAutoDetect(getterVisibility = Visibility.NON_PRIVATE)
    @JsonPropertyOrder({"version", "summaryCreationDateTime", "environment", "workflow"})
    private static class WorkflowSummary {

        private WorkflowSummaryConfiguration m_config;

        private WorkflowManager m_wfm;

        WorkflowSummary(final WorkflowManager wfm, final WorkflowSummaryConfiguration config) {
            m_wfm = wfm;
            m_config = config;
        }

        @JacksonXmlProperty(isAttribute = true)
        String getVersion() {
            return V_1_0_0;
        }

        @JacksonXmlProperty(isAttribute = true)
        String getSummaryCreationDateTime() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
            return formatter.format(ZonedDateTime.now());
        }

        Environment getEnvironment() {
            if (m_config.m_includeExecutionInfo) {
                return Environment.create();
            } else {
                return null;
            }
        }

        Workflow getWorkflow() {
            return Workflow.create(m_wfm, m_config);
        }

    }

    @JsonPropertyOrder({"knimeVersion", "os", "installation", "systemProperties"})
    private interface Environment {
        @JacksonXmlProperty(isAttribute = true)
        String getOS();

        @JacksonXmlProperty(isAttribute = true)
        String getKnimeVersion();

        Installation getInstallation();

        Map<String, String> getSystemProperties();

        static Environment create() {
            return new Environment() {

                @Override
                public String getOS() {
                    return KNIMEConstants.getOSVariant();
                }

                @Override
                public String getKnimeVersion() {
                    return KNIMEConstants.VERSION;
                }

                @Override
                public Installation getInstallation() {
                    return Installation.create();
                }

                @Override
                public Map<String, String> getSystemProperties() {
                    Properties sysProps = System.getProperties();
                    Map<String, String> res = new HashMap<>();
                    for (Entry<?, ?> entry : sysProps.entrySet()) {
                        res.put(entry.getKey().toString(), entry.getValue().toString());
                    }
                    return res;
                }
            };
        }
    }

    private interface Installation {
        @JacksonXmlProperty(localName = "plugin")
        @JacksonXmlElementWrapper(localName = "plugins")
        List<Plugin> getPlugins();

        static Installation create() {
            return new Installation() {

                @Override
                public List<Plugin> getPlugins() {
                    return Plugin.create();
                }

            };
        }
    }

    @JsonPropertyOrder({"name", "version"})
    private interface Plugin {
        @JacksonXmlProperty(isAttribute = true)
        String getName();

        @JacksonXmlProperty(isAttribute = true)
        String getVersion();

        static List<Plugin> create() {
            BundleContext bundleContext = FrameworkUtil.getBundle(WorkflowSummaryGenerator.class).getBundleContext();
            //this plugin needs to be 'activated', bundle context is null otherwise
            Bundle[] bundles = bundleContext.getBundles();
            return Arrays.stream(bundles).map(b -> {
                return new Plugin() {
                    @Override
                    public String getName() {
                        return b.getSymbolicName();
                    }

                    @Override
                    public String getVersion() {
                        return b.getVersion().toString();
                    }
                };
            }).collect(toList());
        }
    }

    @JsonPropertyOrder({"nodes", "annotations", "metadata"})
    private interface Workflow {

        @JacksonXmlProperty(isAttribute = true)
        String getName();

        @JacksonXmlProperty(localName = "node")
        @JacksonXmlElementWrapper(localName = "nodes")
        List<Node> getNodes();

        @JacksonXmlProperty(localName = "annotation")
        @JacksonXmlElementWrapper(localName = "annotations")
        List<String> getAnnotations();

        WorkflowMetadata getMetadata();

        static Workflow create(final WorkflowManager wfm, final WorkflowSummaryConfiguration config) {
            return new Workflow() {

                @Override
                public String getName() {
                    return wfm.getName();
                }

                @Override
                public List<Node> getNodes() {
                    Stream<NodeContainer> stream = wfm.getNodeContainers().stream();
                    if (config.m_nodesToIgnore != null) {
                        stream = stream.filter(nc -> !config.m_nodesToIgnore.contains(nc.getID()));
                    }
                    Comparator<NodeContainer> comp = Comparator.comparing(WorkflowSummaryGenerator::getGraphDepth)
                        .thenComparing(NodeContainer::getID);
                    return stream.sorted(comp::compare).map(nc -> Node.create(nc, config)).collect(Collectors.toList());
                }

                @Override
                public List<String> getAnnotations() {
                    return wfm.getWorkflowAnnotations().stream().map(wa -> wa.getData().getText()).collect(toList());
                }

                @Override
                public WorkflowMetadata getMetadata() {
                    return WorkflowMetadata.create(wfm);
                }

            };
        }
    }

    @JsonPropertyOrder({"id", "name", "type", "state", "graphDepth", "annotation", "metanode", "component",
        "factoryKey", "nodeMessage", "settings", "outputs", "subWorkflow", "executionStatistics", "jobManager",
        "deprecated", "parentId", "linkInfo", "flowVariables"})
    private interface Node {

        @JacksonXmlProperty(isAttribute = true)
        String getId();

        @JacksonXmlProperty(isAttribute = true)
        String getName();

        @JacksonXmlProperty(isAttribute = true)
        String getType();

        @JacksonXmlProperty(isAttribute = true)
        String getAnnotation();

        @JacksonXmlProperty(isAttribute = true)
        Boolean isMetanode();

        @JacksonXmlProperty(isAttribute = true)
        Boolean isComponent();

        @JacksonXmlProperty(isAttribute = true)
        String getState();

        @JacksonXmlProperty(isAttribute = true)
        Integer getGraphDepth();

        @JacksonXmlProperty(isAttribute = true)
        Boolean isDeprecated();

        @JacksonXmlProperty(isAttribute = true)
        String getParentId();

        NodeFactoryKey getFactoryKey();

        NodeMessage getNodeMessage();

        @JacksonXmlProperty(localName = "setting")
        @JacksonXmlElementWrapper(localName = "settings")
        List<Setting> getSettings();

        Workflow getSubWorkflow();

        @JacksonXmlProperty(localName = "output")
        @JacksonXmlElementWrapper(localName = "outputs")
        List<OutputPort> getOutputs();

        ExecutionStatistics getExecutionStatistics();

        JobManager getJobManager();

        LinkInfo getLinkInfo();

        @JacksonXmlProperty(localName = "flowvariable")
        @JacksonXmlElementWrapper(localName = "flowvariables")
        List<FlowVariable> getFlowVariables();

        static Node create(final NodeContainer nc, final WorkflowSummaryConfiguration config) {
            return new Node() {

                @Override
                public String getId() {
                    //remove project wfm id
                    WorkflowManager projectWFM = nc.getParent().getProjectWFM();
                    return NodeIDSuffix.create(projectWFM.getID(), nc.getID()).toString();
                }

                @Override
                public String getName() {
                    return nc.getName();
                }

                @Override
                public String getType() {
                    return nc.getType().toString();
                }

                @Override
                public NodeFactoryKey getFactoryKey() {
                    if (nc instanceof NativeNodeContainer) {
                        return NodeFactoryKey.create(((NativeNodeContainer)nc).getNode().getFactory());
                    }
                    return null;
                }

                @Override
                public Boolean isMetanode() {
                    if (nc instanceof WorkflowManager) {
                        return true;
                    }
                    return null;
                }

                @Override
                public Boolean isComponent() {
                    if (nc instanceof SubNodeContainer) {
                        return true;
                    }
                    return null;
                }

                @Override
                public String getState() {
                    return nc.getNodeContainerState().toString();
                }

                @Override
                public Integer getGraphDepth() {
                    return WorkflowSummaryGenerator.getGraphDepth(nc);
                }

                @Override
                public List<Setting> getSettings() {
                    if (nc instanceof SingleNodeContainer) {
                        SingleNodeContainer snc = (SingleNodeContainer)nc;
                        if (nc.getNodeContainerState().isExecuted()) {
                            try {
                                return Setting.create(snc.getModelSettingsUsingFlowObjectStack(), config);
                            } catch (InvalidSettingsException ex) {
                                throw new IllegalStateException(
                                    "Problem extracting settings of node '" + snc.getNameWithID() + "'", ex);
                            }
                        } else {
                            NodeSettings nodeSettings = snc.getNodeSettings();
                            if (nodeSettings.containsKey("model")) {
                                try {
                                    return Setting.create(nodeSettings.getConfig("model"), config);
                                } catch (InvalidSettingsException ex) {
                                    //can never happen - checked before
                                }
                            }
                        }
                    }
                    return null;
                }

                @Override
                public Workflow getSubWorkflow() {
                    if (nc instanceof WorkflowManager) {
                        return Workflow.create((WorkflowManager)nc, config);
                    } else if (nc instanceof SubNodeContainer) {
                        return Workflow.create(((SubNodeContainer)nc).getWorkflowManager(), config);
                    } else {
                        return null;
                    }
                }

                @Override
                public List<OutputPort> getOutputs() {
                    return IntStream.range(0, nc.getNrOutPorts()).mapToObj(i -> {
                        return OutputPort.create(i, nc.getOutPort(i), config);
                    }).collect(toList());
                }

                @Override
                public String getAnnotation() {
                    if (!nc.getNodeAnnotation().getData().isDefault()) {
                        return nc.getNodeAnnotation().getText();
                    }
                    return null;
                }

                @Override
                public ExecutionStatistics getExecutionStatistics() {
                    if (config.m_includeExecutionInfo && nc instanceof NativeNodeContainer) {
                        return ExecutionStatistics.create(nc.getNodeTimer());
                    }
                    return null;
                }

                @Override
                public JobManager getJobManager() {
                    return JobManager.create(nc.getJobManager(), config);
                }

                @Override
                public NodeMessage getNodeMessage() {
                    return NodeMessage.create(nc.getNodeMessage());
                }

                @Override
                public Boolean isDeprecated() {
                    if (nc instanceof NativeNodeContainer
                        && ((NativeNodeContainer)nc).getNode().getFactory().isDeprecated()) {
                        return true;
                    } else {
                        return null;
                    }
                }

                @Override
                public String getParentId() {
                    if (nc.getParent().isProject()) {
                        return null;
                    } else {
                        //remove project wfm id
                        WorkflowManager projectWFM = nc.getParent().getProjectWFM();
                        return NodeIDSuffix.create(projectWFM.getID(), nc.getParent().getID()).toString();
                    }
                }

                @Override
                public LinkInfo getLinkInfo() {
                    if (nc instanceof NodeContainerTemplate) {
                        return LinkInfo.create(((NodeContainerTemplate)nc).getTemplateInformation());
                    } else {
                        return null;
                    }
                }

                @Override
                public List<FlowVariable> getFlowVariables() {
                    if (nc instanceof SingleNodeContainer) {
                        // the outgoing stack of a SingleNodeContainer only contains variables owned by that node
                        FlowObjectStack fos = ((SingleNodeContainer)nc).getOutgoingFlowObjectStack();
                        if (fos != null) {
                            List<FlowVariable> vars = fos.getAllAvailableFlowVariables().values().stream()//
                                .filter(f -> f.getScope() == Scope.Flow)//
                                .map(FlowVariable::create).collect(Collectors.toList());
                            if (!vars.isEmpty()) {
                                return vars;
                            }
                        }
                    }
                    return null;
                }

            };
        }
    }

    @JsonPropertyOrder({"className", "settings"})
    private interface NodeFactoryKey {

        @JacksonXmlProperty(isAttribute = true)
        String getClassName();

        String getSettings();

        static NodeFactoryKey create(final NodeFactory<NodeModel> factory) {
            return new NodeFactoryKey() {

                @Override
                public String getSettings() {
                    if (factory instanceof DynamicNodeFactory) {
                        NodeSettings settings = new NodeSettings("settings");
                        factory.saveAdditionalFactorySettings(settings);
                        return JSONConfig.toJSONString(settings, WriterConfig.DEFAULT);

                    }
                    return null;
                }

                @Override
                public String getClassName() {
                    return factory.getClass().getCanonicalName();
                }
            };

        }

    }

    @JsonPropertyOrder({"type", "message"})
    private interface NodeMessage {
        @JacksonXmlProperty(isAttribute = true)
        String getType();

        String getMessage();

        static NodeMessage create(final org.knime.core.node.workflow.NodeMessage msg) {
            if (msg == org.knime.core.node.workflow.NodeMessage.NONE) {
                return null;
            }
            return new NodeMessage() {

                @Override
                public String getType() {
                    return msg.getMessageType().toString();
                }

                @Override
                public String getMessage() {
                    return msg.getMessage();
                }

            };
        }
    }

    private interface ExecutionStatistics {

        @JacksonXmlProperty(isAttribute = true)
        String getLastExecutionStartTime();

        @JacksonXmlProperty(isAttribute = true)
        long getLastExecutionDuration();

        @JacksonXmlProperty(isAttribute = true)
        long getExecutionDurationSinceReset();

        @JacksonXmlProperty(isAttribute = true)
        long getExecutionDurationSinceStart();

        @JacksonXmlProperty(isAttribute = true)
        int getExecutionCountSinceReset();

        @JacksonXmlProperty(isAttribute = true)
        int getExecutionCountSinceStart();

        static ExecutionStatistics create(final NodeTimer nt) {
            if (nt.getStartTime() < 0) {
                return null;
            } else {
                return new ExecutionStatistics() {

                    @Override
                    public String getLastExecutionStartTime() {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
                        return formatter.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(nt.getStartTime()),
                            Clock.systemDefaultZone().getZone()));
                    }

                    @Override
                    public long getLastExecutionDuration() {
                        return nt.getLastExecutionDuration();
                    }

                    @Override
                    public long getExecutionDurationSinceReset() {
                        return nt.getExecutionDurationSinceReset();
                    }

                    @Override
                    public long getExecutionDurationSinceStart() {
                        return nt.getExecutionDurationSinceStart();
                    }

                    @Override
                    public int getExecutionCountSinceReset() {
                        return nt.getNrExecsSinceReset();
                    }

                    @Override
                    public int getExecutionCountSinceStart() {
                        return nt.getNrExecsSinceStart();
                    }

                };
            }
        }

    }

    @JsonPropertyOrder({"name", "id", "settings"})
    private interface JobManager {

        @JacksonXmlProperty(isAttribute = true)
        String getId();

        @JacksonXmlProperty(isAttribute = true)
        String getName();

        @JacksonXmlProperty(localName = "setting")
        @JacksonXmlElementWrapper(localName = "settings")
        List<Setting> getSettings();

        static JobManager create(final NodeExecutionJobManager mgr, final WorkflowSummaryConfiguration config) {
            if (mgr == null) {
                return null;
            }
            return new JobManager() {

                @Override
                public String getId() {
                    return mgr.getID();
                }

                @Override
                public String getName() {
                    return NodeExecutionJobManagerPool.getJobManagerFactory(mgr.getID()).getLabel();
                }

                @Override
                public List<Setting> getSettings() {
                    NodeSettings ns = new NodeSettings("job_manager_settings");
                    mgr.save(ns);
                    return Setting.create(ns, config);
                }

            };
        }
    }

    @JsonPropertyOrder({"author", "creationDate", "description", "lastUploaded", "lastEdited"})
    private interface WorkflowMetadata {

        @JacksonXmlProperty(isAttribute = true)
        String getAuthor();

        @JacksonXmlProperty(isAttribute = true)
        String getCreationDate();

        @JacksonXmlProperty(isAttribute = true)
        String getLastUploaded();

        @JacksonXmlProperty(isAttribute = true)
        String getLastEdited();

        String getDescription();

        static WorkflowMetadata create(final WorkflowManager wfm) {
            if (!wfm.isProject()) {
                return null;
            }
            final ReferencedFile rf = wfm.getProjectWFM().getWorkingDir();
            File metadataFile = new File(rf.getFile(), WorkflowPersistor.METAINFO_FILE);
            if (!metadataFile.exists()) {
                return null;
            }
            String[] meta = new String[5];
            try {
                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                docFactory.setNamespaceAware(true);
                Document doc =docFactory.newDocumentBuilder().parse(metadataFile);
                doc.normalize();
                org.w3c.dom.Node root = doc.getChildNodes().item(0);
                //once a metadata version is present, we need to parse it differently here!
                //this should remind us
                assert root.getAttributes().getNamedItem(
                    MetadataXML.METADATA_VERSION) == null : "Implementation problem: metadata version not supported, yet";
                String prefix = root.getPrefix() == null ? "" : root.getPrefix();
                NodeList elements = doc.getElementsByTagName(prefix + ":" + MetadataXML.ATOM_ELEMENT);
                for (int i = 0; i < elements.getLength(); i++) {
                    org.w3c.dom.Node item = elements.item(i);
                    String name = item.getAttributes().getNamedItem(MetadataXML.NAME).getTextContent();
                    if (MetadataXML.AUTHOR_LABEL.equals(name)) {
                        meta[0] = item.getTextContent();
                    } else if (MetadataXML.CREATION_DATE_LABEL.equals(name)) {
                        meta[1] = item.getTextContent();
                    } else if (MetadataXML.DESCRIPTION_LABEL.equals(name)) {
                        meta[2] = item.getTextContent();
                    } else if ("Last Uploaded".equals(name)) {
                        meta[3] = item.getTextContent();
                    } else if ("Last Edited".equals(name)) {
                        meta[4] = item.getTextContent();
                    }
                }
            } catch (SAXException | IOException | ParserConfigurationException e) {
                throw new IllegalStateException(
                    "Something went wrong while extracting the workflow metadata from '" + metadataFile + "'", e);
            }
            return new WorkflowMetadata() {

                @Override
                public String getAuthor() {
                    return meta[0];
                }

                @Override
                public String getCreationDate() {
                    Calendar cal = MetaInfoFile.calendarFromDateString(meta[1]);
                    if (cal != null) {
                        //Only the date is provided here.
                        //The time is meaningless because it is attached to the date as constant value,
                        //see org.knime.workbench.descriptionview.metadata.atoms.DateMetaInfoAtom.TIME_OF_DAY_SUFFIX
                        SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
                        return formatter.format(cal.getTime());
                    }
                    return null;
                }

                @Override
                public String getDescription() {
                    return meta[2];
                }

                @Override
                public String getLastUploaded() {
                    return meta[3];
                }

                @Override
                public String getLastEdited() {
                    return meta[4];
                }

            };
        }
    }

    @JsonPropertyOrder({"index", "type", "inactive", "tableSpec", "dataSummary", "successors"})
    private interface OutputPort {

        @JacksonXmlProperty(isAttribute = true)
        int getIndex();

        @JacksonXmlProperty(isAttribute = true)
        String getType();

        @JacksonXmlProperty(isAttribute = true)
        Boolean isInactive();

        TableSpec getTableSpec();

        String getDataSummary();

        @JacksonXmlProperty(localName = "successor")
        @JacksonXmlElementWrapper(localName = "successors")
        List<Successor> getSuccessors();

        static OutputPort create(final int index, final NodeOutPort p, final WorkflowSummaryConfiguration config) {
            return new OutputPort() {

                @Override
                public String getType() {
                    if (p.getPortType().equals(BufferedDataTable.TYPE)) {
                        return "table";
                    } else if (p.getPortType().equals(FlowVariablePortObject.TYPE)) {
                        return "flowvariable port";
                    }
                    return p.getPortType().toString();
                }

                @Override
                public TableSpec getTableSpec() {
                    if (p.getPortObjectSpec() instanceof DataTableSpec) {
                        return TableSpec.create((DataTableSpec)p.getPortObjectSpec());
                    } else {
                        return null;
                    }
                }

                @Override
                public int getIndex() {
                    return index;
                }

                @Override
                public String getDataSummary() {
                    if (config.m_includeExecutionInfo && p.getPortObject() != null) {
                        return p.getPortObject().getSummary();
                    }
                    return null;
                }

                @Override
                public List<Successor> getSuccessors() {
                    SingleNodeContainer nc = p.getConnectedNodeContainer();
                    if (nc != null) {
                        List<Successor> res = nc.getParent().getOutgoingConnectionsFor(nc.getID(), index).stream()
                            .filter(
                                cc -> config.m_nodesToIgnore == null || !config.m_nodesToIgnore.contains(cc.getDest()))
                            .map(cc -> Successor.create(cc, nc.getParent().getProjectWFM())).sorted() //for deterministic results for testing
                            .collect(toList());
                        return res.isEmpty() ? null : res;
                    }
                    return null;
                }

                @Override
                public Boolean isInactive() {
                    if (p.getPortObjectSpec() instanceof InactiveBranchPortObjectSpec) {
                        return true;
                    }
                    return null;
                }
            };
        }

    }

    @JsonPropertyOrder({"id", "index"})
    private interface Successor extends Comparable<Successor> {

        @JacksonXmlProperty(isAttribute = true)
        String getId();

        @JacksonXmlProperty(isAttribute = true)
        int getPortIndex();

        static Successor create(final ConnectionContainer cc, final WorkflowManager projectWfm) {
            final String id = NodeIDSuffix.create(projectWfm.getID(), cc.getDest()).toString();
            return new Successor() {

                @Override
                public String getId() {
                    return id;
                }

                @Override
                public int getPortIndex() {
                    return cc.getDestPort();
                }

                @Override
                public int compareTo(final Successor o) {
                    //required to get deterministic results for testing
                    return getId().compareTo(o.getId()) * 10 + getPortIndex() - o.getPortIndex();
                }

            };
        }
    }

    private interface TableSpec {
        @JacksonXmlProperty(localName = "column")
        @JacksonXmlElementWrapper(localName = "columns")
        List<Column> getColumns();

        static TableSpec create(final DataTableSpec spec) {
            return new TableSpec() {

                @Override
                public List<Column> getColumns() {
                    return IntStream.range(0, spec.getNumColumns()).mapToObj(i -> {
                        return Column.create(i, spec.getColumnSpec(i));
                    }).collect(toList());
                }
            };
        }
    }

    @JsonPropertyOrder({"name", "type", "index", "columnDomain"})
    private interface Column {
        @JacksonXmlProperty(isAttribute = true)
        String getName();

        @JacksonXmlProperty(isAttribute = true)
        String getType();

        @JacksonXmlProperty(isAttribute = true)
        int getIndex();

        ColumnDomain getColumnDomain();

        @JacksonXmlProperty(localName = "columnproperty")
        @JacksonXmlElementWrapper(localName = "columnproperties")
        List<ColumnProperty> getColumnProperties();

        static Column create(final int index, final DataColumnSpec colSpec) {
            return new Column() {

                @Override
                public String getName() {
                    return colSpec.getName();
                }

                @Override
                public int getIndex() {
                    return index;
                }

                @Override
                public List<ColumnProperty> getColumnProperties() {
                    return ColumnProperty.create(colSpec.getProperties());
                }

                @Override
                public ColumnDomain getColumnDomain() {
                    return ColumnDomain.create(colSpec.getDomain());
                }

                @Override
                public String getType() {
                    return colSpec.getType().toString();
                }
            };
        }

    }

    @JsonPropertyOrder({"values", "lowerBound", "upperBound"})
    private interface ColumnDomain {

        @JacksonXmlProperty(localName = "value")
        @JacksonXmlElementWrapper(localName = "values")
        List<String> getValues();

        String getLowerBound();

        String getUpperBound();

        static ColumnDomain create(final DataColumnDomain domain) {
            return new ColumnDomain() {

                @Override
                public List<String> getValues() {
                    if (domain.hasValues()) {
                        return domain.getValues().stream().map(DataCell::toString).collect(toList());
                    }
                    return null;
                }

                @Override
                public String getUpperBound() {
                    if (domain.hasUpperBound()) {
                        return domain.getUpperBound().toString();
                    }
                    return null;
                }

                @Override
                public String getLowerBound() {
                    if (domain.hasLowerBound()) {
                        return domain.getLowerBound().toString();
                    }
                    return null;
                }
            };
        }

    }

    @JsonPropertyOrder({"key", "value"})
    private interface ColumnProperty {

        @JacksonXmlProperty(isAttribute = true)
        String getKey();

        String getValue();

        static List<ColumnProperty> create(final DataColumnProperties props) {
            Enumeration<String> enumeration = props.properties();
            List<ColumnProperty> res = new ArrayList<>();
            while (enumeration.hasMoreElements()) {
                String key = enumeration.nextElement();
                res.add(new ColumnProperty() {

                    @Override
                    public String getKey() {
                        return key;
                    }

                    @Override
                    public String getValue() {
                        return props.getProperty(key);
                    }

                });
            }
            return res;
        }
    }

    @JsonPropertyOrder({"name", "type", "value"})
    private interface FlowVariable {

        @JacksonXmlProperty(isAttribute = true)
        String getName();

        @JacksonXmlProperty(isAttribute = true)
        String getType();

        String getValue();

        static FlowVariable create(final org.knime.core.node.workflow.FlowVariable var) {
            return new FlowVariable() {

                @Override
                public String getName() {
                    return var.getName();
                }

                @Override
                public String getType() {
                    return var.getVariableType().getIdentifier();
                }

                @Override
                public String getValue() {
                    return var.getValueAsString();
                }

            };
        }

    }

    @JsonPropertyOrder({"key", "value", "type", "settings"})
    private interface Setting {

        @JacksonXmlProperty(isAttribute = true)
        String getKey();

        @JacksonXmlProperty(isAttribute = true)
        String getValue();

        @JacksonXmlProperty(isAttribute = true)
        String getType();

        @JacksonXmlProperty(localName = "setting")
        @JacksonXmlElementWrapper(localName = "settings")
        List<Setting> getSettings();

        static List<Setting> create(final Config config, final WorkflowSummaryConfiguration summaryConfig) {
            Iterator<String> iterator = config.iterator();
            List<Setting> res = new ArrayList<>();
            while (iterator.hasNext()) {
                String key = iterator.next();
                AbstractConfigEntry entry = config.getEntry(key);
                res.add(new Setting() {

                    @Override
                    public String getKey() {
                        return key;
                    }

                    @Override
                    public String getValue() {
                        if (entry.getType() != ConfigEntries.config) {
                            String s = entry.toStringValue();
                            if (s != null) {
                                return summaryConfig.m_textEncoder == null ? s : summaryConfig.m_textEncoder.apply(s);
                            }
                            return null;
                        } else {
                            return null;
                        }
                    }

                    @Override
                    public String getType() {
                        return entry.getType().toString();
                    }

                    @Override
                    public List<Setting> getSettings() {
                        if (entry.getType() == ConfigEntries.config) {
                            return Setting.create((Config)entry, summaryConfig);
                        } else {
                            return null;
                        }
                    }

                });
            }
            return res;
        }
    }

    @JsonPropertyOrder({"sourceURI", "timeStamp", "updateStatus"})
    private interface LinkInfo {

        @JacksonXmlProperty(isAttribute = true)
        String getSourceURI();

        @JacksonXmlProperty(isAttribute = true)
        String getTimeStamp();

        @JacksonXmlProperty(isAttribute = true)
        String getUpdateStatus();

        static LinkInfo create(final MetaNodeTemplateInformation info) {
            info.getSourceURI();
            if (info.getRole() != Role.None) {
                return new LinkInfo() {

                    @Override
                    public String getSourceURI() {
                        return info.getSourceURI() != null ? info.getSourceURI().toString() : null;
                    }

                    @Override
                    public String getTimeStamp() {
                        return info.getTimeStampString();
                    }

                    @Override
                    public String getUpdateStatus() {
                        return info.getUpdateStatus() != null ? info.getUpdateStatus().name() : null;
                    }
                };
            } else {
                return null;
            }
        }
    }

}
