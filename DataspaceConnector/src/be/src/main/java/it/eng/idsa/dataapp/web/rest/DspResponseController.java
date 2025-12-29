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

import it.eng.idsa.dataapp.domain.DspResponseRequest;
import it.eng.idsa.dataapp.service.DspResponseService;

@RestController
public class DspResponseController {
  private static final Logger logger = LoggerFactory.getLogger(DspResponseController.class);
  private DspResponseService dspResponseService;

  public DspResponseController(DspResponseService dspResponseService) {
    this.dspResponseService = dspResponseService;
  }

  @PostMapping("/dsp/response")
  public ResponseEntity<?> routerDspResponse(@RequestHeader HttpHeaders httpHeaders, @RequestHeader String solidToken,
      @RequestBody String body) throws UnsupportedOperationException, IOException {
    try {
      logger.info("Creating Data Sharing Platform access response");

      DspResponseRequest dspResponseRequest = dspResponseService.parseIncomingDspResponseRequest(body);

      return dspResponseService.createDSPAccessResponse(dspResponseRequest, httpHeaders, solidToken);

    } catch (UnsupportedOperationException | URISyntaxException | IOException e) {
      return ResponseEntity.badRequest().body("Internal Error Occured");
    }
  }
}
