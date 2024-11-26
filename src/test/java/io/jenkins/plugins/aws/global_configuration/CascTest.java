package io.jenkins.plugins.aws.global_configuration;

import static org.junit.Assert.assertEquals;

import hudson.ExtensionList;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class CascTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule r, String configContent) {
        CredentialsAwsGlobalConfiguration cfg = ExtensionList.lookupSingleton(CredentialsAwsGlobalConfiguration.class);
        assertEquals("aws", cfg.getCredentialsId());
        assertEquals("us-east-1", cfg.getRegion());
    }

    @Override
    protected String stringInLogExpected() {
        return "CredentialsAwsGlobalConfiguration";
    }
}
