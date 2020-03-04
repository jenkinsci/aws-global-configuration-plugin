package io.jenkins.plugins.aws.global_configuration;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class EndpointConfigurationCasCIT extends AbstractEndpointConfigurationIT {
    @Rule
    public JenkinsRule r = new JenkinsConfiguredWithCodeRule();

    @Override
    protected EndpointConfiguration getEndpointConfiguration() {
        return (EndpointConfiguration) r.jenkins.getDescriptor(EndpointConfiguration.class);
    }

    @Override
    protected void setEndpointConfiguration(String serviceEndpoint, String signingRegion) {
        // no-op (configured by annotations)
    }

    @Override
    @Test
    @ConfiguredWithCode("/default.yml")
    public void shouldHaveDefaultConfiguration() {
        super.shouldHaveDefaultConfiguration();
    }

    @Override
    @Test
    @ConfiguredWithCode("/custom-endpoint-configuration.yml")
    public void shouldCustomiseEndpointConfiguration() {
        super.shouldCustomiseEndpointConfiguration();
    }
}
