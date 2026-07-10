package com.printkeep.quota.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import java.util.Map;

/**
 * Unit tests verifying that custom container info properties are correctly exposed.
 */
class ContainerInfoContributorTests {

    @Test
    @SuppressWarnings("unchecked")
    void testContributeExposesContainerDetails() {
        final ContainerInfoContributor contributor = new ContainerInfoContributor();
        final Info.Builder builder = new Info.Builder();

        contributor.contribute(builder);

        final Info info = builder.build();
        final Map<String, Object> containerInfo = (Map<String, Object>) info.get("container");

        assertThat(containerInfo).isNotNull();
        assertThat(containerInfo).containsKeys("hostname", "osName", "javaVersion", "maxMemoryMb");
    }
}
