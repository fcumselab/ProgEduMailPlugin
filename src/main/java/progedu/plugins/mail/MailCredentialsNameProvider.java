package progedu.plugins.mail;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import edu.umd.cs.findbugs.annotations.NonNull;

public class MailCredentialsNameProvider extends CredentialsNameProvider<MailCredentials> {
  @NonNull
  @Override
  public String getName(@NonNull MailCredentials mailCredentials) {
    return "ProgEdu Mail"; // Show gmail address at name column on credentials page
  }
}
