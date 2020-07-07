package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.Credentials;
import hudson.util.Secret;

public interface MailCredentials extends Credentials {
  String getEmailAccount();

  Secret getPassword();

  String getDescription();
}
