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

import edu.umd.cs.findbugs.annotations.NonNull;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
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
import com.amazonaws.services.securitytoken.model.Credentials;
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
@Symbol("awsCredentials")
@Extension
public final class CredentialsAwsGlobalConfiguration extends AbstractAwsGlobalConfiguration {

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

    /**
     * Testing only
     */
    @Restricted(NoExternalUse.class)
    protected CredentialsAwsGlobalConfiguration(boolean test) {
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
        return getCredentials(credentialsId);
    }

    public AmazonWebServicesCredentials getCredentials(String credentialsId) {
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
    private boolean hasCredentialsConfigured(String credentialsId) {
        return StringUtils.isNotBlank(credentialsId) && getCredentials(credentialsId) != null;
    }

    /**
     * create a AWS session credentials from a Key and a Secret configured in a AWS credential in Jenkins.
     * 
     * @return the AWS session credential result of the request to the AWS token service.
     */
    private AWSSessionCredentials sessionCredentialsFromKeyAndSecret(String region, String credentialsId) {
        AmazonWebServicesCredentials jenkinsAwsCredentials = getCredentials(credentialsId);
        AWSCredentials awsCredentials = jenkinsAwsCredentials.getCredentials();

        if (awsCredentials instanceof AWSSessionCredentials) {
            return (AWSSessionCredentials) awsCredentials;
        }

        AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(awsCredentials);
        com.amazonaws.services.securitytoken.model.Credentials credentials = getSessionCredentials(credentialsProvider,
                region);

        return new BasicSessionCredentials(credentials.getAccessKeyId(), credentials.getSecretAccessKey(),
                credentials.getSessionToken());
    }

    private Credentials getSessionCredentials(AWSCredentialsProvider credentialsProvider, String region) {
        AWSSecurityTokenServiceClientBuilder tokenSvcBuilder = AWSSecurityTokenServiceClientBuilder.standard()
                .withCredentials(credentialsProvider);
        if (region != null) {
            tokenSvcBuilder.withRegion(region);
        }
        AWSSecurityTokenService tokenSvc = tokenSvcBuilder.build();

        GetSessionTokenRequest sessionTokenRequest = new GetSessionTokenRequest()
                .withDurationSeconds(getSessionDuration());
        GetSessionTokenResult sessionToken = tokenSvc.getSessionToken(sessionTokenRequest);
        return sessionToken.getCredentials();
    }

    /**
     * creates an AWS session credentials from the instance profile or user AWS configuration (~/.aws)
     * 
     * @return the AWS session credential from the instance profile or user AWS configuration.
     * @throws IOException
     *             in case ot error.
     */
    private AWSSessionCredentials sessionCredentialsFromInstanceProfile(@NonNull AwsClientBuilder<?, ?> builder)
            throws IOException {
        AWSCredentialsProvider credentialsProvider = builder.getCredentials();
        if (credentialsProvider == null) {
            throw new IOException("This client builder has no associated credentials");
        }
        AWSCredentials awsCredentials = credentialsProvider.getCredentials();

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
     * Use {@link #sessionCredentials(AwsClientBuilder, String, String)}
     */
    @Deprecated
    public AWSSessionCredentials sessionCredentials(@NonNull AwsClientBuilder<?, ?> builder) throws IOException {
        return sessionCredentials(builder, this.getRegion(), this.getCredentialsId());
    }

    /**
     * Select the type of AWS credential that has to be created based on the configuration. If no AWS credential is
     * provided, the IAM instance profile or user AWS configuration is used to create the AWS credentials.
     * 
     * @return An AWS session credential.
     * @throws IOException
     *             in case of error.
     */
    public AWSSessionCredentials sessionCredentials(@NonNull AwsClientBuilder<?, ?> builder, String region,
            String credentialsId) throws IOException {
        AWSSessionCredentials awsCredentials;
        if (hasCredentialsConfigured(credentialsId)) {
            awsCredentials = sessionCredentialsFromKeyAndSecret(region, credentialsId);
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

    @NonNull
    @Override
    public String getDisplayName() {
        return "Amazon S3 Bucket Access settings";
    }

    @NonNull
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
