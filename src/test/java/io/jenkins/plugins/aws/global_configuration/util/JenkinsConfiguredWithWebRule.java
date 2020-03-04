package io.jenkins.plugins.aws.global_configuration.util;

import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.function.Consumer;

public class JenkinsConfiguredWithWebRule extends JenkinsRule {

    private final String path;

    public JenkinsConfiguredWithWebRule(String path) {
        this.path = path;
    }

    /**
     * Load the standard "/configure" page.
     */
    public static JenkinsConfiguredWithWebRule standard() {
        return new JenkinsConfiguredWithWebRule("configure");
    }

    public void configure(Consumer<HtmlForm> configurator) {
        final JenkinsRule.WebClient webClient = super.createWebClient();

        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        webClient.getOptions().setCssEnabled(false);

        try {
            final HtmlPage p = webClient.goTo(path);

            final HtmlForm form = p.getFormByName("config");

            configurator.accept(form);

            super.submit(form);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to configure Jenkins", ex);
        }
    }
}
