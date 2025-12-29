package it.eng.idsa.businesslogic.util;

import java.io.*;

import java.util.*;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.concurrent.Semaphore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

import java.net.URISyntaxException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.*;

import org.json.simple.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.eng.idsa.businesslogic.util.TensorBiometricFile;
import it.eng.idsa.businesslogic.service.TENSORConnectorRegistry;

@Component
public class TensorECC {

  private static final Logger logger = LoggerFactory.getLogger(TensorECC.class);

  private static String dataSharingPlatformAPI;
  private static String encryptorAPI;
  private static String indexerAPI;
  private static TENSORConnectorRegistry registry;

  public TensorECC(
    @Value("${application.dataSharingPlatformAPI}") String dataSharingPlatformAPI, 
    @Value("${application.encryptorAPI}") String encryptorAPI, 
    @Value("${application.indexerAPI}") String indexerAPI,
    TENSORConnectorRegistry registry) {
    super();
    this.dataSharingPlatformAPI = dataSharingPlatformAPI;
    this.encryptorAPI = encryptorAPI;
    this.indexerAPI = indexerAPI;
    this.registry = registry;
  }

  public static Map<String, List<TensorBiometricFile>> prepareBiometricFilesList(MultipartFile suspectProfileZip) throws IOException {
      Map<String, List<TensorBiometricFile>> biometricFilesList = new HashMap<>();

      try (ZipInputStream zis = new ZipInputStream(suspectProfileZip.getInputStream())) {
          ZipEntry entry;
          while ((entry = zis.getNextEntry()) != null) {
              System.out.println("Reading ZIP entry: " + entry.getName());

              if (entry.isDirectory()) {
                  System.out.println("Skipping directory: " + entry.getName());
                  continue;
              }

              String fullPath = entry.getName(); // e.g., face/image.jpg

              if (!fullPath.contains("/")) {
                  System.out.println("Skipping file not inside a directory: " + fullPath);
                  continue;
              }

              String[] parts = fullPath.split("/");
              if (parts.length < 2) {
                  System.out.println("Invalid path format, skipping: " + fullPath);
                  continue;
              }

              String dir = parts[0]; // face, finger, voice, etc.
              String fileName = parts[parts.length - 1];

              System.out.println("Processing file - Directory: " + dir + ", File name: " + fileName);

              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              byte[] buffer = new byte[1024];
              int len;
              while ((len = zis.read(buffer)) > 0) {
                  baos.write(buffer, 0, len);
              }

              byte[] content = baos.toByteArray();
              System.out.println("Read file size: " + content.length + " bytes");

              String contentType;
              try (ByteArrayInputStream bais = new ByteArrayInputStream(content)) {
                  contentType = URLConnection.guessContentTypeFromStream(bais);

                  if (contentType == null) {
                    if (fileName.endsWith(".flac")) contentType = "audio/flac";
                    else if (fileName.endsWith(".tif") || fileName.endsWith(".tiff")) contentType = "image/tiff";
                    else contentType = "application/octet-stream";
                  }
              }

              TensorBiometricFile biometricFile = new TensorBiometricFile(fileName, content, contentType);

              biometricFilesList
                  .computeIfAbsent(dir, k -> new ArrayList<>())
                  .add(biometricFile);

              System.out.println("Added file to category: " + dir);
          }
      } catch (IOException e) {
          System.err.println("Error while parsing biometric ZIP file:");
          e.printStackTrace();
          throw e;
      }

      System.out.println("Biometric file parsing complete. Categories found: " + biometricFilesList.keySet());
      return biometricFilesList;
  }

  // Limit to 2 concurrent requests to gpi-solid
  private static final Semaphore semaphore = new Semaphore(2);

