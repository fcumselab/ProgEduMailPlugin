package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.Credentials;
import hudson.util.Secret;

public interface MailCredentials extends Credentials {
    String getUsername();
    Secret getPassword();
    String getDescription();
}
