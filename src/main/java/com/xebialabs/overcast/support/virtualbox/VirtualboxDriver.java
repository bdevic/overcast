package com.xebialabs.overcast.support.virtualbox;

import java.util.Map;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;

import com.xebialabs.overcast.command.CommandProcessor;

import static com.google.common.base.Splitter.on;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static com.xebialabs.overcast.command.Command.aCommand;
import static com.xebialabs.overcast.support.virtualbox.VirtualboxState.POWEROFF;
import static com.xebialabs.overcast.support.virtualbox.VirtualboxState.SAVED;

public class VirtualboxDriver {

    private CommandProcessor commandProcessor;

    public VirtualboxDriver(final CommandProcessor commandProcessor) {
        this.commandProcessor = commandProcessor;
    }

    /**
     * Fetches VM state.
     */
    public VirtualboxState vmState(String vm) {
        return VirtualboxState.fromStatusString(execute("showvminfo", vm));
    }

    /**
     * Checks if VM exists. Accepts UUID or VM name as an argument.
     */
    public boolean vmExists(final String vm) {
        return filter(newArrayList(on("\n").split(execute("list", "vms"))), new Predicate<String>() {
            @Override
            public boolean apply(final String i) {
                System.out.println(i);
                return i.endsWith("{" + vm + "}") || i.startsWith("\"" + vm + "\"");
            }
        }).size() == 1;
    }

    /**
     * Shuts down if running, restores the snapshot and starts VM.
     */
    public void loadSnapshot(String vm, String snapshotUuid) {
        if (!newHashSet(POWEROFF, SAVED).contains(vmState(vm))) {
            powerOff(vm);
        }
        execute("snapshot", vm, "restore", snapshotUuid);
        start(vm);
    }

    /**
     * Shuts down if running, restores the latest snapshot and starts VM.
     */
    public void loadLatestSnapshot(final String vm) {
        Map<String,String> split = Splitter.on('\n').omitEmptyStrings()
                .withKeyValueSeparator("=")
                .split(execute("snapshot", vm, "list", "--machinereadable"));
        String quotedId = split.get("CurrentSnapshotUUID");

        loadSnapshot(vm, quotedId.substring(1, quotedId.length() - 1));
    }

    /**
     * Shuts down VM.
     */
    public void powerOff(final String vm) {
        execute("controlvm", vm, "poweroff");
    }

    public void start(String vm) {
        execute("startvm", vm, "--type", "headless");
    }

    /**
     * Executes custom VBoxManage command
     */
    public String execute(String... command) {
        return commandProcessor.run(aCommand("VBoxManage").withArguments(command)).getOutput();
    }

    /**
     * Sets extra data on the VM
     */
    public void setExtraData(String vm, String k, String v) {
        execute("setextradata", vm, k, v);
    }

    public String getExtraData(String vm, String k) {
        final String prefix = "Value: ";

        String v = execute("getextradata", vm, k).trim();
        return v.equals("No value set!") ? null : v.substring(prefix.length());
    }

    public void createSnapshot(String vm, String name) {
        execute("snapshot", vm, "take", name, "--description", "'Snapshot taken by Overcast.'");
    }
}
