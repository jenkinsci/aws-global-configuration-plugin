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

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import com.amazonaws.regions.Regions;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;

import hudson.util.FormValidation;

public class CredentialsTest {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Test
    public void doCheckRegion() {
        rr.then(r -> {
            Credentials descriptor = Credentials.get();
            assertEquals(descriptor.doCheckRegion("").kind, FormValidation.Kind.OK);
            assertEquals(descriptor.doCheckRegion("us-west-1").kind, FormValidation.Kind.OK);
            assertEquals(descriptor.doCheckRegion("no-valid").kind, FormValidation.Kind.ERROR);
        });
    }

    @Test
    public void uiAndStorage() {
        rr.then(r -> {
            assertNull("not set initially", Credentials.get().getRegion());
            JenkinsRule.WebClient wc = r.createWebClient();
            HtmlForm config = wc.goTo("aws").getFormByName("config");
            r.submit(config);
            assertNull("round-trips to null", Credentials.get().getRegion());
            config = wc.goTo("aws").getFormByName("config");
            HtmlSelect select = config.getSelectByName("_.region");
            select.setSelectedAttribute(Regions.SA_EAST_1.getName(), true);
            r.submit(config);
            assertEquals("global config page let us edit it", Regions.SA_EAST_1.getName(), Credentials.get().getRegion());
        });
        rr.then(r -> {
            assertEquals("still there after restart of Jenkins", Regions.SA_EAST_1.getName(), Credentials.get().getRegion());
        });
    }

    @Test
    public void credentials() {
        rr.then(r -> {
            AmazonWebServicesCredentials credentials = new AWSCredentialsImpl(CredentialsScope.GLOBAL,
                    "CredentialsTest", "xxx", "secret", "test credentials");
            SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
            Credentials descriptor = Credentials.get();
            descriptor.setCredentialsId("CredentialsTest");
            assertEquals(credentials, descriptor.getCredentials());
            descriptor.setCredentialsId("");
            assertNull(descriptor.getCredentials());
        });
    }
}
