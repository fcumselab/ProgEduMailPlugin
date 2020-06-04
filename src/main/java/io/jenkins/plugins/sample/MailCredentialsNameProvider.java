package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import edu.umd.cs.findbugs.annotations.NonNull;

public class MailCredentialsNameProvider extends CredentialsNameProvider<MailCredentials> {
    @NonNull
    @Override
    public String getName(@NonNull MailCredentials mailCredentials) {
        return mailCredentials.getUsername();
    }
}
