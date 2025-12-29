package it.eng.idsa.dataapp.handler;

import static de.fraunhofer.iais.eis.util.Util.asList;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

import de.fraunhofer.iais.eis.ArtifactRequestMessage;
import de.fraunhofer.iais.eis.ArtifactResponseMessageBuilder;
import de.fraunhofer.iais.eis.Message;
import it.eng.idsa.dataapp.service.SelfDescriptionService;
import it.eng.idsa.dataapp.service.ThreadService;
import it.eng.idsa.dataapp.web.rest.exceptions.BadParametersException;
import it.eng.idsa.dataapp.web.rest.exceptions.NotFoundException;
import it.eng.idsa.dataapp.web.rest.exceptions.NotAuthorizedException;
import it.eng.idsa.multipart.util.DateUtil;
import it.eng.idsa.multipart.util.UtilMessageService;
import it.eng.idsa.dataapp.util.TensorBE;
import it.eng.idsa.dataapp.service.TENSORConnectorRegistry;

@Component
public class ArtifactMessageHandler extends DataAppMessageHandler {

  private Boolean encodePayload;
  private SelfDescriptionService selfDescriptionService;
  private ThreadService threadService;
  private Path dataLakeDirectory;
  private Boolean contractNegotiationDemo;
  private String providerPod;
  private String encryptorAPI;
  private String dataSharingPlatformAPI;
  private final TensorBE tensorBE;
  private final TENSORConnectorRegistry registry;


  private static final Logger logger = LoggerFactory.getLogger(ArtifactMessageHandler.class);

  public ArtifactMessageHandler(SelfDescriptionService selfDescriptionService, ThreadService threadService,
      @Value("${application.dataLakeDirectory}") Path dataLakeDirectory,
      @Value("${application.contract.negotiation.demo}") Boolean contractNegotiationDemo,
      @Value("#{new Boolean('${application.encodePayload:false}')}") Boolean encodePayload,
      @Value("${application.encryptorAPI}") String encryptorAPI,
      @Value("${application.dataSharingPlatformAPI}") String dataSharingPlatformAPI,
      TensorBE tensorBE,
      TENSORConnectorRegistry registry
      ) {
    this.selfDescriptionService = selfDescriptionService;
    this.threadService = threadService;
    this.dataLakeDirectory = dataLakeDirectory;
    this.contractNegotiationDemo = contractNegotiationDemo;
    this.encodePayload = encodePayload;
    this.encryptorAPI = encryptorAPI;
    this.dataSharingPlatformAPI = dataSharingPlatformAPI;
    this.tensorBE = tensorBE;
    this.registry = registry;
  }

  @Override
  public Map<String, Object> handleMessage(Message message, Object payload, String solidPod, String solidToken) {
    providerPod = solidPod;

    logger.info("Handling header through ArtifactMessageHandler");

    ArtifactRequestMessage arm = (ArtifactRequestMessage) message;
    Message artifactResponseMessage = null;
    Map<String, Object> response = new HashMap<>();

    if (arm.getRequestedArtifact() != null) {
      logger.debug("Handling message with requestedElement:" + arm.getRequestedArtifact());
      if (Boolean.TRUE.equals(((Boolean) threadService.getThreadLocalValue("wss")))) {
        logger.debug("Handling message with requestedElement:" + arm.getRequestedArtifact() + " in WSS flow");
        payload = handleWssFlow(message);
      } else {
        logger.debug("Handling message with requestedElement:" + arm.getRequestedArtifact() + " in REST flow");
        payload = handleRestFlow(message, solidToken);
      }
    } else {
      logger.error("Artifact requestedElement not provided");

      throw new BadParametersException("Artifact requestedElement not provided", message);
    }

    artifactResponseMessage = createArtifactResponseMessage(arm);
    response.put(DataAppMessageHandler.HEADER, artifactResponseMessage);
    response.put(DataAppMessageHandler.PAYLOAD, payload);

    return response;
  }

