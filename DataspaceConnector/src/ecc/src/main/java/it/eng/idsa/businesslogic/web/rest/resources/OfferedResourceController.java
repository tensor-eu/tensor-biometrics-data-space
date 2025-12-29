package it.eng.idsa.businesslogic.web.rest.resources;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.print.attribute.standard.Media;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import org.json.simple.JSONObject;

import de.fraunhofer.iais.eis.BaseConnectorImpl;
import de.fraunhofer.iais.eis.Connector;
import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.ResourceImpl;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.eng.idsa.businesslogic.audit.Auditable;
import it.eng.idsa.businesslogic.audit.TrueConnectorEvent;
import it.eng.idsa.businesslogic.audit.TrueConnectorEventType;
import it.eng.idsa.businesslogic.service.resources.JsonException;
import it.eng.idsa.businesslogic.service.resources.OfferedResourceService;
import it.eng.idsa.multipart.processor.MultipartMessageProcessor;
import it.eng.idsa.businesslogic.util.TensorECC;

@Tag(name = "Offered resource controller")
@RestController
@RequestMapping("/api/offeredResource/")
public class OfferedResourceController {

  private static final Logger logger = LoggerFactory.getLogger(OfferedResourceController.class);

  private String dataSharingPlatformAPI;
  
  private OfferedResourceService service;

  private ApplicationEventPublisher publisher;
  private final TensorECC tensorECC;

  public OfferedResourceController(@Value("${application.dataSharingPlatformAPI}") String dataSharingPlatformAPI, OfferedResourceService service, ApplicationEventPublisher publisher, TensorECC tensorECC) {
    super();
    this.dataSharingPlatformAPI = dataSharingPlatformAPI;
    this.service = service;
    this.publisher = publisher;
    this.tensorECC = tensorECC;
  }

  @Operation(tags = "Offered resource controller", summary = "Get requested resource")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Returns requested resource", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = ResourceImpl.class)) }) })
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Auditable(eventType = TrueConnectorEventType.OFFERED_RESOURCE)
  @ResponseBody
  public ResponseEntity<String> getResource(@RequestHeader("resource") URI resource) throws IOException {
    logger.debug("Fetching offered resource with id '{}'", resource);
    Resource resourceGet = service.getOfferedResource(resource);
    return ResponseEntity.ok(MultipartMessageProcessor.serializeToJsonLD(resourceGet));
  }

