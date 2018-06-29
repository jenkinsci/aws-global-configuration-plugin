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

package io.jenkins.plugins.cloud_native.aws;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;

import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * @author Carlos Sanchez
 * @since
 *
 */
public class S3Config extends AbstractAws {

    private static final String BUCKET_REGEXP = "^([a-z]|(\\d(?!\\d{0,2}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})))([a-z\\d]|(\\.(?!(\\.|-)))|(-(?!\\.))){1,61}[a-z\\d\\.]$";
    private static final Pattern bucketPattern = Pattern.compile(BUCKET_REGEXP);

    private static final Logger LOGGER = Logger.getLogger(S3Config.class.getName());

    @SuppressWarnings("FieldMayBeFinal")
    private static boolean DELETE_ARTIFACTS = Boolean
            .getBoolean(CloudNativeAwsConfig.class.getName() + ".deleteArtifacts");
    @SuppressWarnings("FieldMayBeFinal")
    private static boolean DELETE_STASHES = Boolean.getBoolean(CloudNativeAwsConfig.class.getName() + ".deleteStashes");

    public S3Config(CloudNativeAwsConfig config) {
        super(config);
    }

    public boolean isDeleteArtifacts() {
        return DELETE_ARTIFACTS;
    }

    public boolean isDeleteStashes() {
        return DELETE_STASHES;
    }

    /**
     *
     * @return an AmazonS3Client using the configured region
     * @throws IOException
     */
    public AmazonS3 getAmazonS3Client() throws IOException {
        return getAmazonS3ClientBuilder().build();
    }

    /**
     *
     * @return an AmazonS3Client using the configured region
     * @throws IOException
     */
    public AmazonS3ClientBuilder getAmazonS3ClientBuilder() throws IOException {
        return getAmazonS3ClientBuilder(getConfig().getRegion());
    }

    /**
     *
     * @param region
     * @return an AmazonS3Client using the region or not, it depends if a region is configured or not.
     * @throws IOException
     */
    public AmazonS3ClientBuilder getAmazonS3ClientBuilder(String region) throws IOException {
        AmazonS3ClientBuilder ret = AmazonS3ClientBuilder.standard();
        if (CloudNativeAwsConfig.ENDPOINT != null) {
            ret = ret.withPathStyleAccessEnabled(true).withEndpointConfiguration(CloudNativeAwsConfig.ENDPOINT);
        } else if (StringUtils.isNotBlank(region)) {
            ret = ret.withRegion(region);
        } else {
            ret = ret.withForceGlobalBucketAccessEnabled(true);
        }
        AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
                getConfig().sessionCredentials(ret));
        return ret.withCredentials(credentialsProvider);
    }

    public FormValidation doCheckContainer(@QueryParameter String container) {
        FormValidation ret = FormValidation.ok();
        if (StringUtils.isBlank(container)) {
            ret = FormValidation.warning("The S3 Bucket name cannot be empty");
        } else if (!bucketPattern.matcher(container).matches()) {
            ret = FormValidation.error("The S3 Bucket name does not match S3 bucket rules");
        }
        return ret;
    }

    public FormValidation doCheckPrefix(@QueryParameter String prefix) {
        FormValidation ret;
        if (StringUtils.isBlank(prefix)) {
            ret = FormValidation.ok("Artifacts will be stored in the root folder of the S3 Bucket.");
        } else if (prefix.endsWith("/")) {
            ret = FormValidation.ok();
        } else {
            ret = FormValidation.error("A prefix must end with a slash.");
        }
        return ret;
    }

    @RequirePOST
    public FormValidation doCreateS3Bucket(@QueryParameter String container, @QueryParameter String prefix,
            @QueryParameter String region, @QueryParameter String credentialsId) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        FormValidation ret = FormValidation.ok("success");
        try {
            createS3Bucket(container, region, credentialsId);
        } catch (Throwable t) {
            String msg = processExceptionMessage(t);
            ret = FormValidation.error(StringUtils.abbreviate(msg, 200));
        }
        return ret;
    }

    /**
     * create an S3 Bucket.
     * 
     * @param name
     *            name of the S3 Bucket.
     * @param credentialsId
     * @param region
     * @return return the Bucket created.
     * @throws IOException
     *             in case of error obtaining the credentials, in other kind of errors it will throw the runtime
     *             exceptions are thrown by createBucket method.
     */
    public Bucket createS3Bucket(String name, String region, String credentialsId) throws IOException {
        return getAmazonS3ClientBuilder(region).build().createBucket(name);
    }

    public FormValidation validate(String container, String prefix, String region, String credentialsId) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        FormValidation ret = FormValidation.ok("success");
        try {
            getAmazonS3ClientBuilder(region).build().listObjects(container, prefix);
        } catch (Throwable t) {
            String msg = processExceptionMessage(t);
            ret = FormValidation.error(StringUtils.abbreviate(msg, 200));
        }
        return ret;
    }

}