  private String handleWssFlow(Message message) {
    String reqArtifact = ((ArtifactRequestMessage) message).getRequestedArtifact().getPath();
    String requestedArtifact = reqArtifact.substring(reqArtifact.lastIndexOf('/') + 1);
    logger.info("Requested artifact in WSS flow {}", requestedArtifact);
    if (contractNegotiationDemo) {
      logger.info("WSS Demo, reading directly from data lake");
      return readFile(requestedArtifact, message);
    } else {
      if (selfDescriptionService.artifactRequestedElementExist((ArtifactRequestMessage) message,
          selfDescriptionService.getSelfDescription(message))) {
        return readFile(requestedArtifact, message);
      } else {
        logger.error("Artifact requestedElement not exist in self description");

        throw new NotFoundException("Artifact requestedElement not found in self description", message);
      }
    }
  }

  private String readFile(String requestedArtifact, Message message) {
    logger.info("Reading file {} from datalake", requestedArtifact);
    byte[] fileContent;
    try {
      logger.info("Full Path: ", dataLakeDirectory.resolve(requestedArtifact));
      fileContent = Files.readAllBytes(dataLakeDirectory.resolve(requestedArtifact));
    } catch (IOException e) {
      logger.error("Could't read the file {} from datalake", requestedArtifact);

      throw new NotFoundException("Could't read the file from datalake", message);

    }
    String base64EncodedFile = Base64.getEncoder().encodeToString(fileContent);
    logger.info("File read from disk.");
    return base64EncodedFile;
  }

  private JSONObject handleRestFlow(Message message, String solidToken) {
    String reqArtifact = ((ArtifactRequestMessage) message).getRequestedArtifact().getPath();
    String requestedArtifact = reqArtifact.substring(reqArtifact.lastIndexOf('/')
        + 1);

    JSONObject payload = null;
    // Check if requested artifact exist in self description
    if (contractNegotiationDemo || selfDescriptionService.artifactRequestedElementExist(
        (ArtifactRequestMessage) message, selfDescriptionService.getSelfDescription(message))) {
      payload = createResponsePayload(requestedArtifact, solidToken);
      return payload;
    } else {
      logger.error("Artifact requestedElement not exist in self description");

      throw new NotFoundException("Artifact requestedElement not found in self description", message);
    }
  }

  private boolean isBigPayload(String path) {
    String isBig = path.substring(path.lastIndexOf('/'));
    if (isBig.equals("/big")) {
      return true;
    }

    return false;
  }

  private String encodePayload(byte[] payload) {
    logger.info("Encoding payload");

    return Base64.getEncoder().encodeToString(payload);
  }

