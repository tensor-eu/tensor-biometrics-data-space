package it.eng.idsa.dataapp.web.rest;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import it.eng.idsa.dataapp.domain.MatchLocalRequest;
import it.eng.idsa.dataapp.service.MatchLocalService;

@RestController
public class MatchLocalController {
  private static final Logger logger = LoggerFactory.getLogger(MatchLocalController.class);
  private static final String PROVIDER_ID = "providerId";
  private static final String CONSUMER_ID = "consumerId";
  private static final String SAMPLE_FACE_URL = "sampleFaceUrl";
  private static final String SAMPLE_FINGERPRINT_URL = "sampleFingerprintUrl";
  private static final String SAMPLE_VOICE_URL = "sampleVoiceUrl";

  private MatchLocalService matchLocalService;

  public MatchLocalController(MatchLocalService matchLocalService) {
    this.matchLocalService = matchLocalService;
  }

  @PostMapping("/match/local")
  public ResponseEntity<?> routerMatchLocal(@RequestHeader HttpHeaders httpHeaders,
      @RequestBody String body) throws UnsupportedOperationException, IOException {
    JSONParser parser = new JSONParser();
    JSONObject jsonObject;
    try {
      logger.info("Matching process locally");

      jsonObject = (JSONObject) parser.parse(body);

      String providerId = (String) jsonObject.get(PROVIDER_ID);
      String consumerId = (String) jsonObject.get(CONSUMER_ID);
      String sampleFaceUrl = (String) jsonObject.get(SAMPLE_FACE_URL);
      String sampleFingerprintUrl = (String) jsonObject.get(SAMPLE_FINGERPRINT_URL);
      String sampleVoiceUrl = (String) jsonObject.get(SAMPLE_VOICE_URL);

      MatchLocalRequest matchLocalRequest = matchLocalService.parseIncomingMatchLocalRequest(providerId, consumerId,
          sampleFaceUrl,
          sampleFingerprintUrl, sampleVoiceUrl);

      return matchLocalService.createMatchLocal(matchLocalRequest, httpHeaders);

    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Internal Error Occured");

    }
  }

}
