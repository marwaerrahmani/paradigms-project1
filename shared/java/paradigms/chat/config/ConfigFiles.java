package paradigms.chat.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads a {@code .properties} file at runtime.
 * Classpath defaults are applied first. A file next to the executable, then a
 * file in the working directory, overrides them so a deployment can change
 * host and port without recompiling.
 */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    public static Properties load(String fileName, Class<?> anchor) {
        Properties props = new Properties();
        loadResource(props, fileName, anchor);
        Path besideCode = besideCode(fileName, anchor);
        if (besideCode != null) {
            loadFile(props, besideCode);
        }
        Path workingDir = Path.of(fileName).toAbsolutePath().normalize();
        if ((besideCode == null || !workingDir.equals(besideCode)) && Files.isRegularFile(workingDir)) {
            loadFile(props, workingDir);
        }
        return props;
    }

    private static void loadResource(Properties props, String fileName, Class<?> anchor) {
        try (InputStream in = anchor.getResourceAsStream("/" + fileName)) {
            if (in == null) {
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read classpath " + fileName, ex);
        }
    }

    private static Path besideCode(String fileName, Class<?> anchor) {
        try {
            var source = anchor.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path location = Path.of(source.getLocation().toURI());
            Path dir = Files.isDirectory(location) ? location : location.getParent();
            if (dir == null) {
                return null;
            }
            Path candidate = dir.resolve(fileName).normalize();
            return Files.isRegularFile(candidate) ? candidate : null;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static void loadFile(Properties props, Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + path, ex);
        }
    }
}