  private byte[] downloadFile(String suspectProfileID, String solidToken) {
    byte[] downloadedFile = null;
    try {
      String suspectProfileUrl = dataSharingPlatformAPI + "/api/resources/" + providerPod + "%2Fsuspects%2F"
          + suspectProfileID + ".zip.enc?toJSONld=true";
      System.out.println("suspectProfileUrl " + suspectProfileUrl);
      URL url = new URL(suspectProfileUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Cookie", solidToken);

      int responseCode = connection.getResponseCode();
      System.out.println("responseCode " + responseCode);
      // Handle HTTP errors explicitly
      if (responseCode != 200) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            System.err.println(line); // Print server error details
          }
        }
        return null;
      }
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] data = new byte[8192];
      int bytesRead;
      while ((bytesRead = connection.getInputStream().read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, bytesRead);
      }
      downloadedFile = buffer.toByteArray();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return downloadedFile;
  }

  private JSONObject extractZipContents(byte[] zipFile) {
    String encodedResource = null;
    JSONParser parser = new JSONParser();
    JSONObject suspectInfo = new JSONObject();
    JSONArray faceImagesArray = new JSONArray();
    JSONArray voiceFilesArray = new JSONArray();
    JSONArray fingerprintFilesArray = new JSONArray();

    try {
      ByteArrayInputStream ByteArrayInputStream = new ByteArrayInputStream(zipFile);
      ZipInputStream zipInputStream = new ZipInputStream(ByteArrayInputStream);
      System.out.println("ZipInputStream created.");

      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        System.out.println("Reading next entry...");

        String fileName = entry.getName();
        System.out.println("fileName "+ fileName);
        // Handle 'info.json' file (outside 'face/' folder)
        if (fileName.equals("info.json")) {
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          byte[] buffer = new byte[1024];
          int len;
          while ((len = zipInputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, len);
          }
          String jsonContent = outputStream.toString("UTF-8");
          suspectInfo.put("info", (JSONObject) parser.parse(jsonContent));
        } else {
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          byte[] buffer = new byte[1024];
          int len;
          while ((len = zipInputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, len);
          }
          if (outputStream.size() == 0) {
            System.out.println("Stream is empty!");
            continue;
          }
          String mimeType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(outputStream.toByteArray()));

          if (fileName.startsWith("face/")) {
            if (mimeType == null) {
              mimeType = "image/png";
            }
            encodedResource = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
            faceImagesArray.add(encodedResource);
          } else if (fileName.startsWith("voice/")) {
            if (mimeType == null) {
              mimeType = "audio/flac";
            }
            encodedResource = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
            voiceFilesArray.add(encodedResource);
          } else if (fileName.startsWith("finger/")) {
            if (mimeType == null) {
              mimeType = "image/jpeg";
            }
            encodedResource = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
            fingerprintFilesArray.add(encodedResource);
          }
        }

        zipInputStream.closeEntry();
      }
      suspectInfo.put("face", faceImagesArray);
      suspectInfo.put("voice", voiceFilesArray);
      suspectInfo.put("fingerprint", fingerprintFilesArray);

    } catch (Exception e) {
      e.printStackTrace();
    }
    return suspectInfo;
  }

  private JSONObject getSuspectProfile(String suspectProfileID, String providerID, String solidToken) {
    byte[] encryptedFile = downloadFile(suspectProfileID, solidToken);
    if (encryptedFile == null) {
      System.out.println("_________ ERRROR IN encrypted file is null ");
      throw new NotAuthorizedException("Encrypted file could not be retrieved: usage time may have expired.");
    }
    byte[] decryptedFile = tensorBE.queryEncryptor("decrypt", "face", encryptedFile, suspectProfileID + ".zip.enc", providerID);
    System.out.println("Decrypted file length: " + decryptedFile.length);
    
    if (decryptedFile != null && decryptedFile.length > 0) {
        System.out.println("Decryption successful. Data size: " + decryptedFile.length + " bytes.");
        if (decryptedFile.length >= 4) {
          System.out.printf("ZIP signature bytes: 0x%02X 0x%02X 0x%02X 0x%02X\n",
          decryptedFile[0], decryptedFile[1], decryptedFile[2], decryptedFile[3]);
        }

    } else {
        System.out.println("Decryption failed or returned empty data.");
    }

    JSONObject suspectInfo = extractZipContents(decryptedFile);

    return suspectInfo;
  }

  private JSONObject createResponsePayload(String suspectProfileId, String solidToken) {
    String providerId = registry.getIdByPod(providerPod);

    // Request image from Solid's Data Pod
    JSONObject suspectProfile = getSuspectProfile(suspectProfileId, providerId, solidToken);
    if (suspectProfile == null) {
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("message", "Suspect biometric profile not found");
      return jsonObject;
    }
    return suspectProfile;
  }

  private Message createArtifactResponseMessage(ArtifactRequestMessage header) {
    // Need to set transferCotract from original message, it will be used in policy
    // enforcement
    return new ArtifactResponseMessageBuilder()._issuerConnector_(whoIAmEngRDProvider())
        ._issued_(DateUtil.normalizedDateTime())._modelVersion_(UtilMessageService.MODEL_VERSION)
        ._transferContract_(header.getTransferContract())._senderAgent_(whoIAmEngRDProvider())
        ._recipientConnector_(header != null ? asList(header.getIssuerConnector()) : asList(whoIAm()))
        ._correlationMessage_(header != null ? header.getId() : whoIAm())
        ._securityToken_(UtilMessageService.getDynamicAttributeToken()).build();
  }
}
