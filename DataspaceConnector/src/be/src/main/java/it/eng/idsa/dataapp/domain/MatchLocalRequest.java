package it.eng.idsa.dataapp.domain;

public class MatchLocalRequest {
  private String providerId;
  private String consumerId;
  private String sampleFaceUrl;
  private String sampleVoiceUrl;
  private String sampleFingerprintUrl;

  public MatchLocalRequest() {
    super();
  }

  public MatchLocalRequest(String providerId, String consumerId, String sampleFaceUrl, String sampleFingerprintUrl,
      String sampleVoiceUrl) {
    super();
    this.providerId = providerId;
    this.consumerId = consumerId;
    this.sampleFaceUrl = sampleFaceUrl;
    this.sampleFingerprintUrl = sampleFingerprintUrl;
    this.sampleVoiceUrl = sampleVoiceUrl;
  }

  public String getProviderId() {
    return this.providerId;
  }

  public void setProviderId(String providerId) {
    this.providerId = providerId;
  }

  public String getConsumerId() {
    return this.consumerId;
  }

  public void setConsumerId(String consumerId) {
    this.consumerId = consumerId;
  }

  public String getSampleFaceUrl() {
    return this.sampleFaceUrl;
  }

  public void setSampleFaceUrl(String sampleFaceUrl) {
    this.sampleFaceUrl = sampleFaceUrl;
  }

  public String getSampleFingerprintUrl() {
    return this.sampleFingerprintUrl;
  }

  public void setSampleFingerprintUrl(String sampleFingerprintUrl) {
    this.sampleFingerprintUrl = sampleFingerprintUrl;
  }

  public String getSampleVoiceUrl() {
    return this.sampleVoiceUrl;
  }

  public void setSampleVoiceUrl(String sampleVoiceUrl) {
    this.sampleVoiceUrl = sampleVoiceUrl;
  }
}
