package it.eng.idsa.dataapp.service;

import java.io.IOException;
import java.net.URISyntaxException;

import org.json.simple.JSONArray;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import it.eng.idsa.dataapp.domain.MatchInitRequest;

public interface MatchInitService {
  MatchInitRequest parseIncomingMatchInitRequest(String faceHash, String fingerprintHash, String voiceHash);

  ResponseEntity<JSONArray> createMatchInit(MatchInitRequest matchInitRequest, HttpHeaders httpHeaders) throws URISyntaxException, IOException;

}
