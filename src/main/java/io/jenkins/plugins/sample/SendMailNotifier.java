package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.security.ACL;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;

public class SendMailNotifier extends Notifier implements SimpleBuildStep {

    private final String studentEmail;
    private final String credentialsId;
    private final MailCredentials credential;

    @DataBoundConstructor
    public SendMailNotifier(String studentEmail, String credentialsId) {
        this.studentEmail = studentEmail;
        this.credentialsId = credentialsId;

        List<MailCredentials> credentials = CredentialsProvider.lookupCredentials(
                MailCredentials.class, Jenkins.getInstanceOrNull(), ACL.SYSTEM,
                Collections.<DomainRequirement> emptyList()
        );

        MailCredentials credential = CredentialsMatchers.firstOrNull(credentials,
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));

        this.credential = credential;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        sendMail(listener);
    }

    private void sendMail(TaskListener listener) {
        String sender = credential.getUsername(); // Sender's gmail
        String recipients = studentEmail; // Recipients' gmail
        String host = "smtp.gmail.com";

        // Setup mail server
        Properties props = System.getProperties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", "465");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.auth", "true");

        // Get the Session object and pass username and password
        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(sender, credential.getPassword().getPlainText());
            }
        });

        // Used to debug SMTP issues
        // session.setDebug(true);

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(sender, "ProgEdu"));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipients));
            message.setSubject("ProgEdu Test Result"); // Title of mail
            message.setText("ProgEdu Test at " + new Date().toString()); // Content of mail

            listener.getLogger().println("Sending mail to " + sender);
            Transport.send(message); // Send message

            listener.getLogger().println("Sent message successfully....");
        } catch (MessagingException | UnsupportedEncodingException mex) {
            listener.getLogger().println("Sent message fail");
            mex.printStackTrace();
        }
    }

    // Call this plugin after build complete
    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public FormValidation doCheckEmail(@QueryParameter String email) {
            if (email.length() == 0) {
                return FormValidation.error(Messages.SendMailNotifier_DescriptorImpl_errors_missingEmail());
            } else if (email.matches("[\\w]+@[\\w]+(\\.[\\w]+)+")) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.SendMailNotifier_DescriptorImpl_errors_wrongFormat());
            }
        }

        // List all credentials at the plugin options
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter("CredentialsId") String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            return result
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM, Jenkins.get(),
                            MailCredentialsImpl.class)
                    .includeCurrentValue(credentialsId);
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String credentialsId) {
            return FormValidation.ok();
        }


        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.SendMailNotifier_DescriptorImpl_DisplayName();
        }
    }
}