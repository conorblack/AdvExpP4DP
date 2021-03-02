# Table Entry Attack

## Overview

The Table Entry Attack modifies the table entries of a legitimate running program to indirectly cause 'attack' packets to bypass the stateful P4Knocking firewall implemented by this program. 

This attack works by taking advantage of the control plane offloading employed by this implementation of P4Knocking. In order to reduce the consumption of register memory, the P4Knocking implementation uses an optimisation whereby 16-bit controller-assigned ID values are used instead of 32-bit IP addresses to index the P4Knocking register used in the program. This register stores the number of correct 'knock' packets that have been received from each source IP address, with 3 consecutive correct knocks granting firewall bypass. The assignment of ID values works as follows:

1. A data plane switch receives a TCP packet with a source IP address that has not been seen before by the switch.
1. A `packet-in` header is added to the packet and it is sent to the control plane.
1. The control plane receives the packet and assigns a unique ID value to this source IP address.
1. The controller then installs a table entry in the `ip_2_id` table on the switch that sent the `packet-in` message, which contains the source IP address and the matching ID, allowing the data plane program to use this ID to index the P4Knocking register.

Our attack takes advantage of this implementation by intercepting the ID assignment process for our attack IP address and assigning an ID to it that has already been assigned to an IP address that we know has previously passed the stateful firewall checks. In that way, our IP address gains the same access rights as the legitimate IP address, without having to correctly follow the firewall rules.

Without additional modifications, this attack is easily detected by the control plane if it is checking table entries for correctness, as our attack IP address will have an incorrect ID assigned to it. This version of our attack is included in [table-attack-without-spoofing.cpp](attacks/table-entry-attack/table-attack-without-spoofing.cpp) and, when run, illustrates how quickly the control plane realises this discrepancy and deletes the offending forwarding rules. However, we also implement a second version of the attack [table-attack-with-spoofing.cpp](attacks/table-entry-attack/table-attack-with-spoofing.cpp), where the control plane's read requests are intercepted, and the offending table entry is replaced with the entry originally installed by the controller. This successfully fools the controller into belieiving that everything is running as intended. The workflow of this attack variant is depicted in the figure below.

<p align="center">
  <img width="700" height="950" src="/attacks/table-entry-attack.png">
</p>

## Attack Flow

### Attack With Spoofing ([table-attack-with-spoofing.cpp](attacks/table-entry-attack/table-attack-with-spoofing.cpp))

1. The `mininet` Docker container is run with the `LD_PRELOAD` environment variable set to `table-attack-with-spoofing.so`.
1. Before any attack packets are sent, a legitimate IP address sends a TCP packet through the switch `leaf1`. Once this packet is received, the switch sends it to the controller as a `packet-in`. The controller responds by assigning that IP address an ID in the `ip_2_id` table using a `TableEntry` P4Runtime message.
1. Once this `TableEntry` message is received at the switch, `stratum_bmv2` installs it by calling the `add_entry` function from the shared library `libsimpleswitch_runner.so`, which handles the installation of table entries in the BMv2 software switch.
1. However, since the `table-attack-with-spoofing.so` shared library is preloaded, `stratum_bmv2` first searches for the `add_entry` function in this shared library, and inadvertently calls our attack function with the same name.
1. Our preloaded function then checks to see if the source IP address is equal to the 'attack' IP address. Since it is not, the function simply stores the ID value assigned to the legitimate IP address, and calls the original `add_entry` function without modification.
1. The legitimate IP address then completes the P4Knocking knock sequence and is granted full access through the stateful firewall.
1. Later, the attack IP address sends a packet to the same switch and is granted a unique ID value in the same manner as the legitimate IP address. Again, once `stratum_bmv2` calls the `add_entry` function, it indavertently calls our attack function in `table-attack-with-spoofing.so`.
1. This time, however, since the IP address is that of the attacker, our attack code stores its controller-assigned ID and retrieves the ID assigned to the legitimate IP address that it had previously stored. This first ID is then used as the argument when calling the legitimate `add_entry` function, causing the attack IP address to be assigned the same ID as the earlier legitimate IP address. As a result, the attacker gains the same firewall access as the first IP address and can send packets across the switch without having to follow the knock sequence.
1. At some time in the future, the controller then issues a another `TableEntry` message, which is intended to read the current table entries. To accomplish this, `stratum_bmv2` calls the `get_entries` function, again from `libsimpleswitch_runner.so`.
1. However, `get_entries` is also intercepted by `table-attack-with-spoofing.so`. Our attack version of the code initially calls the legitimate `get_entries` function and receives the list of table entries on the switch as the return value.
1. Then, the preloaded `get_entries` function cycles through these table entries, searching by `action name` and `match key` until our modified table entry is found.
1. Our attack function replaces the modified ID with the original ID assigned by the controller in the table entry, then returns the full list of table entries. In this way, the controller never sees our altered table entry and believes that the state of the table is as it intended.


### Attack Without Spooofing ([table-attack-without-spoofing.cpp](attacks/table-entry-attack/table-attack-without-spoofing.cpp))

This variant of the attack is identical to the attack with spoofing, save for the exclusion of the interception of the `get_entries` function. As a result, the controller can see that the table entries on the switch are not consistent with those that it installed, and immediately deletes our manipulated entry.

## Running the Attack Code

Full guidance on how to set up the test environment and run the attack code can be found in [INSTALL](/INSTALL.md).
