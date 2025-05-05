/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.aws.global_configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.util.FormValidation;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.regions.Region;

@WithJenkins
class CredentialsAwsGlobalConfigurationTest {

    @Test
    void doCheckRegion(JenkinsRule r) {
        CredentialsAwsGlobalConfiguration descriptor = CredentialsAwsGlobalConfiguration.get();
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckRegion("").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckRegion("us-west-1").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckRegion("no-valid").kind);
    }

    @Test
    void uiAndStorage(JenkinsRule r) throws Throwable {
        assertNull(CredentialsAwsGlobalConfiguration.get().getRegion(), "not set initially");
        JenkinsRule.WebClient wc = r.createWebClient();
        HtmlForm config = wc.goTo("aws").getFormByName("config");
        r.submit(config);
        assertNull(CredentialsAwsGlobalConfiguration.get().getRegion(), "round-trips to null");
        config = wc.goTo("aws").getFormByName("config");
        HtmlSelect select = config.getSelectByName("_.region");
        select.setSelectedAttribute(Region.SA_EAST_1.id(), true);
        r.submit(config);
        assertEquals(
                Region.SA_EAST_1.id(),
                CredentialsAwsGlobalConfiguration.get().getRegion(),
                "global config page let us edit it");

        r.restart();

        assertEquals(
                Region.SA_EAST_1.id(),
                CredentialsAwsGlobalConfiguration.get().getRegion(),
                "still there after restart of Jenkins");
    }

    @Test
    void credentials(JenkinsRule r) {
        AmazonWebServicesCredentials credentials = new AWSCredentialsImpl(
                CredentialsScope.GLOBAL, "CredentialsAwsGlobalConfigurationTest", "xxx", "secret", "test credentials");
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        CredentialsAwsGlobalConfiguration descriptor = CredentialsAwsGlobalConfiguration.get();
        descriptor.setCredentialsId("CredentialsAwsGlobalConfigurationTest");
        assertEquals(credentials, descriptor.getCredentials());
        descriptor.setCredentialsId("");
        assertNull(descriptor.getCredentials());
    }
}
