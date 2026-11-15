package cv.igrp.platform.access_management.compat;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;

/**
 * A single-node Kafka (KRaft) for tests (v3-22, #32). Testcontainers' own
 * KafkaContainer writes a start script into the container and runs it, which
 * Docker Desktop on Windows can refuse ("Text file busy"); this one is the
 * plain image, configured by environment, on a host port chosen up front so
 * the broker can advertise it.
 */
public final class KafkaBroker {

    private static final int INTERNAL = 9092;

    private final int port = freePort();
    private final String host = DockerClientFactory.instance().dockerHostIpAddress();

    @SuppressWarnings("resource")
    private final GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("apache/kafka:3.9.1"))
            .withEnv("KAFKA_NODE_ID", "1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:" + INTERNAL + ",CONTROLLER://:9093")
            .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://" + host + ":" + port)
            .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT")
            .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withExposedPorts(INTERNAL)
            .withCreateContainerCmdModifier(cmd -> {
                HostConfig hostConfig = cmd.getHostConfig() == null ? HostConfig.newHostConfig() : cmd.getHostConfig();
                cmd.withHostConfig(hostConfig.withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(port), ExposedPort.tcp(INTERNAL))));
            })
            .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    public KafkaBroker start() {
        container.start();
        return this;
    }

    public String bootstrapServers() {
        return host + ":" + port;
    }

    /** An outage: the broker stops answering, and comes back as it was. */
    public void pause() {
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    public void unpause() {
        container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
