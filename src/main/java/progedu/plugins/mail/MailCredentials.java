package progedu.plugins.mail;

import com.cloudbees.plugins.credentials.Credentials;
import hudson.util.Secret;

public interface MailCredentials extends Credentials {
  String getEmailAccount();

  Secret getPassword();

  String getSmtpHost();

  String getSmtpPort();

  String getDescription();
}
