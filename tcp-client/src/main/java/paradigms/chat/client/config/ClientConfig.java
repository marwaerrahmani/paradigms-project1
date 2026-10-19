package paradigms.chat.client.config;

import paradigms.chat.config.ConfigFiles;

import java.util.List;
import java.util.Properties;

/**
 * Server address for TCPClient. Command-line arguments override {@code client.properties}.
 */
public record ClientConfig(String host, int port, boolean fromCommandLine, String error) {

    public boolean valid() {
        return error == null;
    }

    public static ClientConfig load(List<String> args) {
        try {
            return from(ConfigFiles.load("client.properties", ClientConfig.class), args);
        } catch (RuntimeException ex) {
            return new ClientConfig("", 0, false, ex.getMessage());
        }
    }

    public static ClientConfig from(Properties props, List<String> args) {
        List<String> raw = args == null ? List.of() : args;
        if (raw.size() == 1) {
            throw new IllegalArgumentException("Usage: java TCPClient <ServerIPAddress> <PortNumber>");
        }
        String host = props.getProperty("server.host", "localhost").trim();
        String portRaw = props.getProperty("server.port", "3000");
        boolean fromArgs = false;
        if (raw.size() >= 2) {
            host = raw.get(0).trim();
            portRaw = raw.get(1);
            fromArgs = true;
        }
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Server address is empty.");
        }
        return new ClientConfig(host, parsePort(portRaw), fromArgs, null);
    }

    private static int parsePort(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Port must be a number from 1 to 65535.");
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Port must be a number from 1 to 65535.");
        }
    }
}
