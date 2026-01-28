package hudson.security.GlobalSecurityConfiguration

import hudson.security.SecurityRealm
import hudson.markup.MarkupFormatterDescriptor
import hudson.security.AuthorizationStrategy
import jenkins.AgentProtocol
import jenkins.model.GlobalConfiguration
import hudson.Functions
import hudson.model.Descriptor

def f=namespace(lib.FormTagLib)
def l=namespace(lib.LayoutTagLib)
def st=namespace("jelly:stapler")

l.'settings-subpage'(permission:app.ADMINISTER) {
    f.form(method:"post",name:"config",action:"configure") {
        set("instance",my);
        set("descriptor", my.descriptor);

        Functions.getSortedDescriptorsForGlobalConfig(my.FILTER).each { Descriptor descriptor ->
            set("descriptor",descriptor)
            set("instance",descriptor)
            f.rowSet(name:descriptor.jsonSafeClassName) {
                st.include(from:descriptor, page:descriptor.globalConfigPage)
            }
        }

        f.saveApplyBar()
    }

    st.adjunct(includes: "lib.form.confirm")
}
