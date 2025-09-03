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

import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Failure;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.RegionMetadata;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.GetSessionTokenRequest;
import software.amazon.awssdk.services.sts.model.GetSessionTokenResponse;

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
    private static int SESSION_DURATION =
            Integer.getInteger(CredentialsAwsGlobalConfiguration.class.getName() + ".sessionDuration", 3600);

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
    protected CredentialsAwsGlobalConfiguration(boolean test) {}

    public String getRegion() {
        return region;
    }

    @DataBoundSetter
    public void setRegion(String region) {
        this.region = Util.fixEmpty(region);
        checkValue(doCheckRegion(region));
        save();
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = StringUtils.defaultIfBlank(credentialsId, null);
        save();
    }

    @CheckForNull
    public AmazonWebServicesCredentials getCredentials() {
        return credentialsId != null ? getCredentials(credentialsId) : null;
    }

    @CheckForNull
    public AmazonWebServicesCredentials getCredentials(@NonNull String credentialsId) {
        Optional<AmazonWebServicesCredentials> credential = CredentialsProvider.lookupCredentials(
                        AmazonWebServicesCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.emptyList())
                .stream()
                .filter(it -> it.getId().equals(credentialsId))
                .findFirst();
        if (credential.isPresent()) {
            return credential.get();
        } else {
            return null;
        }
    }

    /**
     * create a AWS session credentials from a Key and a Secret configured in a AWS credential in Jenkins.
     *
     * @return the AWS session credential result of the request to the AWS token service.
     */
    private AwsSessionCredentials sessionCredentialsFromKeyAndSecret(
            String region, @NonNull AmazonWebServicesCredentials jenkinsAwsCredentials) {
        AwsCredentials awsCredentials = jenkinsAwsCredentials.resolveCredentials();

        if (awsCredentials instanceof AwsSessionCredentials) {
            return (AwsSessionCredentials) awsCredentials;
        }

        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(awsCredentials);
        software.amazon.awssdk.services.sts.model.Credentials credentials =
                getSessionCredentials(credentialsProvider, region);

        return AwsSessionCredentials.create(
                credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken());
    }

    private Credentials getSessionCredentials(AwsCredentialsProvider credentialsProvider, String region) {
        StsClientBuilder stsClientBuilder = StsClient.builder().credentialsProvider(credentialsProvider);
        if (region != null) {
            stsClientBuilder.region(Region.of(region));
        }
        StsClient stsClient = stsClientBuilder.build();

        GetSessionTokenRequest sessionTokenRequest = GetSessionTokenRequest.builder()
                .durationSeconds(getSessionDuration())
                .build();
        GetSessionTokenResponse sessionToken = stsClient.getSessionToken(sessionTokenRequest);
        return sessionToken.credentials();
    }

    /**
     * creates an AWS session credentials from the instance profile or user AWS configuration (~/.aws)
     *
     * @return the AWS session credential from the instance profile or user AWS configuration.
     * @throws IOException
     *             in case of error.
     */
    private AwsSessionCredentials sessionCredentialsFromInstanceProfile() throws IOException {
        DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
        AwsCredentials awsCredentials = credentialsProvider.resolveCredentials();

        // Assume we are using session credentials
        if (!(awsCredentials instanceof AwsSessionCredentials)) {
            throw new IOException("No valid session credentials");
        }
        return (AwsSessionCredentials) awsCredentials;
    }

    /**
     * Select the type of AWS credential that has to be created based on the configuration. If no AWS credential is
     * provided, the IAM instance profile or user AWS configuration is used to create the AWS credentials.
     *
     * @return An AWS session credential.
     * @throws IOException
     *             in case of error.
     */
    public AwsSessionCredentials sessionCredentials(String region, String credentialsId) throws IOException {
        AmazonWebServicesCredentials baseCredentials =
                StringUtils.isNotBlank(credentialsId) ? getCredentials(credentialsId) : null;
        if (baseCredentials != null) {
            return sessionCredentialsFromKeyAndSecret(region, baseCredentials);
        } else {
            return sessionCredentialsFromInstanceProfile();
        }
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
        for (Region s : Region.regions()) {
            RegionMetadata regionMetadata = RegionMetadata.of(s);
            regions.add(regionMetadata != null ? regionMetadata.description() : s.id(), s.id());
        }
        return regions;
    }

    @RequirePOST
    public ListBoxModel doFillCredentialsIdItems() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        ListBoxModel credentials = new ListBoxModel();
        credentials.add("IAM instance Profile/user AWS configuration", "");
        credentials.addAll(CredentialsProvider.listCredentials(
                AmazonWebServicesCredentials.class,
                Jenkins.get(),
                ACL.SYSTEM,
                Collections.emptyList(),
                CredentialsMatchers.instanceOf(AmazonWebServicesCredentials.class)));
        return credentials;
    }

    public FormValidation doCheckRegion(@QueryParameter String region) {
        if (StringUtils.isNotBlank(region)) {
            if (Region.regions().stream().noneMatch(r -> r.id().equals(region))) {
                return FormValidation.error("Region is not valid");
            }
        }
        return FormValidation.ok();
    }
}
