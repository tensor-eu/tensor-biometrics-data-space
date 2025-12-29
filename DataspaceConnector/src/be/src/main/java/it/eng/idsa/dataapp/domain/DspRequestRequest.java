package it.eng.idsa.dataapp.domain;

import org.springframework.web.multipart.MultipartFile;

public class DspRequestRequest {
  private String providerId;
  private String consumerId;
  private String suspectProfileId;
  private String facialImageSimilarityScore;
  private String fingerprintSimilarityScore;
  private String voiceprintSimilarityScore;
  private String descriptiveText;
  private MultipartFile faceFile;
  private MultipartFile voiceFile;
  private MultipartFile fingerprintFile;

  public DspRequestRequest() {
    super();
  }

  public DspRequestRequest(String providerId, String consumerId,
      String suspectProfileId,
      String facialImageSimilarityScore,
      String fingerprintSimilarityScore,
      String voiceprintSimilarityScore,
      String descriptiveText,
      MultipartFile faceFile,
      MultipartFile voiceFile,
      MultipartFile fingerprintFile) {
    super();
    this.providerId = providerId;
    this.consumerId = consumerId;
    this.suspectProfileId = suspectProfileId;
    this.facialImageSimilarityScore = facialImageSimilarityScore;
    this.fingerprintSimilarityScore = fingerprintSimilarityScore;
    this.voiceprintSimilarityScore = voiceprintSimilarityScore;
    this.descriptiveText = descriptiveText;
    this.faceFile = faceFile;
    this.voiceFile = voiceFile;
    this.fingerprintFile = fingerprintFile;
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

  public String getSuspectProfileId() {
    return this.suspectProfileId;
  }

  public void setSuspectProfileId(String suspectProfileId) {
    this.suspectProfileId = suspectProfileId;
  }

  public String getFacialImageSimilarityScore() {
    return this.facialImageSimilarityScore;
  }

  public void setFacialImageSimilarityScore(String facialImageSimilarityScore) {
    this.facialImageSimilarityScore = facialImageSimilarityScore;
  }

  public String getFingerprintSimilarityScore() {
    return this.fingerprintSimilarityScore;
  }

  public void setFingerprintSimilarityScore(String fingerprintSimilarityScore) {
    this.fingerprintSimilarityScore = fingerprintSimilarityScore;
  }

  public String getVoiceprintSimilarityScore() {
    return this.voiceprintSimilarityScore;
  }

  public void setVoiceprintSimilarityScore(String voiceprintSimilarityScore) {
    this.voiceprintSimilarityScore = voiceprintSimilarityScore;
  }

  public String getDescriptiveText() {
    return this.descriptiveText;
  }

  public void setDescriptiveText(String descriptiveText) {
    this.voiceprintSimilarityScore = descriptiveText;
  }

  public MultipartFile getFaceFile() {
    return this.faceFile;
  }

  public void setFaceFile(MultipartFile faceFile) {
    this.faceFile = faceFile;
  }

  public MultipartFile getVoiceFile() {
    return this.voiceFile;
  }

  public void setVoiceFile(MultipartFile voiceFile) {
    this.voiceFile = voiceFile;
  }

  public MultipartFile getFingerprintFile() {
    return this.fingerprintFile;
  }

  public void setFingerprintFile(MultipartFile fingerprintFile) {
    this.fingerprintFile = fingerprintFile;
  }
}
