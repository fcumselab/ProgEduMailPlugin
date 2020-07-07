package io.jenkins.plugins.sample;

public class FeedbackInformation {
  private String status;
  private String fileName;
  private String line;
  private String message;
  private String symptom;
  private String suggest;

  public String getStatus() {
    return status;
  }

  public String getFileName() {
    return fileName;
  }

  public String getLine() {
    return line;
  }

  public String getMessage() {
    return message;
  }

  public String getSymptom() {
    return symptom;
  }

  public String getSuggest() {
    return suggest;
  }
}