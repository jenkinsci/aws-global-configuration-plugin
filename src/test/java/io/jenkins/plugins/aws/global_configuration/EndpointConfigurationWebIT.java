package io.jenkins.plugins.aws.global_configuration;

import io.jenkins.plugins.aws.global_configuration.util.JenkinsConfiguredWithWebRule;
import org.junit.Rule;

public class EndpointConfigurationWebIT extends AbstractEndpointConfigurationIT {

    @Rule
    public final JenkinsConfiguredWithWebRule r = new JenkinsConfiguredWithWebRule("aws");

    @Override
    protected EndpointConfiguration getEndpointConfiguration() {
        return (EndpointConfiguration) r.jenkins.getDescriptor(EndpointConfiguration.class);
    }

    @Override
    protected void setEndpointConfiguration(String serviceEndpoint, String signingRegion) {
        r.configure(form -> {
            form.getInputByName("_.serviceEndpoint").setValueAttribute(serviceEndpoint);
            form.getSelectByName("_.signingRegion").setSelectedAttribute(signingRegion, true);
        });
    }
}
