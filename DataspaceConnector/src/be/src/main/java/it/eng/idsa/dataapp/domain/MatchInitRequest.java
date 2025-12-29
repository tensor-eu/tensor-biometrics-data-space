package it.eng.idsa.dataapp.domain;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MatchInitRequest {
    private String faceHash;
    private String voiceHash;
    private String fingerprintHash;

    public MatchInitRequest() {
      super();
    }

    public MatchInitRequest(String faceHash, String fingerprintHash, String voiceHash) {
        super();
        this.faceHash = faceHash;
        this.fingerprintHash = fingerprintHash;
        this.voiceHash = voiceHash;
    }

    public String getFaceHash() {
      return this.faceHash;
    }

    public void setFaceHash(String faceHash) {
      this.faceHash = faceHash;
    }

    public String getFingerprintHash() {
      return this.fingerprintHash;
    }

    public void setFingerprintHash(String fingerprintHash) {
      this.fingerprintHash = fingerprintHash;
    }

    public String getVoiceprintHash() {
      return this.voiceHash;
    }

    public void setVoiceprintHash(String voiceHash) {
      this.voiceHash = voiceHash;
    }
}

