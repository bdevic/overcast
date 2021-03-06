/* License added by: GRADLE-LICENSE-PLUGIN
 *
 * Copyright 2008-2012 XebiaLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xebialabs.overcast.host;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xebialabs.overcast.OvercastProperties;
import com.xebialabs.overcast.command.Command;
import com.xebialabs.overcast.command.CommandProcessor;
import com.xebialabs.overcast.support.vagrant.VagrantDriver;
import com.xebialabs.overcast.support.virtualbox.VirtualboxDriver;
import com.xebialabs.overthere.ConnectionOptions;
import com.xebialabs.overthere.OperatingSystemFamily;
import com.xebialabs.overthere.cifs.CifsConnectionBuilder;
import com.xebialabs.overthere.cifs.CifsConnectionType;
import com.xebialabs.overthere.spi.OverthereConnectionBuilder;
import com.xebialabs.overthere.ssh.SshConnectionBuilder;
import com.xebialabs.overthere.ssh.SshConnectionType;
import com.xebialabs.overthere.util.DefaultAddressPortMapper;

import static com.xebialabs.overcast.OvercastProperties.getOvercastProperty;
import static com.xebialabs.overcast.OvercastProperties.getRequiredOvercastProperty;
import static com.xebialabs.overcast.OvercastProperties.parsePortsProperty;
import static com.xebialabs.overcast.command.CommandProcessor.atLocation;

public class CloudHostFactory {

    public static final String HOSTNAME_PROPERTY_SUFFIX = ".hostname";

    public static final String TUNNEL_USERNAME_PROPERTY_SUFFIX = ".tunnel.username";
    public static final String TUNNEL_PASSWORD_PROPERTY_SUFFIX = ".tunnel" + OvercastProperties.PASSWORD_PROPERTY_SUFFIX;
    public static final String TUNNEL_PORTS_PROPERTY_SUFFIX = ".tunnel.ports";

    private static final String VAGRANT_DIR_PROPERTY_SUFFIX = ".vagrantDir";
    private static final String VAGRANT_VM_PROPERTY_SUFFIX = ".vagrantVm";
    private static final String VAGRANT_IP_PROPERTY_SUFFIX = ".vagrantIp";
    private static final String VAGRANT_SNAPSHOT_EXPIRATION_CMD = ".vagrantSnapshotExpirationCmd";
    private static final String VAGRANT_OS_PROPERTY_SUFFIX = ".vagrantOs";

    private static final String VBOX_UUID_PROPERTY_SUFFIX = ".vboxUuid";
    private static final String VBOX_IP = ".vboxBoxIp";
    private static final String VBOX_SNAPSHOT = ".vboxSnapshotUuid";

    public static Logger logger = LoggerFactory.getLogger(CloudHostFactory.class);

    public static CloudHost getCloudHostWithNoTeardown(String hostLabel) {
        return getCloudHost(hostLabel, true);
    }

    public static CloudHost getCloudHost(String hostLabel) {
        return getCloudHost(hostLabel, false);
    }

    private static CloudHost getCloudHost(String hostLabel, boolean disableEc2) {
        CloudHost host = createCloudHost(hostLabel, disableEc2);
        return wrapCloudHost(hostLabel, host);
    }

    protected static CloudHost createCloudHost(String label, boolean disableEc2) {
        String hostName = getOvercastProperty(label + HOSTNAME_PROPERTY_SUFFIX);
        if (hostName != null) {
            return createExistingCloudHost(label);
        }

        String vagrantDir = getOvercastProperty(label + VAGRANT_DIR_PROPERTY_SUFFIX);
        if (vagrantDir != null) {
            return createVagrantCloudHost(label, vagrantDir);
        }

        String amiId = getOvercastProperty(label + Ec2CloudHost.AMI_ID_PROPERTY_SUFFIX);
        if (amiId != null) {
            return createEc2CloudHost(label, amiId, disableEc2);
        }

        String vboxUuid = getOvercastProperty(label + VBOX_UUID_PROPERTY_SUFFIX);
        if (vboxUuid != null) {
            return createVboxHost(label, vboxUuid);
        }

        String kvmBaseDomain = getOvercastProperty(label + LibvirtHost.LIBVIRT_BASE_DOMAIN_PROPERTY_SUFFIX);
        if (kvmBaseDomain != null) {
            return createLibvirtHost(label, kvmBaseDomain);
        }

        throw new IllegalStateException("No valid configuration has been specified for host label " + label);
    }

    private static CloudHost createVboxHost(final String label, final String vboxUuid) {
        String vboxIp = getOvercastProperty(label + VBOX_IP);
        String vboxSnapshot = getOvercastProperty(label + VBOX_SNAPSHOT);
        return new VirtualboxHost(vboxIp, vboxUuid, vboxSnapshot);
    }

    private static CloudHost createExistingCloudHost(final String label) {
        logger.info("Using existing host for {}", label);
        return new ExistingCloudHost(label);
    }

    private static CloudHost createVagrantCloudHost(final String hostLabel, final String vagrantDir) {
        String vagrantVm = getOvercastProperty(hostLabel + VAGRANT_VM_PROPERTY_SUFFIX);
        String vagrantIp = getOvercastProperty(hostLabel + VAGRANT_IP_PROPERTY_SUFFIX);
        String vagrantExpirationCmd = getOvercastProperty(hostLabel + VAGRANT_SNAPSHOT_EXPIRATION_CMD);
        String vagrantOs = getOvercastProperty(hostLabel + VAGRANT_OS_PROPERTY_SUFFIX, OperatingSystemFamily.UNIX.toString());

        logger.info("Using Vagrant to create {}", hostLabel);

        CommandProcessor cmdProcessor = atLocation(vagrantDir);
        VagrantDriver vagrantDriver = new VagrantDriver(hostLabel, cmdProcessor);
        VirtualboxDriver vboxDriver = new VirtualboxDriver(cmdProcessor);

        if (vagrantExpirationCmd == null) {
            return new VagrantCloudHost(vagrantVm, vagrantIp, vagrantDriver);
        } else {
            ConnectionOptions options = new ConnectionOptions();
            options.set(ConnectionOptions.ADDRESS, vagrantIp);
            options.set(ConnectionOptions.USERNAME, "vagrant");
            options.set(ConnectionOptions.PASSWORD, "vagrant");

            OverthereConnectionBuilder cb = null;
            if(OperatingSystemFamily.WINDOWS.toString().equals(vagrantOs)) {
                options.set(ConnectionOptions.OPERATING_SYSTEM, OperatingSystemFamily.WINDOWS);
                options.set(SshConnectionBuilder.CONNECTION_TYPE, CifsConnectionType.WINRM_INTERNAL);

                cb = new CifsConnectionBuilder("winrm", options, new DefaultAddressPortMapper());
            } else {
                options.set(ConnectionOptions.OPERATING_SYSTEM, OperatingSystemFamily.UNIX);
                options.set(SshConnectionBuilder.CONNECTION_TYPE, SshConnectionType.SFTP);

                cb = new SshConnectionBuilder("ssh", options, new DefaultAddressPortMapper());
            }
            return new CachedVagrantCloudHost(vagrantVm, vagrantIp, Command.fromString(vagrantExpirationCmd), vagrantDriver, vboxDriver, cmdProcessor, cb);
        }
    }

    private static CloudHost createEc2CloudHost(final String label, final String amiId, final boolean disableEc2) {
        if (disableEc2) {
            throw new IllegalStateException("Only an AMI ID (" + amiId + ") has been specified for host label " + label
                + ", but EC2 hosts are not available.");
        }
        logger.info("Using Amazon EC2 for {}", label);
        return new Ec2CloudHost(label, amiId);
    }

    private static CloudHost createLibvirtHost(final String label, final String baseDomain) {
        logger.info("Using Libvirt base domain {} for {}", baseDomain, label);
        return new LibvirtHost(label, baseDomain);
    }

    private static CloudHost wrapCloudHost(String label, CloudHost actualHost) {
        String tunnelUsername = getOvercastProperty(label + TUNNEL_USERNAME_PROPERTY_SUFFIX);
        if (tunnelUsername == null) {
            return actualHost;
        }

        logger.info("Starting SSH tunnels for {}", label);

        String tunnelPassword = getRequiredOvercastProperty(label + TUNNEL_PASSWORD_PROPERTY_SUFFIX);
        String ports = getRequiredOvercastProperty(label + TUNNEL_PORTS_PROPERTY_SUFFIX);
        Map<Integer, Integer> portForwardMap = parsePortsProperty(ports);
        return new TunneledCloudHost(actualHost, tunnelUsername, tunnelPassword, portForwardMap);
    }
}
