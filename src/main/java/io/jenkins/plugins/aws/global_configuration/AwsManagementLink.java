package io.jenkins.plugins.aws.global_configuration;

import com.google.common.base.Predicate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.util.FormApply;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

/**
 * @author Oleg Nenashev
 * @since TODO
 */
@Extension
@Symbol("aws")
public class AwsManagementLink extends ManagementLink implements Describable<AwsManagementLink> {

    private static Logger LOGGER = Logger.getLogger(AwsManagementLink.class.getName());

    @CheckForNull
    @Override
    public String getUrlName() {
        return "aws";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "AWS";
    }

    @Override
    public String getIconFileName() {
        return "symbol-aws-icon-solid plugin-oss-symbols-api";
    }

    @Override
    public String getDescription() {
        return "Amazon Web Services Configuration";
    }

    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @Override
    public Descriptor<AwsManagementLink> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(AwsManagementLink.class);
    }

    @POST
    public synchronized void doConfigure(StaplerRequest2 req, StaplerResponse2 rsp)
            throws IOException, ServletException, Descriptor.FormException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        // for compatibility reasons, the actual value is stored in Jenkins
        BulkChange bc = new BulkChange(Jenkins.get());
        try {
            boolean result = configure(req, req.getSubmittedForm());
            LOGGER.log(Level.FINE, "security saved: " + result);
            Jenkins.get().save();
            FormApply.success(req.getContextPath() + "/manage").generateResponse(req, rsp, null);
        } finally {
            bc.commit();
        }
    }

    public boolean configure(StaplerRequest2 req, JSONObject json) throws Descriptor.FormException {
        boolean result = true;
        for (Descriptor<?> d : Functions.getSortedDescriptorsForGlobalConfigByDescriptor(
                descriptor -> FILTER.apply(descriptor.getCategory()))) {
            result &= configureDescriptor(req, json, d);
        }

        return result;
    }

    private boolean configureDescriptor(StaplerRequest2 req, JSONObject json, Descriptor<?> d)
            throws Descriptor.FormException {
        // collapse the structure to remain backward compatible with the JSON structure before 1.
        String name = d.getJsonSafeClassName();
        // if it doesn't have the property, the method returns invalid null object.
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject();
        json.putAll(js);
        return d.configure(req, js);
    }

    public static final Predicate<GlobalConfigurationCategory> FILTER = new Predicate<GlobalConfigurationCategory>() {
        @Override
        public boolean apply(GlobalConfigurationCategory input) {
            return input instanceof AwsGlobalConfigurationCategory;
        }
    };

    @Extension
    @Symbol("aws")
    public static final class DescriptorImpl extends Descriptor<AwsManagementLink> {
        @Override
        public String getDisplayName() {
            return "AWS";
        }
    }
}
