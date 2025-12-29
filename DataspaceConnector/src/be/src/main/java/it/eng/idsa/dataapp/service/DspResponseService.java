package it.eng.idsa.dataapp.service;

import java.io.IOException;
import java.net.URISyntaxException;

import org.json.simple.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import it.eng.idsa.dataapp.domain.DspResponseRequest;

public interface DspResponseService {
  DspResponseRequest parseIncomingDspResponseRequest(String body);

  ResponseEntity<JSONObject> createDSPAccessResponse(DspResponseRequest dspResponseRequest, HttpHeaders httpHeaders,
      String solidToken)
      throws URISyntaxException, IOException;
}
