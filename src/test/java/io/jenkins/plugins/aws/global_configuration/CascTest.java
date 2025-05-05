package io.jenkins.plugins.aws.global_configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.ExtensionList;
import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CascTest extends AbstractRoundTripTest {

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule r, String configContent) {
        CredentialsAwsGlobalConfiguration cfg = ExtensionList.lookupSingleton(CredentialsAwsGlobalConfiguration.class);
        assertEquals("aws", cfg.getCredentialsId());
        assertEquals("us-east-1", cfg.getRegion());
    }

    @Override
    protected String stringInLogExpected() {
        return "CredentialsAwsGlobalConfiguration";
    }
}
