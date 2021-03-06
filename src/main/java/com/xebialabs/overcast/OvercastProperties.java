package com.xebialabs.overcast;

import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newLinkedHashMap;

/**
 * Methods to load and access the {@code overcast.properties} file.
 */
public class OvercastProperties {

    public static final String PASSWORD_PROPERTY_SUFFIX = ".password";

    private static Logger logger = LoggerFactory.getLogger(OvercastProperties.class);


    private static Properties overcastProperties;

    static {
        overcastProperties = PropertiesLoader.loadOvercastProperties();
    }

    public static String getOvercastProperty(String key) {
        return getOvercastProperty(key, null);
    }

    public static String getOvercastProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            value = overcastProperties.getProperty(key, defaultValue);
        }
        if (logger.isTraceEnabled()) {
            if (value == null) {
                logger.trace("Overcast property {} is null", key);
            } else {
                logger.trace("Overcast property {}={}", key, key.endsWith(PASSWORD_PROPERTY_SUFFIX) ? "********" : value);
            }
        }
        return value;
    }

    public static String getRequiredOvercastProperty(String key) {
        String value = getOvercastProperty(key);
        checkState(value != null, "Required property %s is not specified as a system property or in " + PropertiesLoader.OVERCAST_PROPERTY_FILE
                + " which can be placed in the current working directory, in ~/.overcast or on the classpath", key);
        return value;
    }




    public static Map<Integer, Integer> parsePortsProperty(String ports) {
        Map<Integer, Integer> portForwardMap = newLinkedHashMap();
        StringTokenizer toker = new StringTokenizer(ports, ",");
        while (toker.hasMoreTokens()) {
            String[] localAndRemotePort = toker.nextToken().split(":");
            checkArgument(localAndRemotePort.length == 2, "Property value \"%s\" does not have the right format, e.g. 2222:22,1445:445", ports);
            try {
                int localPort = Integer.parseInt(localAndRemotePort[0]);
                int remotePort = Integer.parseInt(localAndRemotePort[1]);
                portForwardMap.put(remotePort, localPort);
            } catch (NumberFormatException exc) {
                throw new IllegalArgumentException("Property value \"" + ports + "\" does not have the right format, e.g. 2222:22,1445:445", exc);
            }
        }
        return portForwardMap;
    }


}
