package it.eng.idsa.dataapp.web.rest;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import it.eng.idsa.dataapp.domain.DisplayEvidenceRequest;
import it.eng.idsa.dataapp.service.DisplayEvidenceService;

@RestController
public class DisplayEvidenceController {
  private static final Logger logger = LoggerFactory.getLogger(DisplayEvidenceController.class);
  private static final String SUSPECT_PROFILE_ID = "suspectProfileID";
  private static final String REQUESTOR_ID = "requestorID";
  private static final String REQUEST_ID = "requestID";

  private DisplayEvidenceService displayEvidenceService;

  public DisplayEvidenceController(DisplayEvidenceService displayEvidenceService) {
    this.displayEvidenceService = displayEvidenceService;
  }

  @PostMapping("/display-evidence")
  public ResponseEntity<?> routerDisplayEvidence(@RequestHeader HttpHeaders httpHeaders, @RequestHeader String solidToken, @RequestBody String body)
      throws UnsupportedOperationException, IOException {
    JSONParser parser = new JSONParser();
    JSONObject jsonObject;
    try {
      logger.info("Display evidence initialization");

      jsonObject = (JSONObject) parser.parse(body);

      String suspectProfileID = (String) jsonObject.get(SUSPECT_PROFILE_ID);
      String requestorID = (String) jsonObject.get(REQUESTOR_ID);
      String requestID = (String) jsonObject.get(REQUEST_ID);

      DisplayEvidenceRequest displayEvidenceRequest = displayEvidenceService.parseIncomingDisplayEvidenceRequest(
          suspectProfileID,
          requestorID, requestID);

      return displayEvidenceService.createDisplayEvidence(displayEvidenceRequest, httpHeaders, solidToken);

    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Internal Error Occured");

    }
  }
}