  @Operation(tags = "Offered resource controller", summary = "Add new resource")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Returns modified connector", content = {
          @Content(mediaType = "multipart/form-data", schema = @Schema(implementation = BaseConnectorImpl.class)) }) })
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public ResponseEntity<String> addOrUpdateResource(@RequestHeader("catalog") URI catalog,
      @RequestHeader("solidToken") String solidToken,
      @RequestPart(value = "suspectProfileZip", required = true) MultipartFile resourceFile,
      @RequestPart("suspectProfileInfo") String suspectProfileInfo,
      @RequestPart("user") String user,
      @RequestPart("sensitive") String sensitive,
      HttpServletRequest request) throws IOException {

    // Check for potential errors before proceeding to creations
    String contentType = resourceFile.getContentType();

    // We will set the suspect profile Id to the value of correlationId
    String correlationId = UUID.randomUUID().toString();
    System.out.println("correlationId " + correlationId);
    // Check whether appropriate type is provided in the request body parts
    if (!"application/zip".equals(contentType) && !"application/x-zip-compressed".equals(contentType)) {
      throw new IOException("Provided file is not of type zip.");
    }
    publisher.publishEvent(
        new TrueConnectorEvent(request, TrueConnectorEventType.HTTP_REQUEST_RECEIVED, correlationId, suspectProfileInfo));
    Connector modifiedConnector = null;
    try {
      // Part 4: Register resource in Connector's Catalog
      Serializer s = new Serializer();
      Resource r = s.deserialize(suspectProfileInfo, Resource.class);
      logger.info("Adding offered resource with id '{}' to catalog '{}'", r.getId(), catalog);
      
      String suspectProfileId = r.getId().toString().substring(r.getId().toString().lastIndexOf("/") + 1);
      System.out.println(suspectProfileId); 

      // Part 1: Use Indexer Component to generate the Hashed Indexes and store the produced results in the Solid Pod
      System.out.println("Raw sensitive string from request: " + sensitive);

      boolean isSensitive = "true".equalsIgnoreCase(sensitive);

      // if (isSensitive) {
      //   Boolean indexingComplete = tensorECC.indexBiometricSamples(
      //     resourceFile,
      //     suspectProfileId.toString(),
      //     user,
      //     isSensitive,
      //     solidToken
      //   );

      //   if (!indexingComplete) {
      //     throw new IOException("Indexing of biometric data has failed.");
      //   }
      // } else {
      //   // Optionally, log that indexing was skipped
      //   System.out.println("Sensitive flag is false. Skipping biometric indexing.");
      // }

      Boolean indexingComplete = tensorECC.indexBiometricSamples(resourceFile, suspectProfileId.toString(), user, isSensitive, solidToken);
      if (!indexingComplete) {
        throw new IOException("Indexing of biometric data has failed.");
      }

      // Part 2: Encrypt suspect profile
      byte[] encryptedSuspectProfile = tensorECC.queryEncryptor("face", resourceFile, suspectProfileId + ".zip", user);

      // Part 3: Use DSP & Solid Pod to store the AES encrypted resources to the Solid Pod
      Boolean storingComplete = tensorECC.storeSuspectProfile(solidToken, user, suspectProfileId, encryptedSuspectProfile);

      modifiedConnector = service.addOfferedResource(catalog, r);
      publisher.publishEvent(new TrueConnectorEvent(request, TrueConnectorEventType.OFFERED_RESOURCE_CREATED, suspectProfileId)); 
    } catch (IOException e) {
      publisher.publishEvent(
          new TrueConnectorEvent(request, TrueConnectorEventType.OFFERED_RESOURCE_CREATION_FAILED, correlationId));
      throw new JsonException("Error while processing request\n" + e.getMessage());
    }
    return ResponseEntity.ok(MultipartMessageProcessor.serializeToJsonLD(modifiedConnector));
  }

  @Operation(tags = "Offered resource controller", summary = "Update existing resource")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Returns modified connector", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = BaseConnectorImpl.class)) }) })
  @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public ResponseEntity<String> updateResource(@RequestHeader("catalog") URI catalog,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = ResourceImpl.class)) }) @RequestBody String resource,
      HttpServletRequest request) throws IOException {
    String correlationId = UUID.randomUUID().toString();
    publisher.publishEvent(
        new TrueConnectorEvent(request, TrueConnectorEventType.HTTP_REQUEST_RECEIVED, correlationId, resource));
    Connector modifiedConnector = null;
    try {
      Serializer s = new Serializer();
      Resource r = s.deserialize(resource, Resource.class);
      logger.info("Updating offered resource with id '{}' to catalog '{}'", r.getId(), catalog);
      modifiedConnector = service.updateOfferedResource(catalog, r);
      publisher.publishEvent(
          new TrueConnectorEvent(request, TrueConnectorEventType.OFFERED_RESOURCE_UPDATED, correlationId));
    } catch (IOException e) {
      publisher.publishEvent(
          new TrueConnectorEvent(request, TrueConnectorEventType.OFFERED_RESOURCE_UPDATE_FAILED, correlationId));
      throw new JsonException("Error while processing request\n" + e.getMessage());
    }
    return ResponseEntity.ok(MultipartMessageProcessor.serializeToJsonLD(modifiedConnector));
  }

  @Operation(tags = "Offered resource controller", summary = "Delete existing resource")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Returns modified connector", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = BaseConnectorImpl.class)) }) })
  @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Auditable(eventType = TrueConnectorEventType.OFFERED_RESOURCE_DELETED)
  @ResponseBody
  public ResponseEntity<String> deleteResource(@RequestHeader("resource") URI offeredResource) throws IOException {
    Connector modifiedConnector = null;
    logger.info("Deleting offered resource with id '{}'", offeredResource);
    modifiedConnector = service.deleteOfferedResource(offeredResource);
    return ResponseEntity.ok(MultipartMessageProcessor.serializeToJsonLD(modifiedConnector));
  }
}
