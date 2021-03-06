package com.xebialabs.overcast.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.google.common.base.Splitter;

import static com.google.common.base.Joiner.on;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Collections2.filter;

public class Command {

    private List<String> command = new ArrayList<String>();

    private Command() {}

    public static Command aCommand(String executable) {
        if (executable == null) {
            throw new IllegalArgumentException("Executable can not be null");
        }
        Command c = new Command();
        c.withPart(executable);
        return c;
    }

    public Command withPart(String... part) {
        if (part == null) {
            return this;
        }

        command.addAll(filter(Arrays.asList(part), notNull()));
        return this;
    }

    public Command withArguments(String... argument) {
        return withPart(argument);
    }

    public Command withOptions(String... option) {
        return withPart(option);
    }

    public Command withPrefix(String prefix) {
        return withPart(prefix);
    }

    public List<String> getCommand() {
        return command;
    }

    @Override
    public String toString() {
        return on(" ").join(command);
    }

    public static Command fromString(String s) {
        Command c = new Command();

        for (String o : Splitter.on(" ").split(s)) {
            c.getCommand().add(o);
        }

        return c;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof Command)) {
            return false;
        }

        return this.toString().equals(obj.toString());
    }

    @Override
    public int hashCode() {
        int c = 1000;

        for (char ch : this.toString().toCharArray()) {
            c += ch;
        }

        return c;
    }

    public List<String> asList() {
        return Arrays.asList(getCommand().toArray(new String[getCommand().size()]));
    }
}
