package io.jenkins.plugins.aws.global_configuration;

import com.amazonaws.regions.Regions;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;

public class RegionUtils {
    public static ListBoxModel fillRegionItems() {
        ListBoxModel regions = new ListBoxModel();
        regions.add("Auto", "");
        for (Regions s : Regions.values()) {
            regions.add(s.getDescription(), s.getName());
        }
        return regions;
    }

    public static FormValidation checkRegion(@QueryParameter String region) {
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
