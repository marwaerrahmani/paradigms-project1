package paradigms.chat.server.config;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigTest {

    @Test
    void readsPortAndBindAddress() {
        Properties props = new Properties();
        props.setProperty("server.port", "4040");
        props.setProperty("server.bindAddress", "127.0.0.1");
        ServerConfig config = ServerConfig.from(props);
        assertEquals(4040, config.port());
        assertEquals("127.0.0.1", config.bindAddress());
    }

    @Test
    void loadsClasspathDefaults() {
        ServerConfig config = ServerConfig.load();
        assertEquals(3000, config.port());
        assertEquals("0.0.0.0", config.bindAddress());
    }

    @Test
    void rejectsABadPort() {
        Properties props = new Properties();
        props.setProperty("server.port", "nope");
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.from(props));
    }
}
