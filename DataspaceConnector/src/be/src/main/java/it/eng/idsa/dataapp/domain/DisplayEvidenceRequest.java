package it.eng.idsa.dataapp.domain;

public class DisplayEvidenceRequest {
  private String suspectProfileID;
  private String requestorID;
  private String requestID;

  public DisplayEvidenceRequest() {
    super();
  }

  public DisplayEvidenceRequest(String suspectProfileID, String requestorID, String requestID) {
    super();
    this.suspectProfileID = suspectProfileID;
    this.requestorID = requestorID;
    this.requestID = requestID;
  }

  public String getSuspectProfileID() {
    return this.suspectProfileID;
  }

  public void setSuspectProfileID(String suspectProfileID) {
    this.suspectProfileID = suspectProfileID;
  }

  public String getRequestorID() {
    return this.requestorID;
  }

  public void setRequestorID(String requestorID) {
    this.requestorID = requestorID;
  }

  public String getRequestID() {
    return this.requestID;
  }

  public void setRequestID(String requestID) {
    this.requestID = requestID;
  }
}
