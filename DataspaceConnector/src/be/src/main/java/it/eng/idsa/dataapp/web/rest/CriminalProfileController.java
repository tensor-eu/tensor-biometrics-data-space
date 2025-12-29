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

import it.eng.idsa.dataapp.domain.CriminalProfileRequest;
import it.eng.idsa.dataapp.service.CriminalProfileService;

@RestController
public class CriminalProfileController {
  private static final Logger logger = LoggerFactory.getLogger(CriminalProfileController.class);
  private static final String SUSPECT_PROFILE_ID = "suspectProfileID";
  private static final String PROVIDER_ID = "providerID";

  private CriminalProfileService criminalProfileService;

  public CriminalProfileController(CriminalProfileService criminalProfileService) {
    this.criminalProfileService = criminalProfileService;
  }

  @PostMapping("/criminal-profile")
  public ResponseEntity<?> routerCriminalProfile(@RequestHeader HttpHeaders httpHeaders, @RequestHeader String solidToken,
      @RequestBody String body) throws UnsupportedOperationException, IOException {
    logger.info("Retrieving Criminal Profile process initialization");
    JSONParser parser = new JSONParser();
    JSONObject jsonObject;
    try {
      logger.info("Criminal profile");

      jsonObject = (JSONObject) parser.parse(body);

      String suspectProfileID = (String) jsonObject.get(SUSPECT_PROFILE_ID);
      String providerID = (String) jsonObject.get(PROVIDER_ID);

      CriminalProfileRequest criminalProfileRequest = criminalProfileService
          .parseIncomingCriminalProfileRequest(suspectProfileID, providerID);

      return criminalProfileService.createCriminalProfile(criminalProfileRequest, httpHeaders, solidToken);

    } catch (Exception e) {
      return ResponseEntity.badRequest().body("Internal Error Occured");

    }
  }
}