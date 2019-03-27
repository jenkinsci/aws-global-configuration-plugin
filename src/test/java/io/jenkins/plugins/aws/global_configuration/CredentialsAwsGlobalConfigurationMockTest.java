/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetSessionTokenResult;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

import jenkins.model.Jenkins;
import org.junit.Ignore;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ CredentialsAwsGlobalConfiguration.class, Jenkins.class, AWSSecurityTokenServiceClientBuilder.class })
@PowerMockIgnore({ "javax.management.*", "org.apache.http.conn.ssl.*", "com.amazonaws.http.conn.ssl.*",
        "javax.net.ssl.*", "javax.xml.*" })
public class CredentialsAwsGlobalConfigurationMockTest {

    private static String CREDENTIALS_ID = "CredentialsAwsGlobalConfigurationMockTest";
    private static String REGION = "us-east-1";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private Jenkins jenkins;

    @Spy
    private CredentialsAwsGlobalConfiguration config = new CredentialsAwsGlobalConfiguration(true);

    @Mock
    private AmazonWebServicesCredentials jenkinsCredentials;

    @Mock
    private AWSSecurityTokenService tokenService;

    @Mock
    private AWSSecurityTokenServiceClientBuilder tokenServiceBuilder;

    @Before
    public void before() throws Exception {
        PowerMockito.mockStatic(CredentialsAwsGlobalConfiguration.class);
        when(CredentialsAwsGlobalConfiguration.get()).thenReturn(config);
        PowerMockito.mockStatic(Jenkins.class);
        when(Jenkins.get()).thenReturn(jenkins);
        doReturn(jenkinsCredentials).when(config).getCredentials(CREDENTIALS_ID);

        // AWS token service mocking
        PowerMockito.spy(AWSSecurityTokenServiceClientBuilder.class);
        PowerMockito.doReturn(tokenServiceBuilder).when(AWSSecurityTokenServiceClientBuilder.class, "standard");
        when(tokenServiceBuilder.build()).thenReturn(tokenService);
        GetSessionTokenResult tokenResult = new GetSessionTokenResult();
        tokenResult.setCredentials(new com.amazonaws.services.securitytoken.model.Credentials());
        tokenResult.getCredentials().setAccessKeyId("xxx");
        tokenResult.getCredentials().setSecretAccessKey("yyy");
        tokenResult.getCredentials().setSessionToken("sss");
        when(tokenService.getSessionToken(any())).thenReturn(tokenResult);

        System.setProperty("aws.accessKeyId", "");
        System.setProperty("aws.secretKey", "");
    }

    @Test
    public void testSessionCredentialsFromSessionCredential() throws Exception {
        AwsClientBuilder<?, ?> builder = AmazonS3ClientBuilder.standard();
        AWSCredentials credentials = mock(AWSSessionCredentials.class);
        when(jenkinsCredentials.getCredentials()).thenReturn(credentials);
        AWSSessionCredentials sessionCredentials = config.sessionCredentials(builder, null, CREDENTIALS_ID);
        assertSame(credentials, sessionCredentials);
    }

    @Test
    public void testSessionCredentialsFromSessionCredentialAndRegion() throws Exception {
        AwsClientBuilder<?, ?> builder = AmazonS3ClientBuilder.standard();
        AWSCredentials credentials = mock(AWSSessionCredentials.class);
        when(jenkinsCredentials.getCredentials()).thenReturn(credentials);
        AWSSessionCredentials sessionCredentials = config.sessionCredentials(builder, REGION, CREDENTIALS_ID);
        assertSame(credentials, sessionCredentials);
    }

    @Test
    public void testSessionCredentialsFromBasicCredential() throws Exception {
        AwsClientBuilder<?, ?> builder = AmazonS3ClientBuilder.standard();
        AWSCredentials credentials = mock(BasicAWSCredentials.class);
        when(jenkinsCredentials.getCredentials()).thenReturn(credentials);
        when(tokenServiceBuilder.withCredentials(isA(AWSStaticCredentialsProvider.class)))
                .thenReturn(tokenServiceBuilder);
        AWSSessionCredentials sessionCredentials = config.sessionCredentials(builder, null, CREDENTIALS_ID);
        sessionCredentials.getAWSAccessKeyId();
        verify(tokenServiceBuilder).withCredentials(isA(AWSStaticCredentialsProvider.class));
        verify(tokenServiceBuilder, never()).withRegion((String) any());
        verify(tokenServiceBuilder, never()).withRegion((Regions) any());
    }

    @Test
    public void testSessionCredentialsFromBasicCredentialAndRegion() throws Exception {
        AwsClientBuilder<?, ?> builder = AmazonS3ClientBuilder.standard();
        AWSCredentials credentials = mock(BasicAWSCredentials.class);
        when(jenkinsCredentials.getCredentials()).thenReturn(credentials);
        when(tokenServiceBuilder.withCredentials(isA(AWSStaticCredentialsProvider.class)))
                .thenReturn(tokenServiceBuilder);
        when(tokenServiceBuilder.withRegion(REGION)).thenReturn(tokenServiceBuilder);
        AWSSessionCredentials sessionCredentials = config.sessionCredentials(builder, REGION, CREDENTIALS_ID);
        sessionCredentials.getAWSAccessKeyId();
        verify(tokenServiceBuilder).withCredentials(isA(AWSStaticCredentialsProvider.class));
        verify(tokenServiceBuilder).withRegion(REGION);
    }

    @Ignore("This relies on not having AWS credentials configured")
    @Test
    public void testSessionCredentialsFromEnvironment() throws Exception {
        // no easy way to test session credentials without an environment
        thrown.expect(IOException.class);
        thrown.expectMessage("Unable to get credentials from environment");
        AwsClientBuilder<?, ?> builder = AmazonS3ClientBuilder.standard();
        config.sessionCredentials(builder, null, null);
    }

}
