package paradigms.chat.client.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigTest {

    @Test
    void commandLineOverridesTheFile() {
        Properties props = new Properties();
        props.setProperty("server.host", "10.0.0.8");
        props.setProperty("server.port", "3000");
        ClientConfig config = ClientConfig.from(props, List.of("localhost", "4040"));
        assertEquals("localhost", config.host());
        assertEquals(4040, config.port());
        assertTrue(config.fromCommandLine());
        assertTrue(config.valid());
    }

    @Test
    void oneArgumentIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ClientConfig.from(new Properties(), List.of("localhost")));
    }

    @Test
    void missingArgumentsUseTheFile() {
        Properties props = new Properties();
        props.setProperty("server.host", "10.0.0.8");
        props.setProperty("server.port", "2500");
        ClientConfig config = ClientConfig.from(props, List.of());
        assertEquals("10.0.0.8", config.host());
        assertEquals(2500, config.port());
        assertFalse(config.fromCommandLine());
    }
}
