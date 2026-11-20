package com.javaniac.blockytype.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Downloads the libraries that are not in {@code dir} yet (Maven layout) from a Maven repository at {@code baseUrl} and
 * verifies each file against its SHA-256 from {@code libraries.txt}. Plain JDK, so the bootstrap needs no library.
 */
public final class LibraryDownloader {
    private static final int ATTEMPTS = 3;
    private static final int THREADS = 6;
    private static final String HASH_MISMATCH = "Hash mismatch";

    private final String baseUrl;
    private final Path dir;
    private final Consumer<String> log;

    public LibraryDownloader(String baseUrl, Path dir, Consumer<String> log) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.dir = dir;
        this.log = log;
    }

    /** Makes sure every library is present and valid. Returns their paths in the order of {@code libs}. */
    public List<Path> ensure(List<Library> libs) throws IOException {
        List<Library> missing = new ArrayList<>();
        for (Library lib : libs) if (!isValid(dir.resolve(lib.path()), lib.sha256())) missing.add(lib);
        if (!missing.isEmpty()) {
            log.accept("Downloading " + missing.size() + " librar" + (missing.size() == 1 ? "y" : "ies") + " (first start only)...");
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(THREADS, missing.size()));
            try {
                List<Future<?>> jobs = new ArrayList<>();
                int[] done = {0};
                for (Library lib : missing) {
                    jobs.add(pool.submit(() -> {
                        download(lib);
                        int n;
                        synchronized (done) { n = ++done[0]; }
                        log.accept("  [" + n + "/" + missing.size() + "] " + lib);
                        return null;
                    }));
                }
                for (Future<?> job : jobs) {
                    try {
                        job.get();
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof IOException io) throw io;
                        throw new IOException(e.getCause());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while downloading libraries", e);
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        }
        List<Path> paths = new ArrayList<>();
        for (Library lib : libs) paths.add(dir.resolve(lib.path()));
        return paths;
    }

    private void download(Library lib) throws IOException {
        Path target = dir.resolve(lib.path());
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                fetch(baseUrl + lib.path(), part);
                String actual = sha256(part);
                if (!actual.equals(lib.sha256())) {
                    Files.deleteIfExists(part);
                    // The file itself is wrong: retrying would fetch the same bytes.
                    throw new SecurityIOException(HASH_MISMATCH + " for " + lib + ": expected SHA-256 " + lib.sha256() + " but got " + actual);
                }
                try {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (SecurityIOException e) {
                throw e;
            } catch (IOException e) {
                Files.deleteIfExists(part);
                last = new IOException("Could not download " + lib + ": " + e.getMessage(), e);
                try {
                    Thread.sleep(200L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw last;
                }
            }
        }
        throw last;
    }

    /** A downloaded file that does not match its hash. Not retried. */
    private static final class SecurityIOException extends IOException {
        SecurityIOException(String message) {
            super(message);
        }
    }

    private static void fetch(String url, Path to) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(60_000);
        c.setRequestProperty("User-Agent", "BlockyType-bootstrap");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " for " + url);
            try (InputStream in = c.getInputStream()) {
                Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            c.disconnect();
        }
    }

    private static boolean isValid(Path file, String sha256) {
        try {
            return Files.isRegularFile(file) && sha256(file).equals(sha256);
        } catch (IOException e) {
            return false;
        }
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
