package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

// Name provider for this credential
@NameWith(value = MailCredentialsNameProvider.class)

public class MailCredentialsImpl extends BaseStandardCredentials implements MailCredentials {
    private final String username;
    private final Secret password;
    private final String description;

    @DataBoundConstructor
    public MailCredentialsImpl(String id, String description, String username, String password) {
        super(id, description);
        this.username = username;
        this.password = Secret.fromString(password);
        this.description = description;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public Secret getPassword() {
        return password;
    }

    @Extension
    public static class Descriptor extends CredentialsDescriptor{
        public String getDisplayName(){
            return "Email Credentials";
        }
    }
}
