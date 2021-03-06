package com.xebialabs.overcast.support.libvirt;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xebialabs.overthere.CmdLine;
import com.xebialabs.overthere.OverthereConnection;
import com.xebialabs.overthere.util.CapturingOverthereExecutionOutputHandler;

import static com.xebialabs.overcast.OvercastProperties.getOvercastProperty;
import static com.xebialabs.overcast.OvercastProperties.getRequiredOvercastProperty;
import static com.xebialabs.overcast.OverthereUtil.overthereConnectionFromURI;
import static com.xebialabs.overthere.util.CapturingOverthereExecutionOutputHandler.capturingHandler;

/**
 * {@link IpLookupStrategy} that uses SSH to execute a command on a remote host to look up the IP based on the MAC.
 */
public class SshIpLookupStrategy implements IpLookupStrategy {
    private static final Logger log = LoggerFactory.getLogger(SshIpLookupStrategy.class);

    private static final String SSH_TIMEOUT_SUFFIX = ".SSH.timeout";
    private static final String SSH_COMMAND_SUFFIX = ".SSH.command";
    private static final String SSH_URL_SUFFIX = ".SSH.url";

    private URI url;
    private String command;
    private int timeout;

    public SshIpLookupStrategy(URI url, String command, int timeout) {
        this.url = url;
        this.command = command;
        this.timeout = timeout;
    }

    public static SshIpLookupStrategy create(String prefix) {
        try {
            URI uri = new URI(getRequiredOvercastProperty(prefix + SSH_URL_SUFFIX));
            String command = getRequiredOvercastProperty(prefix + SSH_COMMAND_SUFFIX);
            int timeout = Integer.parseInt(getOvercastProperty(prefix + SSH_TIMEOUT_SUFFIX, "60"));
            SshIpLookupStrategy instance = new SshIpLookupStrategy(uri, command, timeout);
            return instance;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String lookup(String mac) {
        CmdLine cmdLine = new CmdLine();
        String fragment = MessageFormat.format(command, mac);
        cmdLine.addRaw(fragment);
        log.info("Executing '{}'", cmdLine);

        OverthereConnection connection = overthereConnectionFromURI(url);
        try {
            int seconds = timeout;
            while (seconds > 0) {
                CapturingOverthereExecutionOutputHandler outputHandler = capturingHandler();
                CapturingOverthereExecutionOutputHandler errorOutputHandler = capturingHandler();
                connection.execute(outputHandler, errorOutputHandler, cmdLine);
                if (!errorOutputHandler.getOutputLines().isEmpty()) {
                    throw new RuntimeException("Had stderror: " + errorOutputHandler.getOutput());
                }
                if (outputHandler.getOutputLines().isEmpty()) {
                    sleep(1);
                    seconds--;
                    log.debug("No IP found after will try for {} seconds", seconds);
                    continue;
                }
                String line = outputHandler.getOutputLines().get(0);
                log.debug("Found IP={} for MAC={}", line, mac);
                return outputHandler.getOutputLines().get(0);
            }
            throw new RuntimeException("No IP found for MAC: " + mac);
        } finally {
            connection.close();
        }
    }

    private static void sleep(final int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
