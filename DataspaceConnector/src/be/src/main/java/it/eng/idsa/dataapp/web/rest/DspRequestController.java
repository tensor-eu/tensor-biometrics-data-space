package it.eng.idsa.dataapp.web.rest;

import java.io.IOException;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;

import it.eng.idsa.dataapp.domain.DspRequestRequest;
import it.eng.idsa.dataapp.service.DspRequestService;

@RestController
public class DspRequestController {
  private static final Logger logger = LoggerFactory.getLogger(DspRequestController.class);
  private DspRequestService dspRequestService;

  public DspRequestController(DspRequestService dspRequestService) {
    this.dspRequestService = dspRequestService;
  }

  @PostMapping(value = "/dsp/request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> routerDspRequest(@RequestHeader HttpHeaders httpHeaders, @RequestHeader String solidToken,
      @RequestPart(value = "providerId") String providerId,
      @RequestPart(value = "consumerId") String consumerId,
      @RequestPart(value = "faceFile", required = false) MultipartFile faceFile,
      @RequestPart(value = "voiceFile", required = false) MultipartFile voiceFile,
      @RequestPart(value = "fingerprintFile", required = false) MultipartFile fingerprintFile,
      @RequestPart(value = "suspectProfileId") String suspectProfileId,
      @RequestPart(value = "facialImageSimilarityScore") String facialImageSimilarityScore,
      @RequestPart(value = "fingerprintSimilarityScore") String fingerprintSimilarityScore,
      @RequestPart(value = "voiceprintSimilarityScore") String voiceprintSimilarityScore,
      @RequestPart(value = "descriptiveText") String descriptiveText)
      throws UnsupportedOperationException, IOException {
    try {
      logger.info("Creating Data Sharing Platform access request");
      System.out.println("Received DSP Request:");
      System.out.println("Headers: " + httpHeaders);
      System.out.println("solidToken: " + solidToken);
      System.out.println("providerId: " + providerId);
      System.out.println("consumerId: " + consumerId);
      System.out.println("suspectProfileId: " + suspectProfileId);
      System.out.println("facialImageSimilarityScore: " + facialImageSimilarityScore);
      System.out.println("fingerprintSimilarityScore: " + fingerprintSimilarityScore);
      System.out.println("voiceprintSimilarityScore: " + voiceprintSimilarityScore);
      System.out.println("descriptiveText: " + descriptiveText);

      DspRequestRequest dspRequestRequest = dspRequestService.parseIncomingDspRequestRequest(providerId,
          consumerId, suspectProfileId, facialImageSimilarityScore, fingerprintSimilarityScore,
          voiceprintSimilarityScore, descriptiveText, faceFile, voiceFile, fingerprintFile);

      return dspRequestService.createDSPAccessRequest(dspRequestRequest, httpHeaders, solidToken);
    } catch (UnsupportedOperationException | URISyntaxException | IOException e) {
      return ResponseEntity.badRequest().body("Internal Error Occured");
    }
  }
}
