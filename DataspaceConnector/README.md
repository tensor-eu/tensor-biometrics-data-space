## Component

Data Space Connector

# Deployment

# Endpoints

## 1. Initialization of Matching Process

This endpoint performs the initialization endpoint receives biometric data for the searching process. It queries the Metadata Broker to discover registered Connectors of each trusted LEA participating in the Biometrics Data Space. Once discovered, it triggers the local comparison endpoint on each Connector, providing the captured biometric data of the potential suspect. The endpoint returns an aggregated list of similarity measurements per biometric modality from all queried Connectors. This list can then be used by the end user to select the suspect profile to access.

- **URL:** `https://{CONNECTOR_IP}:{CONNECTOR_PORT}/match/init`
- **Method:** `POST`
- **Description:** Retrieves the list of participant Data Space Connectors with LEA IDs, LEA Connector IPs and further information.

## Request Body

```json
{
     "providerId": "Owner LEA ID (string)",
     "sampleFaceUrl": "The URL of the face biometric sample",,
     "sampleFingerprintUrl": "The URL of the fingerprint biometric sample",,
     "sampleVoiceUrl": "The URL of the voice biometric sample",
}

```

---

## 2. Local Comparison

The local comparison is executed by each Connector queried during the initialization process. It integrates with the Large-Scale Indexing component, which calculates hashes for the provided biometric data and then uses these hashes to compare and retrieve similarity scores for potential matches of suspects. The endpoint returns a list of matching scores for each potential suspect found in the local database to the initialization matching endpoint.

- **URL:** `https://{CONNECTOR_IP}:{CONNECTOR_PORT}/match/local`
- **Method:** `POST`
- **Description:** Retrieves the list of similarity scores for the provided biometric evidence in the specific LEA where searched.

## Request Body

```json
{
     "providerId": "Owner LEA ID (string)",
     "consumerId": "Requestor LEA ID (string)",
     "sampleFaceUrl": "The URL of the face biometric sample",
     "sampleFingerprintUrl": "The URL of the fingerprint biometric sample",,
     "sampleVoiceUrl": "The URL of the voice biometric sample",
}

```

---

## 3. Blockchain Access Request

To ensure transparency and traceability, all the attempts to access external biometric data of potential suspects from different LEAs databases must be registered to the blockchain through the Data Sharing Platform. This request returns all the necessary information which will be used in the external Smart Wallet to sign the request access transaction by the requestor for non-repudiation purposes.

- **URL:** `https://{CONNECTOR_IP}:{CONNECTOR_PORT}/dsp/request`

- **Method:** `POST`
- **Request Body:** `multipart/form-data`
- **Description:** Generates a new request to the Blockchain and returns information to be used in the Smart Wallet signing transaction.

### Request Body Fields

| Field Name                   | Type   | Description                                                               |
| ---------------------------- | ------ | ------------------------------------------------------------------------- |
| `providerId`                 | string | Owner LEA ID                                                              |
| `consumerId`                 | string | Requestor LEA ID                                                          |
| `faceFile`                   | file   | Facial image of potential suspect (`.png`, `.jpg`, `.jpeg`)               |
| `fingerprintFile`            | file   | Fingerprint image of potential suspect (`.png`, `.jpg`, `.jpeg`, `.tiff`) |
| `voiceFile`                  | file   | Voice record of potential suspect (`.flac`)                               |
| `suspectProfileId`           | string | The suspect profile ID requested for access                               |
| `facialImageSimilarityScore` | string | The face similarity score for the requested suspect profile               |
| `fingerprintSimilarityScore` | string | The fingerprint similarity score                                          |
| `voiceprintSimilarityScore`  | string | The voice similarity score                                                |
| `descriptiveText`            | string | Text describing the criminal case                                         |

---

## 4. Display Biometric Evidence of incoming Access Request

The Owner LEA receives a notification about incoming access requests to the suspect profiles of their local database. Then, the user is called to visually verify a potential match by displaying the biometirc samples shared by the requestor LEA and proceed to the response.

- **URL:** `https://{CONNECTOR_IP}:{CONNECTOR_PORT}/display-evidence"`
- **Method:** `POST`
- **Description:** Retrieves the shared biometric data and returns those as base64 files.

## Request Body

```json
{
  "suspectProfileID": "The suspect profile ID requested for access (string)",
  "requestorID": "Requestor LEA ID (string)",
  "requestID": "Blockchain DSP Access Request ID (string)"
}
```

---

## 5. Blockchain Access Response

To ensure transparency and traceability, all the attempts to respond to incoming access requests for potential suspects from different LEAs databases must be registered to the blockchain through the Data Sharing Platform. This request returns all the necessary information which will be used in the external Smart Wallet to sign the response transaction by the owner for non-repudiation purposes.

- **URL:** `https://{CONNECTOR_IP}:{CONNECTOR_PORT}/display-evidence"`
- **Method:** `POST`
- **Description:** Generates a new response to a specific request by either accepting or rejecting the access and returns the blockchain data to be used by the external Smart Wallet.

## Request Body

```json
{
  "solidPod": "The solid pod name of the owner (string)",
  "providerId": "Owner LEA ID (string)",
  "consumerId": "Requestor LEA ID (string)",
  "requestID": "Blockchain DSP Access Request ID (string)",
  "recipientAddress": "The Solid recipient address (string)",
  "suspectProfileId": "The suspect profile ID requested for access (string)",
  "duration": "The duration to be accessed in seconds (number)",
  "accessType": "The access type determines the action to be performed in the suspect profile (default: read)",
  "responseType": "The response type of the request (accept/deny)",
  "encryptionKey": "The encryption AES-256 key of the exchanged data",
  "tos": "A descriptive text as justification for the responseType"
}
```

---

## 6. Data Exchange

The data exchange takes place under the secure configurations of the Data Space Connectors of each LEA. The data shared between two connectors are encrypted with AES-256 encryption and they are decrypted once they are received in the Requestor Connector.

- **URL:** `https://{CONNECTOR_IP}:{CONNECTOR_PORT}/proxy"`
- **Method:** `POST`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Basic <Base64-encoded-username:password>`
- **Description:** Performs data exchange which returns the suspect profile with information about the suspect and the biometric data in base64 encoding.

## Request Body

```json
{
    "multipart": "form",
    "Forward-To": "https://{OWNER_CONNECTOR_IP}/data",
    "messageType": "ArtifactRequestMessage",
    "requestedArtifact": "Requested suspect profile ID (string)"
}
---
```
