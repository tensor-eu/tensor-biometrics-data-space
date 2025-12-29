package it.eng.idsa.dataapp.web.rest;

import java.io.IOException;
import java.net.URISyntaxException;

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
import it.eng.idsa.dataapp.domain.MatchInitRequest;
import it.eng.idsa.dataapp.service.MatchInitService;

@RestController
public class MatchInitController {
  private static final Logger logger = LoggerFactory.getLogger(MatchInitController.class);
  private static final String FACE_HASH = "faceHash";
  private static final String FINGERPRINT_HASH = "fingerprintHash";
  private static final String VOICE_HASH = "voiceHash";

  private MatchInitService matchInitService;

  public MatchInitController(MatchInitService matchInitService) {
    this.matchInitService = matchInitService;
  }

  @PostMapping("/match/init")
  public ResponseEntity<?> routerMatchInit(@RequestHeader HttpHeaders httpHeaders,
      @RequestBody String body) throws UnsupportedOperationException, IOException {
    JSONParser parser = new JSONParser();
    JSONObject jsonObject;
    try {
      logger.info("Matching process initialization");

      jsonObject = (JSONObject) parser.parse(body);

      String faceHash = (String) jsonObject.get(FACE_HASH);
      String fingerprintHash = (String) jsonObject.get(FINGERPRINT_HASH);
      String voiceHash = (String) jsonObject.get(VOICE_HASH);

      MatchInitRequest matchInitRequest = matchInitService.parseIncomingMatchInitRequest(faceHash, fingerprintHash, voiceHash);

      return matchInitService.createMatchInit(matchInitRequest, httpHeaders);

    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Internal Error Occured");

    }
  }
}
