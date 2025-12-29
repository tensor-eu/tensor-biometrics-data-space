
package it.eng.idsa.dataapp.service;

import java.io.IOException;
import java.net.URISyntaxException;

import org.json.simple.JSONArray;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import it.eng.idsa.dataapp.domain.MatchLocalRequest;

public interface MatchLocalService {
  MatchLocalRequest parseIncomingMatchLocalRequest(String providerId, String consumerId, String sampleFaceUrl,
      String sampleFingerprintUrl,
      String sampleVoiceUrl);

  ResponseEntity<JSONArray> createMatchLocal(MatchLocalRequest matchLocalRequest, HttpHeaders httpHeaders)
      throws URISyntaxException, IOException;
}
