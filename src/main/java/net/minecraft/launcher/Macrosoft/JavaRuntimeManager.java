package net.minecraft.launcher.Macrosoft;

import com.mojang.launcher.OperatingSystem;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public final class JavaRuntimeManager {

    public interface ProgressListener {
        void onProgress(String stage, int percent, String detail);
    }

    public static class JavaRuntimeOption {
        public final String id;
        public final String os;
        public final String arch;
        public final String url;

        public JavaRuntimeOption(String id, String os, String arch, String url) {
            this.id = id;
            this.os = os;
            this.arch = arch;
            this.url = url;
        }

        @Override
        public String toString() {
            return id + " (" + os + "/" + arch + ")";
        }
    }

    public static class InstalledRuntime {
        public final String id;
        public final Path directory;
        public final Path javaExecutable;

        public InstalledRuntime(String id, Path directory, Path javaExecutable) {
            this.id = id;
            this.directory = directory;
            this.javaExecutable = javaExecutable;
        }

        @Override
        public String toString() {
            return id + " → " + javaExecutable;
        }
    }

    private JavaRuntimeManager() {
    }

    public static Path javaBaseDir(Path macrosoftBaseDir) {
        return macrosoftBaseDir.resolve(".java");
    }

    public static List<JavaRuntimeOption> listOptionsFromApi(org.json.JSONObject apiJson) {
        List<JavaRuntimeOption> options = new ArrayList<>();
        if (apiJson == null) {
            return options;
        }

        org.json.JSONObject javaRoot = apiJson.optJSONObject("java");
        if (javaRoot == null) {
            return options;
        }

        for (String runtimeId : javaRoot.keySet()) {
            org.json.JSONObject runtimeObj = javaRoot.optJSONObject(runtimeId);
            if (runtimeObj == null) {
                continue;
            }
            for (String os : runtimeObj.keySet()) {
                org.json.JSONObject osObj = runtimeObj.optJSONObject(os);
                if (osObj == null) {
                    continue;
                }
                for (String arch : osObj.keySet()) {
                    String url = osObj.optString(arch, null);
                    if (url != null && !url.trim().isEmpty()) {
                        options.add(new JavaRuntimeOption(runtimeId, os, arch, url));
                    }
                }
            }
        }
        return options;
    }

    public static List<JavaRuntimeOption> filterForCurrentPlatform(List<JavaRuntimeOption> all) {
        String targetOs = currentOsKey();
        String targetArch = currentArchKey();

        List<JavaRuntimeOption> out = new ArrayList<>();
        for (JavaRuntimeOption option : all) {
            if (targetOs.equalsIgnoreCase(option.os) && targetArch.equalsIgnoreCase(option.arch)) {
                out.add(option);
            }
        }
        return out;
    }

    public static List<InstalledRuntime> listInstalled(Path macrosoftBaseDir) {
        List<InstalledRuntime> installed = new ArrayList<>();
        Path javaDir = javaBaseDir(macrosoftBaseDir);
        if (!Files.isDirectory(javaDir)) {
            return installed;
        }

        try (Stream<Path> stream = Files.list(javaDir)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    Path executable = findJavaExecutable(dir);
                    if (executable != null) {
                        installed.add(new InstalledRuntime(dir.getFileName().toString(), dir, executable));
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }

        installed.sort(Comparator.comparing(i -> i.id));
        return installed;
    }

    public static InstalledRuntime install(Path macrosoftBaseDir, JavaRuntimeOption option) throws IOException {
        return install(macrosoftBaseDir, option, (stage, percent, detail) -> {});
    }

    public static InstalledRuntime install(Path macrosoftBaseDir, JavaRuntimeOption option, ProgressListener listener) throws IOException {
        Path baseDir = javaBaseDir(macrosoftBaseDir);
        Files.createDirectories(baseDir);

        Path tempArchive = Files.createTempFile("macrosoft-java-", ".tar.gz");
        Path tempExtract = Files.createTempDirectory(baseDir, ".tmp-java-");

        try {
            listener.onProgress("download", 0, "Iniciando download...");
            download(option.url, tempArchive, listener);
            listener.onProgress("download", 70, "Download concluído");

            listener.onProgress("extract", 72, "Extraindo arquivos...");
            extractTarGz(tempArchive, tempExtract, listener);
            listener.onProgress("extract", 96, "Extração concluída");

            Path finalRuntimeDir = baseDir.resolve(option.id);
            deleteRecursively(finalRuntimeDir);
            Files.move(tempExtract, finalRuntimeDir, StandardCopyOption.REPLACE_EXISTING);

            Path executable = findJavaExecutable(finalRuntimeDir);
            if (executable == null) {
                throw new IOException("Java executável não encontrado após instalação: " + option.id);
            }
            listener.onProgress("finalize", 100, "Instalação finalizada");
            return new InstalledRuntime(option.id, finalRuntimeDir, executable);
        } finally {
            Files.deleteIfExists(tempArchive);
            if (Files.exists(tempExtract)) {
                deleteRecursively(tempExtract);
            }
        }
    }

    public static void uninstall(Path macrosoftBaseDir, InstalledRuntime runtime) throws IOException {
        deleteRecursively(runtime.directory);
    }

    public static Map<String, InstalledRuntime> mapInstalledById(Path macrosoftBaseDir) {
        Map<String, InstalledRuntime> map = new HashMap<>();
        for (InstalledRuntime runtime : listInstalled(macrosoftBaseDir)) {
            map.put(runtime.id, runtime);
        }
        return map;
    }

    private static void download(String url, Path target, ProgressListener listener) throws IOException {
        URLConnection connection = new URL(url).openConnection();
        long total = connection.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            long downloaded = 0L;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                int percent = 0;
                String detail;
                if (total > 0) {
                    percent = (int) Math.min(70, (downloaded * 70) / total);
                    detail = humanSize(downloaded) + " / " + humanSize(total);
                } else {
                    detail = humanSize(downloaded);
                }
                listener.onProgress("download", percent, "Baixando... " + detail);
            }
        }
    }

    private static void extractTarGz(Path archive, Path targetDir, ProgressListener listener) throws IOException {
        long archiveSize = Files.size(archive);
        try (InputStream fileIn = new ProgressInputStream(Files.newInputStream(archive));
             InputStream gzipIn = new GZIPInputStream(fileIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                Path outPath = safeResolve(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Path parent = outPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(tarIn, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
                long consumed = ((ProgressInputStream) fileIn).getReadBytes();
                int percent = archiveSize > 0 ? 72 + (int) Math.min(24, (consumed * 24) / archiveSize) : 80;
                listener.onProgress("extract", percent, "Extraindo: " + entry.getName());
            }
        }
    }

    private static String humanSize(long bytes) {
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024.0;
            index++;
        }
        return String.format("%.1f %s", value, units[index]);
    }

    private static class ProgressInputStream extends InputStream {
        private final InputStream delegate;
        private long readBytes;

        ProgressInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        long getReadBytes() {
            return readBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) readBytes++;
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = delegate.read(b, off, len);
            if (count > 0) readBytes += count;
            return count;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static Path safeResolve(Path base, String child) throws IOException {
        Path out = base.resolve(child).normalize();
        if (!out.startsWith(base.normalize())) {
            throw new IOException("Entrada inválida no tar: " + child);
        }
        return out;
    }

    private static Path findJavaExecutable(Path root) throws IOException {
        final String exeName = OperatingSystem.getCurrentPlatform() == OperatingSystem.WINDOWS ? "javaw.exe" : "java";

        try (Stream<Path> walk = Files.walk(root, 6)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(exeName)
                    || (OperatingSystem.getCurrentPlatform() == OperatingSystem.WINDOWS
                        && path.getFileName().toString().equalsIgnoreCase("java.exe")))
                .findFirst()
                .orElse(null);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    private static String currentOsKey() {
        OperatingSystem os = OperatingSystem.getCurrentPlatform();
        switch (os) {
            case WINDOWS:
                return "windows";
            case OSX:
                return "macos";
            case LINUX:
            default:
                return "linux";
        }
    }

    private static String currentArchKey() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        return arch.contains("64") ? "x64" : "x86";
    }
}
