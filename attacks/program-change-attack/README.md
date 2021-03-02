# Program Change Attack

## Overview

The Program Change Attack causes a pre-written malicious program to run on the target switch in place of the program intended by the controller. 

In this case, given that the attack goal is to allow 'attack' packets to bypass the P4Knocking firewall, the malicious program includes the table shown below. This table simply sets the P4Knocking stage variable (that indicates how many consecutive correct knock packets have been received from the source IP address) to 3 for any IP addresses inserted into the table. This allows packets from these IP addresses to pass through the firewall.

```p4
table hack_table {
    key = {
        hdr.ipv4.src_addr : lpm;
    }
    actions = {
        pass_through;
        NoAction;
    }
    default_action = NoAction;
    }
    
action pass_through() {
    local_metadata.stage = 3;
}    
```

**Note**: All data structures present in the original program were maintained in the malicious program, as this avoids the need to falsify responses to read requests from the control plane for P4 entities that ought to be present.

## Attack Flow

Two versions of this attack are presented: one in which the attack code is inserted when the control plane is not expecting a program change ([program-attack-attacker-change.cpp](program-attack-attacker-change.cpp)) and another when the controller is pushing the legitimate program to the switch ([program-attack-controller-change.cpp](program-attack-controller-change.cpp)). The purpose of this is to demonstrate how the first attack fails as the switch is unexpectedly brought offline, while the second attack succeeds as the controller is already expecting this disruption to service. The diagram below depicts the elements of the successful variant of the attack.

<p align="center">
  <img width="800" height="955" src="/attacks/program-change-attack.png">
</p>

The control flow for each attack is outlined below.

### Controller-Initiated Change ([program-attack-controller-change.cpp](program-attack-controller-change.cpp))

1. The `mininet` Docker container is run with the `LD_PRELOAD` environment variable set to `program-attack-controller-change.so`.
1. Once the network is started, the controller pushes a new pipeline configuration (consisting of a P4Info file and BMv2 JSON file) to the switch, which describes the legitimate P4Knocking program using a `SetPipelineConfigRequest` RPC.
1. This RPC is received by the P4Runtime Server on the switch, which stores a copy of the P4Info and BMv2 JSON files locally, before calling the `_pi_update_device_start` function from the `libbmpi.so` shared library that implements the SDK functionality for the BMv2 software switch. It is these locally stored files that are returned to the control plane when a `GetPipelineConfigRequest` RPC is issued, allowing the attack programs to remain hidden.
1. However, since the `program-attack-controller-change.so` shared library is preloaded, the P4Runtime Server executable (`stratum_bmv2`) first searches for the `_pi_update_device_start` function in this shared library, and inadvertently calls our attack function with the same name instead.
1. Our attack `_pi_update_device_start` function simply ignores the P4Info and BMv2 JSON files passed to it and instead calls the legitimate `_pi_update_device_start` from `libbmpi.so` with new BMv2 JSON and P4Info arguments supplied within the attack function, causing the software switch to run this program instead.
1. Our attack code also intercepts calls to the `mt_add_entry` function, one of the functions required to install a new table entry in the switch. When our attack function is called, it first calls the legitimate `mt_add_entry` function and stores its return value locally.
1. Then, a new table entry is constructed to be installed in the `hack_table` that is present in our malicious P4 program. This table entry matches the IP address `192.168.1.122` and invokes the action `pass_through`, which causes packets from the given source IP address to be forwarded regardless of their adherence to the stateful firewall rules. This table entry is inserted using a second call to the legitimate `mt_add_entry` function.
1. Finally, the original function return value is returned from the attack shared library function and our attack packets can now pass through the switch.

### Attacker-Initiated Change ([program-attack-attacker-change.cpp](program-attack-attacker-change.cpp))

This attack is identical to the controller-initiated attack, apart from the fact that the `_pi_update_device_start` function is not intercepted. Instead, the `get_entries` function is intercepted and the `_pi_update_device_start` function called from within it. This means that the attack doesn't have to wait for a `SetPipelineConfigRequest` RPC to install the attack program on the switch. However, launching the attack in this way causes the switch to fail, as the program change causes it to be brought offline unexpectedly and the controller continues to issue RPCs for the switch to process.

**Note**: The code to insert the table entry is omitted here as switch failure renders it redundant.

## Running the Attack Code

Full guidance on how to set up the test environment and run the attack code can be found in [INSTALL](/INSTALL.md).