  private static void sendRequest(TensorBiometricFile file, String biometricType, String user, String suspectProfileId, boolean sensitive, String solidToken) {
    System.out.println("Sending request for " + suspectProfileId);
    URL url;
    HttpURLConnection connection = null;
    JSONObject jsonObject = null;

    try {   
      //String biometricDataUrl = uploadBiometricSample(file, user, solidToken, suspectProfileId);
      String encodedBiometricFile = encodeBiometricSample(file, user, solidToken, suspectProfileId);
      url = new URL(indexerAPI + "/indexLocalData");
      connection = (HttpURLConnection) url.openConnection();
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-type", "application/json; charset=UTF-8");

      try (OutputStream os = connection.getOutputStream();
          OutputStreamWriter osw = new OutputStreamWriter(os);
          BufferedWriter wr = new BufferedWriter(osw)) {

        // JSONObject bodyObject = new JSONObject();
        // bodyObject.put("suspect_id", suspectProfileId);
        // bodyObject.put("biometric_type", biometricType);
        // bodyObject.put("full_biometric_data_url", encodedBiometricFile);
        // bodyObject.put("owner", user);
        // bodyObject.put("sensitive", sensitive);

        // String body = bodyObject.toJSONString();
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("suspect_id", suspectProfileId);
        bodyMap.put("biometric_type", biometricType);
        bodyMap.put("full_biometric_data_url", encodedBiometricFile);
        bodyMap.put("owner", user);
        bodyMap.put("sensitive", sensitive);

        String body = new ObjectMapper().writeValueAsString(bodyMap);

        wr.write(body);
      }
      System.out.println("Starting index for suspect " + suspectProfileId + " at " + System.currentTimeMillis());

      int responseCode = connection.getResponseCode();
      System.out.println("indexBiometricFile: " + responseCode);
      System.out.println("Finished index for suspect " + suspectProfileId + " at " + System.currentTimeMillis() + " with response code " + responseCode);
      connection.disconnect();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void indexBiometricFile(TensorBiometricFile file, String biometricType, String user, String suspectProfileId, boolean sensitive, String solidToken) {
    try {
      // Acquire a "slot" for concurrency
      semaphore.acquire();

      // Optional: small sleep to reduce burstiness
      Thread.sleep(5000); 

      // --- Existing logic to send the request ---
      sendRequest(file, biometricType, user, suspectProfileId, sensitive, solidToken);
      Thread.sleep(10000); 
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Interrupted while waiting for semaphore: " + e.getMessage());
    } finally {
      // Release the slot so other threads can proceed
      semaphore.release();
    }
  }

  public static String encodeBiometricSample(TensorBiometricFile file, String user, String solidToken, String suspectProfileId) {
    if (file == null || file.getContent() == null) {
        throw new IllegalArgumentException("Biometric file or content cannot be null");
    }

    String encodedFileContent = Base64.getEncoder().encodeToString(file.getContent());

    //return "data:" + file.getContentType() + ";base64," + encodedFileContent;
    return Base64.getEncoder().encodeToString(file.getContent());

  }

  public static String uploadBiometricSample(TensorBiometricFile file, String user, String solidToken, String suspectProfileId) {
    String dspAPI = registry.getDspAPIById(user);
    String podName = registry.getPodById(user);
    
    String biometricFileUrl = dspAPI + "/api/resources/"+podName+"%2Findexed_data%2F" + suspectProfileId + "%2F" + file.getFileName();
    try {

      String fileName = file.getFileName();
      // Construct the upload URL
      String uploadUrl = "https://" + dspAPI + ".tensor-horizon.eu/dsp/api/resources/"+podName+ "%2Findexed_data%2F" + suspectProfileId + "%2F";

      System.out.println("uploadURL " + uploadUrl);
      System.out.println("fileName: "+ file.getFileName());
      HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");

      String boundary = "---------------------------" + System.currentTimeMillis();
      String charset = "UTF-8";
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
      connection.setRequestProperty("Cookie", solidToken);

      try (
          OutputStream output = connection.getOutputStream();
          PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);
          ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(file.getContent());
          BufferedInputStream bufferedInputStream = new BufferedInputStream(byteArrayInputStream)) {

        // File Field
        writer.append("--" + boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getFileName() + "\"")
            .append("\r\n");
        writer.append("Content-Type: " + HttpURLConnection.guessContentTypeFromName(file.getFileName())).append("\r\n\r\n")
            .flush();

        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
          output.write(buffer, 0, bytesRead);
        }
        output.flush();

        // End of multipart/form-data
        writer.append("\r\n--" + boundary + "--").append("\r\n").flush();
      }

      // Response
      int responseCode = connection.getResponseCode();
      System.out.println("RESPONSE CODE UPLOAD " + responseCode);


      connection.disconnect();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return biometricFileUrl;
  }


  public static Boolean indexBiometricSamples(MultipartFile biometricFileZip, String suspectProfileId, String user, boolean sensitive, String solidToken) {
    try {
      Map<String, List<TensorBiometricFile>> files = prepareBiometricFilesList(biometricFileZip);

      List<TensorBiometricFile> faceFiles = files.get("face");
      List<TensorBiometricFile> fingerprintFiles = files.get("fingerprint");
      List<TensorBiometricFile> voiceFiles = files.get("voice");
      // Face
      if (faceFiles != null) {
        for (TensorBiometricFile file : faceFiles) {
          System.out.println("Face file: " + file.getFileName() + " content type : " + file.getContentType());
          indexBiometricFile(file, "image", user, suspectProfileId, sensitive, solidToken);
        }
      }
      // Fingerprint
      if (fingerprintFiles != null) {
        for (TensorBiometricFile file : fingerprintFiles) {
          System.out.println("Fingerprint file: " + file.getFileName() + " content type : " + file.getContentType());
          indexBiometricFile(file, "fingerprint", user, suspectProfileId, sensitive, solidToken);
        }
      }
      // Voice
      if (voiceFiles != null) {
        for (TensorBiometricFile file : voiceFiles) {
          System.out.println("Voice file: " + file.getFileName() + " content type : " + file.getContentType());
          indexBiometricFile(file, "voice", user, suspectProfileId, sensitive, solidToken);
        }
      }
      return true;

    } catch (Exception e) {
      logger.error("Error while indexing suspect profile biometric samples", e);
      return false;
    }
  }

  private Map<String, String> getFuzzyExtractorFile(String user) {
    Map<String, String> fuzzyExtractorFile = new HashMap<>();
    try {
        String filename = registry.getFuzzyExtractorFileById(user);

        InputStream is = getClass().getClassLoader().getResourceAsStream(filename);

        if (is == null) {
            throw new FileNotFoundException("Image file not found: " + filename);
        }

        byte[] fileBytes = is.readAllBytes();
        is.close();
        fuzzyExtractorFile.put("filename", filename);
        fuzzyExtractorFile.put("content", Base64.getEncoder().encodeToString(fileBytes)); // If you want to keep content in Base64 for debug/logging
        fuzzyExtractorFile.put("binary", "true"); // Flag to indicate it's a binary image

    } catch (Exception e) {
        e.printStackTrace();
    }
    return fuzzyExtractorFile;
  }

  private void addRequestFilePart(PrintWriter writer, OutputStream outputStream, String fieldName, String fileName,
      byte[] uploadFile, String boundary, String CRLF, String charset) throws IOException {
    // Boundary and form-data headers
    writer.append("--").append(boundary).append(CRLF);
    writer.append("Content-Disposition: form-data; name=\"").append(fieldName)
        .append("\"; filename=\"").append(fileName).append("\"").append(CRLF);

    // Set content type or default to application/octet-stream
    String contentType = "application/octet-stream"; // Assuming unknown content type
    writer.append("Content-Type: ").append(contentType).append(CRLF);
    writer.append(CRLF).flush(); // End of headers

    // Write the byte array (file content) directly to the outputStream
    outputStream.write(uploadFile);
    outputStream.flush();

    // Finish part with CRLF
    writer.append(CRLF).flush(); // Blank line after the file content
  }

  public static byte[] convertMultipartFileToBytes(MultipartFile multipartFile) throws IOException {
    System.out.println("Converted multipart file to bytes ");
    return multipartFile != null && !multipartFile.isEmpty() ? multipartFile.getBytes() : null;
  }

  public byte[] queryEncryptor(String mode, MultipartFile multipartFile, String fileName, String user) {
    // Variable for binary file to be returned
    byte[] resultFile = null;
    try {
      byte[] file = convertMultipartFileToBytes(multipartFile);

      // Information about multipart request 
      String boundary = "Boundary-" + System.currentTimeMillis();
      String CRLF = "\r\n"; 
      String charset = "UTF-8";

      // Encryptor variables based on queryType
      String endpoint = "/encrypt";
      String feFileIndicator = "enroll_image";
      String fileIndicator ="file_to_encrypt";

      // Prepare URL
      System.out.println("encryptor URL "+ encryptorAPI);
      URL url = new URL(encryptorAPI + endpoint + "?mode=" + mode);
   
      // Create the connection
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.setUseCaches(false);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

      // Open connection
      OutputStream outputStream = connection.getOutputStream();
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, charset), true);

      // Add fuzzy extractor image in the request parts
      Map<String, String> fuzzyExtractorFile = getFuzzyExtractorFile(user);
      String fuzzyExtractorFileName = fuzzyExtractorFile.get("filename");
      System.out.println("fuzzyExtractorFileName: " + fuzzyExtractorFileName);
      // Use binary bytes (not base64-decoded from string)
      InputStream imageStream = getClass().getClassLoader().getResourceAsStream(fuzzyExtractorFileName);
      byte[] fuzzyExtractorFileContent = imageStream.readAllBytes();
      imageStream.close();

      addRequestFilePart(writer, outputStream, feFileIndicator, fuzzyExtractorFileName, fuzzyExtractorFileContent, boundary, CRLF, charset);
      System.out.println("fileName: " + fileName);
      // Add file to encrypt
      addRequestFilePart(writer, outputStream, fileIndicator, fileName, file, boundary, CRLF, charset);
    
      // End of multipart form data
      writer.append("--").append(boundary).append("--").append(CRLF).flush();

      // Handle the response
      int responseCode = connection.getResponseCode();
      System.out.println("ENCRYPTOR : " + responseCode);
      if (responseCode == HttpURLConnection.HTTP_OK) {
        // Read the response into a ByteArrayOutputStream
        InputStream inputStream = connection.getInputStream();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[4096]; // 4KB buffer size
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          byteArrayOutputStream.write(buffer, 0, bytesRead);
        }

        // Convert to byte array
        resultFile = byteArrayOutputStream.toByteArray();

        // Close streams
        byteArrayOutputStream.close();
        inputStream.close();
      }

