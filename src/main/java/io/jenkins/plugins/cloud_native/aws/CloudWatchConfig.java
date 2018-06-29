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

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.FilterLogEventsRequest;

import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * @author Carlos Sanchez
 * @since
 *
 */
public class CloudWatchConfig extends AbstractAws {

    private static final Logger LOGGER = Logger.getLogger(CloudWatchConfig.class.getName());

    public CloudWatchConfig(CloudNativeAwsConfig config) {
        super(config);
    }

    /**
     *
     * @return an AWSLogsClientBuilder using the configured region
     * @throws IOException
     */
    public AWSLogsClientBuilder getAWSLogsClientBuilder() throws IOException {
        return getAWSLogsClientBuilder(getConfig().getRegion());
    }

    /**
     *
     * @return an AWSLogsClientBuilder using the passed region
     * @throws IOException
     */
    private AWSLogsClientBuilder getAWSLogsClientBuilder(String region) throws IOException {
        AWSLogsClientBuilder builder = AWSLogsClientBuilder.standard();
        if (StringUtils.isNotBlank(region)) {
            builder = builder.withRegion(region);
        }
        AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
                getConfig().sessionCredentials(builder));
        return builder.withCredentials(credentialsProvider);
    }

    public FormValidation doCheckLogGroupName(@QueryParameter String logGroup) {
        FormValidation ret = FormValidation.ok();
        if (StringUtils.isBlank(logGroup)) {
            ret = FormValidation.warning("The log group name cannot be empty");
        }
        return ret;
    }

    public FormValidation validate(String logGroupName, String region, String credentialsId) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        AWSLogs client = getAWSLogsClientBuilder(region).build();

        FormValidation ret = FormValidation.ok("success");
        try {
            FilterLogEventsRequest request = new FilterLogEventsRequest();
            request.setLogGroupName(logGroupName);
            client.filterLogEvents(request);
        } catch (Throwable t) {
            String msg = processExceptionMessage(t);
            ret = FormValidation.error(StringUtils.abbreviate(msg, 200));
        }
        return ret;
    }
}
