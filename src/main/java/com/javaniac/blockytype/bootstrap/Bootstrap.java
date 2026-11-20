package com.javaniac.blockytype.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point of the jar. The jar holds only BlockyType's own classes: this class downloads every library listed in
 * {@code libraries.txt} (Kotlin, Minestom, logback, ...) into {@code libraries/}, puts them and the jar itself on one
 * class loader and starts {@code BlockyType.main}. It uses nothing but the JDK, because no library is available yet.
 */
public final class Bootstrap {
    static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    static final String MAIN_CLASS = "com.javaniac.blockytype.BlockyType";

    private Bootstrap() {}

    public static void main(String[] args) throws Exception {
        List<Library> libs;
        try (InputStream in = Bootstrap.class.getResourceAsStream("/libraries.txt")) {
            if (in == null) throw new IllegalStateException("libraries.txt is missing from the jar");
            libs = Library.parseAll(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
        }
        List<Path> jars = new LibraryDownloader(MAVEN_CENTRAL, Path.of("libraries"), s -> System.out.println("[bootstrap] " + s)).ensure(libs);

        List<URL> urls = new ArrayList<>();
        for (Path p : jars) urls.add(p.toUri().toURL());
        urls.add(selfJar().toUri().toURL());

        // Parent = platform loader: Kotlin, Minestom and BlockyType must all live in this one loader.
        URLClassLoader loader = new URLClassLoader("blockytype", urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        Method main = loader.loadClass(MAIN_CLASS).getMethod("main", String[].class);
        try {
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) throw ex;
            if (e.getCause() instanceof Error err) throw err;
            throw e;
        }
    }

    private static Path selfJar() throws IOException {
        try {
            return Path.of(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }
}
