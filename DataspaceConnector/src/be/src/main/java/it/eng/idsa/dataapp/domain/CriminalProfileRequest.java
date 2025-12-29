package it.eng.idsa.dataapp.domain;

public class CriminalProfileRequest {
  private String suspectProfileID;
  private String providerID;

  public CriminalProfileRequest() {
    super();
  }

  public CriminalProfileRequest(String suspectProfileID, String providerID) {
    super();
    this.suspectProfileID = suspectProfileID;
    this.providerID = providerID;
  }

  public String getSuspectProfileID() {
    return this.suspectProfileID;
  }

  public void setSuspectProfileID(String suspectProfileID) {
    this.suspectProfileID = suspectProfileID;
  }

  public String getProviderID() {
    return this.providerID;
  }

  public void setProviderID(String providerID) {
    this.providerID = providerID;
  }
}
