package it.eng.idsa.dataapp.service;

import java.io.IOException;
import java.net.URISyntaxException;

import org.json.simple.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import it.eng.idsa.dataapp.domain.CriminalProfileRequest;

public interface CriminalProfileService {
  CriminalProfileRequest parseIncomingCriminalProfileRequest(String suspectProfileID, String providerID);

  ResponseEntity<JSONObject> createCriminalProfile(CriminalProfileRequest criminalProfileRequest,
      HttpHeaders httpHeaders, String solidToken) throws URISyntaxException, IOException;
}