      connection.disconnect();


    } catch (IOException e) {
      logger.error("Error while encrypting suspect profile", e);
    }

    return resultFile;
  }

  public Boolean storeSuspectProfile(String solidToken, String user, String suspectProfileId, byte[] resourceFile) {
    try {
      File file = new File(suspectProfileId);
      try (OutputStream os = new FileOutputStream(file)) {
        os.write(resourceFile);
      }

      String userPod = registry.getPodById(user);

      String uploadUrl = dataSharingPlatformAPI + "/api/resources/" + userPod + "%2Fsuspects%2F?toJSONld=true";

      System.out.println("uploadURL " + uploadUrl);
      HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");

      String boundary = "---------------------------" + System.currentTimeMillis();
      String charset = "UTF-8";
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
      connection.setRequestProperty("Cookie", solidToken);
      
      try (
          OutputStream output = connection.getOutputStream();
          PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);
          FileInputStream fileInputStream = new FileInputStream(file);
          BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
        // Text Field
        writer.append("--" + boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"index\"").append("\r\n");
        writer.append("Content-Type: text/plain; charset=" + charset).append("\r\n\r\n");
        writer.append(suspectProfileId).append("\r\n").flush();
        String fileName = file.getName() + ".zip.enc";
        // File Field
        writer.append("--" + boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"")
            .append("\r\n");
        writer.append("Content-Type: " + HttpURLConnection.guessContentTypeFromName(fileName)).append("\r\n\r\n")
            .flush();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
          output.write(buffer, 0, bytesRead);
        }
        output.flush();

        // End of multipart/form-data
        writer.append("\r\n--" + boundary + "--").append("\r\n").flush();
      }

      // Response
      int responseCode = connection.getResponseCode();
      System.out.println("RESPONSE CODE UPLOAD " + responseCode);

      connection.disconnect();
     
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
     return true;
  }

  // public void storeSuspectProfile(String solidToken, String user, String suspectProfileId, byte[] resourceFile) throws Exception {
  //   // Setup all-trusting SSL
  //   TrustManager[] trustAllCerts = new TrustManager[] {
  //       new X509TrustManager() {
  //           public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
  //           public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
  //           public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
  //       }
  //   };
  //   SSLContext sc = SSLContext.getInstance("TLS");
  //   sc.init(null, trustAllCerts, new java.security.SecureRandom());
  //   HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
  //   HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

  //   String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
  //   String LINE_FEED = "\r\n";

  //   URL url = new URL(uploadURL);
  //   HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
  //   conn.setUseCaches(false);
  //   conn.setDoOutput(true);
  //   conn.setDoInput(true);
  //   conn.setRequestMethod("POST");
  //   conn.setRequestProperty("Cookie", sessionCookie);
  //   conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

  //   try (OutputStream output = conn.getOutputStream();
  //        PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, "UTF-8"), true)) {

  //       // Add index field
  //       writer.append("--" + boundary).append(LINE_FEED);
  //       writer.append("Content-Disposition: form-data; name=\"index\"").append(LINE_FEED);
  //       writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
  //       writer.append(LINE_FEED).append(indexValue).append(LINE_FEED).flush();

  //       // Add file field
  //       writer.append("--" + boundary).append(LINE_FEED);
  //       writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"").append(LINE_FEED);
  //       writer.append("Content-Type: application/octet-stream").append(LINE_FEED);
  //       writer.append(LINE_FEED).flush();

  //       // Write the byte[] directly to the output stream
  //       output.write(fileBytes);
  //       output.flush();

  //       writer.append(LINE_FEED).flush();
  //       writer.append("--" + boundary + "--").append(LINE_FEED);
  //       writer.flush();
  //   }

  //   int responseCode = conn.getResponseCode();
  //   System.out.println("Upload Response Code: " + responseCode);

  //   try (BufferedReader in = new BufferedReader(new InputStreamReader(
  //           responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream()))) {
  //       String inputLine;
  //       StringBuilder response = new StringBuilder();
  //       while ((inputLine = in.readLine()) != null) {
  //           response.append(inputLine).append("\n");
  //       }
  //       System.out.println("Response Body:");
  //       System.out.println(response.toString());
  //   }
  // }


}