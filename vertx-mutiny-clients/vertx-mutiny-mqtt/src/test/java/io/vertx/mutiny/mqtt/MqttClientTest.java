package io.vertx.mutiny.mqtt;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.MountableFile;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.mqtt.messages.MqttConnAckMessage;

public class MqttClientTest {

    public static GenericContainer<?> container = new GenericContainer<>("eclipse-mosquitto:2.0")
            .withExposedPorts(1883)
            .withLogConsumer(of -> System.out.print(of.getUtf8String()))
            .withCopyFileToContainer(MountableFile.forClasspathResource("mosquitto.conf"), "/mosquitto/config/mosquitto.conf")
            .waitingFor(Wait.forListeningPort())
            .waitingFor(Wait.forLogMessage(".*mosquitto .* running.*", 1));

    @BeforeClass
    public static void init() {
        container.start();
    }

    @AfterClass
    public static void shutdown() {
        container.stop();
    }

    Vertx vertx;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        assertThat(vertx, is(notNullValue()));
    }

    @After
    public void tearDown() {
        vertx.closeAndAwait();
    }

    @Test
    public void test() {
        MqttClient client = MqttClient.create(vertx, new MqttClientOptions()
                .setAutoGeneratedClientId(true));

        // Unfortunately, the latest mosquitto are taking a lot of time to accept connection, without any way to find out
        // need a retry loop
        AtomicReference<MqttConnAckMessage> ref = new AtomicReference<>();
        Awaitility.await().atMost(Duration.ofSeconds(30))
                .until(() -> {
                    try {
                        ref.set(client
                                .connect(container.getMappedPort(1883), container.getHost())
                                .await().indefinitely());
                    } catch (Exception e) {
                        return false;
                    }
                    return true;
                });

        assertThat(ref.get(), is(notNullValue()));
        assertThat(ref.get().code(), is(MqttConnectReturnCode.CONNECTION_ACCEPTED));
        client.disconnectAndAwait();
    }

}
