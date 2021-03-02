

package org.onosproject.ngsdn.tutorial;

import org.onlab.packet.MacAddress;
import org.onlab.packet.Ethernet;
import org.onlab.packet.TCP;
import org.onlab.packet.IPv4;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.core.ApplicationId;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.ngsdn.tutorial.common.FabricDeviceConfig;
import org.onosproject.ngsdn.tutorial.common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;
import java.io.*;
import java.util.*;


import static org.onosproject.ngsdn.tutorial.AppConstants.INITIAL_SETUP_DELAY;

/**
 * App component that implements the P4Knocking ID allocation function.
 */
@Component(
        immediate = true,
        // *** TODO EXERCISE 4
        // Enable component (enabled = true)
        enabled = true
)
public class P4KnockingComponent {

    private Stack<Integer> stack = new Stack<Integer>();

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final int DEFAULT_BROADCAST_GROUP_ID = 255;

    private ApplicationId appId;

    //--------------------------------------------------------------------------
    // ONOS CORE SERVICE BINDING
    //
    // These variables are set by the Karaf runtime environment before calling
    // the activate() method.
    //--------------------------------------------------------------------------

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private NetworkConfigService configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MainComponent mainComponent;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    private final PacketProcessor packetProcessor = new PKPacketProcessor();
    private final TrafficSelector intercept = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4).build();

    //--------------------------------------------------------------------------
    // COMPONENT ACTIVATION.
    //
    // When loading/unloading the app the Karaf runtime environment will call
    // activate()/deactivate().
    //--------------------------------------------------------------------------

    private void initialiseStack() {
        int j = 2<<16;
        for(int i = j-1; i > 0; i--) {
            stack.push(i);
        }
    }

    @Activate
    protected void activate() {
        appId = mainComponent.getAppId();

        packetService.addProcessor(packetProcessor, 1); //PacketPriority.valueOf("REACTIVE"));
        packetService.requestPackets(intercept, PacketPriority.valueOf("CONTROL"), appId,
                Optional.empty());
        initialiseStack();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {

        packetService.removeProcessor(packetProcessor);

        log.info("Stopped");
    }

    private boolean isPortKnocking(Ethernet eth) {
        if(eth.getEtherType() == Ethernet.TYPE_IPV4) {
            IPv4 IPv4Packet = (IPv4) eth.getPayload();
            TCP TCPSegment = (TCP) IPv4Packet.getPayload();
            if(TCPSegment.getChecksum()==46) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }

    }

    private void processPacket(PacketContext context, Ethernet eth) {
       // log.info("In processPacketMethod");
        int id = stack.pop();
        IPv4 IPv4Packet = (IPv4) eth.getPayload();
        int srcAddr = IPv4Packet.getSourceAddress();
        setIdTableEntry(context, srcAddr, id);
    }

    private void setIdTableEntry(PacketContext context, int srcAddr, int id) {
        PiCriterion match = PiCriterion.builder().matchExact(PiMatchFieldId.of("hdr.ipv4.src_addr"), srcAddr).build();
        PiAction action = PiAction.builder().withId(PiActionId.of("IngressPipeImpl.id_found")).withParameter(new PiActionParam(PiActionParamId.of("current_id"), id))
                .build();
        FlowRule flowRule = Utils.buildFlowRule(context.inPacket().receivedFrom().deviceId(), appId, "IngressPipeImpl.ip_2_id_tb", match, action);
        flowRuleService.applyFlowRules(flowRule);
        //log.info("flow rule added");
    }

    private class PKPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            Ethernet eth = context.inPacket().parsed();
           // log.info("In process method");
            if(isPortKnocking(eth)) {
                processPacket(context,eth);
            }
        }
    }

}