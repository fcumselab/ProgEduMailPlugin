package io.jenkins.plugins.sample;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import fcu.selab.progextractor.data.ExtractFeedback;
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
import org.codehaus.jackson.map.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;

public class SendMailNotifier extends Notifier implements SimpleBuildStep {

    private final String studentEmail;
    private final String credentialsId;
    private final String smtpHost;
    private final int smtpPort;
    private final String assignmentType;
    private final MailCredentials credential;

    @DataBoundConstructor
    public SendMailNotifier(String studentEmail, String credentialsId, String smtpHost, int smtpPort, String assignmentType) {
        this.studentEmail = studentEmail;
        this.credentialsId = credentialsId;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.assignmentType = assignmentType;

        // Get all available credentials
        List<MailCredentials> credentials = CredentialsProvider.lookupCredentials(
                MailCredentials.class, Jenkins.getInstanceOrNull(), ACL.SYSTEM,
                Collections.<DomainRequirement>emptyList()
        );

        // Get the credential from the above list
        this.credential = CredentialsMatchers.firstOrNull(credentials,
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public String getAssignmentType() {
        return assignmentType;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public MailCredentials getCredential() {
        return credential;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException {
        String buildStatus = Objects.requireNonNull(run.getResult()).toString();

        String consoleText = String.join("\n", run.getLog(9999)); // Get full console text
        FeedbackInformation[] information = extractMessage(consoleText);
        sendMail(listener, buildStatus, information);
    }

    /**
     * Extract message that will send to student from full console text
     *
     * @param consoleText - Console text of the build.
     * @return - Extracted messages from the console text.
     */
    private FeedbackInformation[] extractMessage(String consoleText) {
        String status = null;

        // Find the build status such as utf, cpf
        Pattern pattern = Pattern.compile("WEB return value is :.*\"status\":\"(.*)\""); // Get build status
        Matcher matcher = pattern.matcher(consoleText);
        while (matcher.find()) {
            status = matcher.group(1);
        }

        // Extract the build information
        ExtractFeedback extractFeedback = new ExtractFeedback(this.assignmentType, status, consoleText);
        String feedback = extractFeedback.getFeedback();

        ObjectMapper mapper = new ObjectMapper();
        FeedbackInformation[] feedbackInformation = null;
        try {
            feedbackInformation = mapper.readValue(feedback, FeedbackInformation[].class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return feedbackInformation;
    }


    /**
     * Send mail to students.
     *
     * @param listener    - Listener of the build
     * @param buildStatus - SUCCESS or FAILURE
     * @param information - Extract information from console text
     */
    private void sendMail(TaskListener listener, String buildStatus, FeedbackInformation[] information) {
        String sender = this.credential.getEmailAccount(); // Sender's gmail
        String recipients = this.studentEmail; // Recipients' gmail

        // Setup mail ser
        // .++.ver
        Properties props = System.getProperties();
        props.put("mail.smtp.host", this.smtpHost);
        props.put("mail.smtp.port", this.smtpPort);
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.auth", "true");

        // Get the Session object and pass username & password
        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(sender, credential.getPassword().getPlainText());
            }
        });


        // Status abbreviation to full
        Map<String, String> status = new HashMap<>();
        status.put("ini", "Initial");
        status.put("bs", "Build Success");
        status.put("cpf", "Compile Failure");
        status.put("csf", "Coding Style Failure");
        status.put("utf", "Unit Test Failure");


        // Set up HTML mail content.
        // Since the HTML file will package in jar file, getResource method can't get it
        // Thus, we use getResourceAsStream method instead
        Scanner sca = new Scanner(SendMailNotifier.class.getResourceAsStream("MailContent.html"));

        StringBuilder htmlContent = new StringBuilder();
        while (sca.hasNextLine()) {
            htmlContent.append(sca.nextLine()).append("\n");
        }

        Document doc = Jsoup.parse(htmlContent.toString());
        Element tbody = doc.selectFirst("tbody");

        for (FeedbackInformation info : information) {
            Element tr = new Element("tr");
            tr.appendChild(new Element("td").text(info.getFileName()));
            tr.appendChild(new Element("td").text(info.getLine()));
            tr.appendChild(new Element("td").text(info.getMessage()));
            tr.appendChild(new Element("td").text(info.getSymptom()));
            tr.appendChild(new Element("td").text(info.getSuggest()));
            tbody.appendChild(tr);
        }


        try {
            // Set message of mail
            Message mailMessage = new MimeMessage(session);
            mailMessage.setFrom(new InternetAddress(sender, "ProgEdu"));
            mailMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(recipients));
            mailMessage.setSubject("ProgEdu檢測通知"); // Title of mail
            mailMessage.setContent(doc.toString(), "text/html");

            listener.getLogger().println("Sending mail to " + recipients);
            Transport.send(mailMessage); // Send message

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
        public FormValidation doCheckStudentEmail(@QueryParameter String studentEmail) {
            if (studentEmail.length() == 0) {
                return FormValidation.error(Messages.SendMailNotifier_DescriptorImpl_errors_missingEmail());
            } else if (studentEmail.matches("[\\w]+@[\\w]+(\\.[\\w]+)+")) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.SendMailNotifier_DescriptorImpl_errors_wrongFormat());
            }
        }

        public FormValidation doCheckSmtpHost(@QueryParameter String smtpHost) {
            if (smtpHost.matches("[\\w]+(\\.[\\w]+)+")) {
                return FormValidation.ok();
            }
            return FormValidation.error(Messages.SendMailNotifier_DescriptorImpl_errors_wrongSMTPFormat());
        }

        public FormValidation doCheckSmtpPort(@QueryParameter String smtpPort) {
            if (smtpPort.matches("[\\d]+")) {
                int port = Integer.parseInt(smtpPort);
                if (0 <= port && port <= 65535) {
                    return FormValidation.ok();
                }
            }
            return FormValidation.error(Messages.SendMailNotifier_DescriptorImpl_errors_wrongSMTPPort());
        }

        public FormValidation doCheckAssignmentType(@QueryParameter String assignmentType) {
            if (assignmentType.matches("(maven|java|android|web)(?i)")) {
                return FormValidation.ok();
            }
            return FormValidation.error(Messages.SendMailNotifier_DescriptorImpl_errors_wrongAssignmentType());
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String credentialsId) {
            if (!credentialsId.equals("")) {
                return FormValidation.ok();
            }
            return FormValidation.error(Messages.SendMailNotifier_DescriptorImpl_errors_emptyCredentialsId());
        }

        // List all credentials at the plugin options
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            return result
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM, Jenkins.get(),
                            MailCredentialsImpl.class)
                    .includeCurrentValue(credentialsId);
        }

        // List all credentials at the plugin options
        public ListBoxModel doFillAssignmentTypeItems(@QueryParameter String assignmentType) {
            StandardListBoxModel result = new StandardListBoxModel();
            return result
                    .includeEmptyValue()
                    .add("maven")
                    .add("java")
                    .add("android")
                    .add("web");
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