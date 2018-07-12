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

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest;
import com.amazonaws.services.securitytoken.model.GetSessionTokenResult;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Failure;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

/**
 * Store the AWS configuration to save it on a separate file
 */
@Extension @Symbol("credentials")
public class CredentialsAwsGlobalConfiguration extends AbstractAwsGlobalConfiguration {

    /**
     * field to fake endpoint on test.
     */
    static AwsClientBuilder.EndpointConfiguration ENDPOINT;

    /**
     * Session token duration in seconds.
     */
    @SuppressWarnings("FieldMayBeFinal")
    private static int SESSION_DURATION = Integer.getInteger(CredentialsAwsGlobalConfiguration.class.getName() + ".sessionDuration",
            3600);

    /**
     * force the region to use for the presigned S3 URLs generated.
     */
    private String region;

    /**
     * AWS credentials to access to the S3 Bucket, if it is empty, it would use the IAM instance profile from the
     * jenkins hosts.
     */
    private String credentialsId;

    public CredentialsAwsGlobalConfiguration() {
        load();
    }

    public String getRegion() {
        return region;
    }

    @DataBoundSetter
    public void setRegion(String region) {
        this.region = Util.fixEmpty(region);
        checkValue(doCheckRegion(region));
        save();
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = StringUtils.defaultIfBlank(credentialsId, null);
        save();
    }

    public AmazonWebServicesCredentials getCredentials() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        Optional<AmazonWebServicesCredentials> credential = CredentialsProvider
                .lookupCredentials(AmazonWebServicesCredentials.class, Jenkins.get(), ACL.SYSTEM,
                        Collections.emptyList())
                .stream().filter(it -> it.getId().equals(credentialsId)).findFirst();
        if (credential.isPresent()) {
            return credential.get();
        } else {
            return null;
        }
    }

    /**
     *
     * @return true if an AWS credential is configured and the AWS credential exists.
     */
    private boolean hasCredentialsConfigured() {
        return StringUtils.isNotBlank(getCredentialsId()) && getCredentials() != null;
    }

    /**
     * create a AWS session credentials from a Key and a Secret configured in a AWS credential in Jenkins.
     * 
     * @return the AWS session credential result of the request to the AWS token service.
     */
    private AWSSessionCredentials sessionCredentialsFromKeyAndSecret() {
        AmazonWebServicesCredentials jenkinsAwsCredentials = getCredentials();
        AWSCredentials awsCredentials = jenkinsAwsCredentials.getCredentials();

        if (awsCredentials instanceof AWSSessionCredentials) {
            return (AWSSessionCredentials) awsCredentials;
        }

        AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(awsCredentials);
        AWSSecurityTokenServiceClientBuilder tokenSvcBuilder = AWSSecurityTokenServiceClientBuilder.standard()
                .withRegion(getRegion()).withCredentials(credentialsProvider);
        AWSSecurityTokenService tokenSvc = tokenSvcBuilder.build();

        GetSessionTokenRequest sessionTokenRequest = new GetSessionTokenRequest()
                .withDurationSeconds(getSessionDuration());
        GetSessionTokenResult sessionToken = tokenSvc.getSessionToken(sessionTokenRequest);
        com.amazonaws.services.securitytoken.model.Credentials credentials = sessionToken.getCredentials();

        return new BasicSessionCredentials(credentials.getAccessKeyId(), credentials.getSecretAccessKey(),
                credentials.getSessionToken());
    }

    /**
     * creates an AWS session credentials from the instance profile or user AWS configuration (~/.aws)
     * 
     * @return the AWS session credential from the instance profile or user AWS configuration.
     * @throws IOException
     *             in case ot error.
     */
    private AWSSessionCredentials sessionCredentialsFromInstanceProfile(AwsClientBuilder<?, ?> builder)
            throws IOException {
        AWSCredentials awsCredentials = builder.getCredentials().getCredentials();

        if (awsCredentials == null) {
            throw new IOException("Unable to get credentials from environment");
        }

        // Assume we are using session credentials
        if (!(awsCredentials instanceof AWSSessionCredentials)) {
            throw new IOException("No valid session credentials");
        }
        return (AWSSessionCredentials) awsCredentials;
    }

    /**
     * Select the type of AWS credential that has to be created based on the configuration. If no AWS credential is
     * provided, the IAM instance profile or user AWS configuration is used to create the AWS credentials.
     * 
     * @return A n AWS session credential.
     * @throws IOException
     *             in case of error.
     */
    public AWSSessionCredentials sessionCredentials(AwsClientBuilder<?, ?> builder) throws IOException {
        AWSSessionCredentials awsCredentials;
        if (ENDPOINT != null) {
            awsCredentials = new BasicSessionCredentials("FakeKey", "FakeSecret", "FakeToken");
        } else if (hasCredentialsConfigured()) {
            awsCredentials = sessionCredentialsFromKeyAndSecret();
        } else {
            awsCredentials = sessionCredentialsFromInstanceProfile(builder);
        }
        return awsCredentials;
    }

    private void checkValue(@NonNull FormValidation formValidation) {
        if (formValidation.kind == FormValidation.Kind.ERROR) {
            throw new Failure(formValidation.getMessage());
        }
    }

    public int getSessionDuration() {
        return SESSION_DURATION;
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return "Amazon S3 Bucket Access settings";
    }

    @Nonnull
    public static CredentialsAwsGlobalConfiguration get() {
        return ExtensionList.lookupSingleton(CredentialsAwsGlobalConfiguration.class);
    }

    public ListBoxModel doFillRegionItems() {
        ListBoxModel regions = new ListBoxModel();
        regions.add("Auto", "");
        for (Regions s : Regions.values()) {
            regions.add(s.getDescription(), s.getName());
        }
        return regions;
    }

    @RequirePOST
    public ListBoxModel doFillCredentialsIdItems() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        ListBoxModel credentials = new ListBoxModel();
        credentials.add("IAM instance Profile/user AWS configuration", "");
        credentials.addAll(
                CredentialsProvider.listCredentials(AmazonWebServicesCredentials.class, Jenkins.get(), ACL.SYSTEM,
                        Collections.emptyList(), CredentialsMatchers.instanceOf(AmazonWebServicesCredentials.class)));
        return credentials;
    }

    public FormValidation doCheckRegion(@QueryParameter String region) {
        if (StringUtils.isNotBlank(region)) {
            try {
                Regions.fromName(region);
            } catch (IllegalArgumentException x) {
                return FormValidation.error("Region is not valid");
            }
        }
        return FormValidation.ok();
    }

}
