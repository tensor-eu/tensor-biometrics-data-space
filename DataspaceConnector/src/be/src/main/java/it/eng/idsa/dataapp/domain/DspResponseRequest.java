package it.eng.idsa.dataapp.domain;

public class DspResponseRequest {
  private String providerId;
  private String consumerId;
  private String solidPod;
  private String suspectProfileId;
  private long requestId;
  private String recipientAddress;
  private String resUrl;
  private String duration;
  private String accessType;
  private String responseType;
  private String encryptionKey;
  private String tos;

  public DspResponseRequest() {
    super();
  }

  public DspResponseRequest(String providerId, String consumerId, String solidPod, long requestId, String suspectProfileId, String recipientAddress,
      String resUrl, String duration,
      String accessType, String responseType, String encryptionKey, String tos) {
    super();
    this.providerId = providerId;
    this.consumerId = consumerId;
    this.solidPod = solidPod;
    this.suspectProfileId = suspectProfileId;
    this.requestId = requestId;
    this.recipientAddress = recipientAddress;
    this.resUrl = resUrl;
    this.duration = duration;
    this.accessType = accessType;
    this.responseType = responseType;
    this.encryptionKey = encryptionKey;
    this.tos = tos;
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

  public String getSolidPod() {
    return this.solidPod;
  }

  public void setSolidPod(String solidPod) {
    this.solidPod = solidPod;
  }

  public String getSuspectProfileId() {
    return this.suspectProfileId;
  }

  public void setSuspectProfileId(String suspectProfileId) {
    this.suspectProfileId = suspectProfileId;
  }

  public long getRequestId() {
    return this.requestId;
  }

  public void setRequestId(long requestId) {
    this.requestId = requestId;
  }

  public String getRecipientAddress() {
    return this.recipientAddress;
  }

  public void setRecipientAddress(String recipientAddress) {
    this.recipientAddress = recipientAddress;
  }

  public String getResUrl() {
    return this.resUrl;
  }

  public void setResUrl(String resUrl) {
    this.resUrl = resUrl;
  }

  public String getDuration() {
    return this.duration;
  }

  public void setDuration(String duration) {
    this.duration = duration;
  }

  public String getAccessType() {
    return this.accessType;
  }

  public void setAccessType(String accessType) {
    this.accessType = accessType;
  }

  public String getResponseType() {
    return this.responseType;
  }

  public void setResponseType(String responseType) {
    this.responseType = responseType;
  }

  public String getEncryptionKey() {
    return this.encryptionKey;
  }

  public void setEncryptionKey(String encryptionKey) {
    this.encryptionKey = encryptionKey;
  }

  public String getTos() {
    return this.tos;
  }

  public void setTos(String tos) {
    this.tos = tos;
  }

}
