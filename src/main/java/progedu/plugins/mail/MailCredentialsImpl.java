package progedu.plugins.mail;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

// Show "ProgEdu Mail" at name column on credentials page
@NameWith(value = MailCredentialsNameProvider.class)

public class MailCredentialsImpl extends BaseStandardCredentials implements MailCredentials {
  private final String emailAccount;
  private final Secret password;
  private final String smtpHost;
  private final String smtpPort;
  private final String description;
  private static final String kind = "Email Credential";

  /**
   * Constructor.
   *
   * @param id           - Credential's ID
   * @param description  - Credential's description
   * @param emailAccount - Email account
   * @param password     - Email password
   * @param smtpHost     - SMTP host
   * @param smtpPort     - SMTP port
   */
  @DataBoundConstructor
  public MailCredentialsImpl(String id, String description, String emailAccount, String password,
                             String smtpHost, String smtpPort) {
    super(id, description);
    this.emailAccount = emailAccount;
    this.password = Secret.fromString(password);
    this.smtpHost = smtpHost;
    this.smtpPort = smtpPort;
    this.description = description;
  }

  @Override
  public String getEmailAccount() {
    return emailAccount;
  }

  @Override
  public Secret getPassword() {
    return password;
  }

  @Override
  public String getSmtpHost() {
    return smtpHost;
  }

  @Override
  public String getSmtpPort() {
    return smtpPort;
  }

  @Extension
  public static class Descriptor extends CredentialsDescriptor {
    public String getDisplayName() {
      return MailCredentialsImpl.kind;
    }
  }
}
