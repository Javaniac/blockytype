package com.javaniac.blockytype.bootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One runtime library of `libraries.txt`: `group:artifact:version[:classifier] sha256`. */
public record Library(String group, String artifact, String version, String classifier, String sha256) {

    /** Parses one line. Throws {@link IllegalArgumentException} when it is not `coordinates sha256`. */
    public static Library parse(String line) {
        String[] halves = line.trim().split("\\s+");
        if (halves.length != 2) throw new IllegalArgumentException("Bad library line (want 'group:artifact:version sha256'): " + line);
        String[] c = halves[0].split(":");
        if (c.length < 3 || c.length > 4) throw new IllegalArgumentException("Bad library coordinates: " + halves[0]);
        return new Library(c[0], c[1], c[2], c.length == 4 ? c[3] : null, halves[1].toLowerCase(Locale.ROOT));
    }

    /** Parses all lines, skipping blank lines and `#` comments. */
    public static List<Library> parseAll(Iterable<String> lines) {
        List<Library> libs = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            libs.add(parse(t));
        }
        return libs;
    }

    /** Path below a Maven repository root, for example `org/xerial/sqlite-jdbc/3.50.2.0/sqlite-jdbc-3.50.2.0.jar`. */
    public String path() {
        return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version
                + (classifier == null ? "" : "-" + classifier) + ".jar";
    }

    @Override
    public String toString() {
        return group + ":" + artifact + ":" + version + (classifier == null ? "" : ":" + classifier);
    }
}
