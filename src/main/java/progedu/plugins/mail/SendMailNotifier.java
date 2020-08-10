package progedu.plugins.mail;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.gson.Gson;
import fcu.selab.progextractor.data.ExtractFeedback;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class SendMailNotifier extends Notifier implements SimpleBuildStep {

  private static final int INIT_COMMIT = 1;
  private final String studentEmail;
  private final String credentialsId;
  private final String releaseTime;
  private final String assignmentType;
  private MailCredentials credential;
  private String mailTitle;

  /**
   * Constructor.
   *
   * @param studentEmail   - Student Email
   * @param credentialsId  - Credentials ID
   * @param assignmentType - Assignment Type
   */
  @DataBoundConstructor
  public SendMailNotifier(
          String studentEmail, String credentialsId, String releaseTime, String assignmentType) {
    this.studentEmail = studentEmail;
    this.credentialsId = credentialsId;
    this.releaseTime = releaseTime;
    this.assignmentType = assignmentType;

    setCredentialFromID();
  }

  public String getStudentEmail() {
    return studentEmail;
  }

  public String getReleaseTime() {
    return this.releaseTime;
  }

  public String getAssignmentType() {
    return assignmentType;
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

    setCredentialFromID();

    Document mailContent = null;
    int buildNumber = run.number;
    listener.getLogger().println(
            "--------------------------SendMailNotifier--------------------------");
    if (buildNumber == INIT_COMMIT) { // Initial commit
      mailContent = getInitMailContent();
    } else {
      String consoleText = String.join("\n", run.getLog(9999)); // Get full console text
      String buildStatus = getBuildStatus(consoleText);

      FeedbackInformation[] information = extractMessage(buildStatus, consoleText);
      mailContent = getMailContent(buildStatus, buildNumber, information);
    }
    sendMail(listener, mailContent);

    listener.getLogger().println(
            "--------------------------SendMailNotifier--------------------------");
  }

  /**
   * Get credential from credential ID.
   */
  public void setCredentialFromID() {
    // Get all available credentials
    List<MailCredentials> credentials = CredentialsProvider.lookupCredentials(
            MailCredentials.class, Jenkins.getInstanceOrNull(), ACL.SYSTEM,
            Collections.<DomainRequirement>emptyList()
    );

    // Get the credential from the above list
    this.credential = CredentialsMatchers.firstOrNull(credentials,
            CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
  }

  /**
   * Find the build status such as utf, cpf.
   *
   * @param consoleText - Full console text.
   * @return buildStatus - Build status such as utf, cpf.
   */
  private String getBuildStatus(String consoleText) {
    String buildStatus = "";
    Pattern pattern = Pattern.compile("WEB return value is :.*\"status\":\"(.*)\"");
    Matcher matcher = pattern.matcher(consoleText);
    while (matcher.find()) {
      buildStatus = matcher.group(1);
    }
    return buildStatus;
  }

  /**
   * Extract message that will send to student from full console text.
   *
   * @param buildStatus - Build status such as utf, cpf.
   * @param consoleText - Console text of the build.
   * @return - Extracted messages from the console text.
   */
  private FeedbackInformation[] extractMessage(String buildStatus, String consoleText) {
    // Extract the build information
    ExtractFeedback extractFeedback = new ExtractFeedback(
            this.assignmentType, buildStatus, consoleText);
    String feedback = extractFeedback.getFeedback();

    Gson gson = new Gson();
    return gson.fromJson(feedback, FeedbackInformation[].class);
  }

  /**
   * Get initial HTML mail content
   *
   * @return HTML content of mail
   */
  private Document getInitMailContent() throws IOException {
    Document doc = Jsoup.parse(getClass().getResourceAsStream("InitMailContent.html"),
            "UTF-8", System.getProperty("user.dir"));

    doc.selectFirst("#release-time").text(this.releaseTime);
    this.mailTitle = "ProgEdu作業通知";
    return doc;
  }

  /**
   * Set up HTML mail content.
   *
   * @param buildStatus - Build status such as utf, cpf.
   * @param buildNumber - The build number.
   * @param information - Extract information from console text.
   * @return doc - HTML content of mail.
   */
  private Document getMailContent(String buildStatus, int buildNumber,
                                    FeedbackInformation[] information) throws IOException {

    // Status abbreviation to full
    Map<String, String> statusMap = new HashMap<>();
    statusMap.put("ini", "Initial");
    statusMap.put("bs", "Build Success");
    statusMap.put("cpf", "Compile Failure");
    statusMap.put("csf", "Coding Style Failure");
    statusMap.put("utf", "Unit Test Failure");
    statusMap.put("uitf", "UI Test Failure");
    statusMap.put("whf", "HTML Failure");
    statusMap.put("wsf", "CSS Failure");
    statusMap.put("wef", "JavaScript Failure");

    String fullBuildStatus = statusMap.get(buildStatus);

    // Since the HTML file will package in a jar file, "getResource" method can't get it.
    // Thus, we use "getResourceAsStream" method instead.
    Document doc = Jsoup.parse(getClass().getResourceAsStream("MailContent.html"),
            "UTF-8", System.getProperty("user.dir"));

    doc.selectFirst("#build-number").text(String.valueOf(buildNumber));
    doc.selectFirst("#build-status").text(fullBuildStatus).addClass(buildStatus);

    Element tbody = doc.selectFirst("tbody");
    for (FeedbackInformation info : information) {
      Element tr = new Element("tr");
      tr.appendChild(new Element("td").text(info.getFileName()));
      tr.appendChild(new Element("td").text(info.getLine()));
      tr.appendChild(new Element("td").text(info.getMessage()));
      tr.appendChild(new Element("td").text(info.getSymptom()));
      tbody.appendChild(tr);
    }
    this.mailTitle = "ProgEdu檢測通知";
    return doc;
  }

  /**
   * Send mail to students.
   *
   * @param listener - Listener of the build
   * @param doc      - Mail content that is HTML
   */
  private void sendMail(TaskListener listener, Document doc) {
    String sender = this.credential.getEmailAccount(); // Sender's gmail
    String recipients = this.studentEmail; // Recipients' gmail

    // Setup mail server
    Properties props = System.getProperties();
    props.put("mail.smtp.host", this.credential.getSmtpHost());
    props.put("mail.smtp.port", this.credential.getSmtpPort());
    props.put("mail.smtp.ssl.enable", "true");
    props.put("mail.smtp.auth", "true");

    // Get the Session object and pass username & password
    Session session = Session.getInstance(props, new javax.mail.Authenticator() {
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(sender, credential.getPassword().getPlainText());
      }
    });

    try {
      // Set message of mail
      Message mailMessage = new MimeMessage(session);
      mailMessage.setFrom(new InternetAddress(sender, "ProgEdu"));
      mailMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(recipients));
      mailMessage.setSubject(this.mailTitle); // Title of mail
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
    /**
     * Validation for student email.
     *
     * @param studentEmail - Student Email
     * @return - If student's email is valid or not
     */
    public FormValidation doCheckStudentEmail(@QueryParameter String studentEmail) {
      if (studentEmail.length() == 0) {
        return FormValidation.error(Messages.SendMailNotifier_DescriptorImpl_errors_missingEmail());
      } else if (studentEmail.matches("[\\w]+@[\\w]+(\\.[\\w]+)+")) {
        return FormValidation.ok();
      } else {
        return FormValidation.error(Messages.SendMailNotifier_DescriptorImpl_errors_wrongFormat());
      }
    }

    /**
     * Validation for assignment type.
     *
     * @param assignmentType - Assignment type (e.g. maven, web)
     * @return - If assignment type is valid or not
     */
    public FormValidation doCheckAssignmentType(@QueryParameter String assignmentType) {
      if (assignmentType.matches("(maven|javac|android|web)(?i)")) {
        return FormValidation.ok();
      }
      return FormValidation.error(
              Messages.SendMailNotifier_DescriptorImpl_errors_wrongAssignmentType());
    }

    /**
     * Validation for credentials ID.
     *
     * @param credentialsId - Credentials ID
     * @return - If credentials ID is valid or not
     */
    public FormValidation doCheckCredentialsId(@QueryParameter String credentialsId) {
      if (!credentialsId.equals("")) {
        return FormValidation.ok();
      }
      return FormValidation.error(
              Messages.SendMailNotifier_DescriptorImpl_errors_emptyCredentialsId());
    }

    /**
     * List and fill all credentials at the plugin options.
     *
     * @param credentialsId - Credentials ID.
     * @return - All credentials.
     */
    public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
      StandardListBoxModel result = new StandardListBoxModel();
      return result
              .includeEmptyValue()
              .includeAs(ACL.SYSTEM, Jenkins.get(),
                      MailCredentialsImpl.class)
              .includeCurrentValue(credentialsId);
    }


    /**
     * List and fill all credentials at the plugin options.
     *
     * @param assignmentType - Assignment type
     * @return - All assignment types.
     */
    public ListBoxModel doFillAssignmentTypeItems(@QueryParameter String assignmentType) {
      StandardListBoxModel result = new StandardListBoxModel();
      return result
              .includeEmptyValue()
              .add("maven")
              .add("javac")
              .add("android")
              .add("web");
    }


    @Override
    public boolean isApplicable(Class<? extends AbstractProject> oneClass) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return Messages.SendMailNotifier_DescriptorImpl_DisplayName();
    }
  }
}