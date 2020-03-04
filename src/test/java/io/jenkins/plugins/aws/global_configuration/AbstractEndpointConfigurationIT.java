package io.jenkins.plugins.aws.global_configuration;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

public abstract class AbstractEndpointConfigurationIT {

    protected abstract EndpointConfiguration getEndpointConfiguration();

    protected abstract void setEndpointConfiguration(String serviceEndpoint, String signingRegion);

    @Test
    public void shouldHaveDefaultConfiguration() {
        final EndpointConfiguration config = getEndpointConfiguration();

        assertSoftly(s -> {
            s.assertThat(config.getServiceEndpoint()).as("Service Endpoint").isNull();
            s.assertThat(config.getSigningRegion()).as("Signing Region").isNull();
        });
    }

    @Test
    public void shouldCustomiseEndpointConfiguration() {
        // Given
        setEndpointConfiguration("http://localhost:4584", "us-east-1");

        // When
        final EndpointConfiguration config = getEndpointConfiguration();

        // Then
        assertSoftly(s -> {
            s.assertThat(config.getServiceEndpoint()).as("Service Endpoint").isEqualTo("http://localhost:4584");
            s.assertThat(config.getSigningRegion()).as("Signing Region").isEqualTo("us-east-1");
        });
    }
}
