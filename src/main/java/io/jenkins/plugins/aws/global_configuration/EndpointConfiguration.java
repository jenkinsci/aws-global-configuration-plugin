package io.jenkins.plugins.aws.global_configuration;

import com.amazonaws.AmazonClientException;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClient;
import com.amazonaws.services.secretsmanager.model.ListSecretsRequest;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Failure;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.io.Serializable;

@Symbol("endpointConfiguration")
@Extension
public class EndpointConfiguration extends AbstractAwsGlobalConfiguration
        implements Serializable {
    private static final long serialVersionUID = 1L;

    private String serviceEndpoint;
    private String signingRegion;

    public EndpointConfiguration() {
        load();
    }

    public String getServiceEndpoint() {
        return serviceEndpoint;
    }

    public String getSigningRegion() {
        return signingRegion;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setServiceEndpoint(String serviceEndpoint) {
        this.serviceEndpoint = serviceEndpoint;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setSigningRegion(String signingRegion) {
        this.signingRegion = Util.fixEmpty(signingRegion);
        checkValue(doCheckRegion(signingRegion));
        save();
    }

    public ListBoxModel doFillSigningRegionItems() {
        return RegionUtils.fillRegionItems();
    }

    private void checkValue(@NonNull FormValidation formValidation) {
        if (formValidation.kind == FormValidation.Kind.ERROR) {
            throw new Failure(formValidation.getMessage());
        }
    }

    private FormValidation doCheckRegion(@QueryParameter String region) {
        return RegionUtils.checkRegion(region);
    }

    @Override
    public String toString() {
        return "Service Endpoint = " + serviceEndpoint + ", Signing Region = " + signingRegion;
    }

    @Nonnull
    public static EndpointConfiguration get() {
        return ExtensionList.lookupSingleton(EndpointConfiguration.class);
    }

    @Override
    @Nonnull
    public String getDisplayName() {
            return Messages.endpointConfiguration();
        }

    /**
     * Test the endpoint configuration.
     *
     * @param serviceEndpoint the AWS service endpoint e.g. http://localhost:4584
     * @param signingRegion the AWS signing region e.g. us-east-1
     * @return a success or failure indicator
     */
    @POST
    @SuppressWarnings("unused")
    public FormValidation doTestEndpointConfiguration(
            @QueryParameter("serviceEndpoint") final String serviceEndpoint,
            @QueryParameter("signingRegion") final String signingRegion) {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        final String r;
        if (signingRegion == null || signingRegion.isEmpty()) {
            r = getCurrentRegionOrDefault();
        } else {
            r = signingRegion;
        }
        final AwsClientBuilder.EndpointConfiguration ec =
               new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, r);
        // FIXME use a service-independent API call
        final AWSSecretsManager client =
                AWSSecretsManagerClient.builder().withEndpointConfiguration(ec).build();

        final int statusCode;
        try {
            statusCode = client.listSecrets(new ListSecretsRequest())
                    .getSdkHttpMetadata()
                    .getHttpStatusCode();
        } catch (AmazonClientException ex) {
            final String msg = Messages.awsClientError() + ": '" + ex.getMessage() + "'";
            return FormValidation.error(msg);
        }

        if ((statusCode >= 200) && (statusCode <= 399)) {
            return FormValidation.ok(Messages.success());
        } else {
            return FormValidation.error(Messages.awsServerError() + ": HTTP " + statusCode);
        }
    }

    private static String getCurrentRegionOrDefault() {
        final Region currentRegion = Regions.getCurrentRegion();

        if (currentRegion != null) {
            return currentRegion.getName();
        }

        return Regions.DEFAULT_REGION.getName();
    }
}
