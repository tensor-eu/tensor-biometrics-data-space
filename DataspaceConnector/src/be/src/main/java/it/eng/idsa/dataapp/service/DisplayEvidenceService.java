package it.eng.idsa.dataapp.service;

import java.io.IOException;
import java.net.URISyntaxException;

import org.json.simple.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import it.eng.idsa.dataapp.domain.DisplayEvidenceRequest;;

public interface DisplayEvidenceService {
  DisplayEvidenceRequest parseIncomingDisplayEvidenceRequest(String suspectProfileID, String requestorID,
      String requestID);

  ResponseEntity<JSONObject> createDisplayEvidence(DisplayEvidenceRequest displayEvidenceRequest,
      HttpHeaders httpHeaders, String solidToken) throws URISyntaxException, IOException;

}
