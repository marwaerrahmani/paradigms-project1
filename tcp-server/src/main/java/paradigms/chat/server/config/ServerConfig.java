package paradigms.chat.server.config;

import paradigms.chat.config.ConfigFiles;

import java.util.Properties;

/**
 * Bind address and port for TCPServer. Values come from {@code server.properties}.
 */
public record ServerConfig(String bindAddress, int port) {

    public static ServerConfig load() {
        return from(ConfigFiles.load("server.properties", ServerConfig.class));
    }

    public static ServerConfig from(Properties props) {
        String bind = props.getProperty("server.bindAddress", "0.0.0.0").trim();
        if (bind.isEmpty()) {
            bind = "0.0.0.0";
        }
        int port = parsePort(props.getProperty("server.port", "3000"));
        return new ServerConfig(bind, port);
    }

    static int parsePort(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("server.port is missing.");
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 0 || port > 65535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("server.port must be a number from 0 to 65535.");
        }
    }
}
