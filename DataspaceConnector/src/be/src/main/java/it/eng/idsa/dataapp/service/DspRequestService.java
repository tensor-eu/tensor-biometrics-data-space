package it.eng.idsa.dataapp.service;

import java.io.IOException;
import java.net.URISyntaxException;

import org.json.simple.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import it.eng.idsa.dataapp.domain.DspRequestRequest;

public interface DspRequestService {
  DspRequestRequest parseIncomingDspRequestRequest(String providerId, String consumerId,
      String suspectProfileId,
      String facialImageSimilarityScore,
      String fingerprintSimilarityScore,
      String voiceprintSimilarityScore,
      String descriptiveText,
      MultipartFile faceFile,
      MultipartFile voiceFile,
      MultipartFile fingerprintFile);

  ResponseEntity<JSONObject> createDSPAccessRequest(DspRequestRequest dspRequestRequest, HttpHeaders httpHeaders,
      String solidToken)
      throws URISyntaxException, IOException;

}
