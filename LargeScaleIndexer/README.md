## Component

Large-Scale Indexer

---

## Endpoints

### 1. Service Health Check

Check whether the service has been properly launched and is running.

- **URL:** `/isAlive`
- **Method:** `GET`
- **Description:** Returns a simple response indicating that the service is alive.

**Example Request:**

```http
GET /isAlive
```

### 2. Calculate Hash for Evidence

This endpoint allows you to calculate the hash of a piece of evidence. The hash can then be used for searching or matching purposes in other endpoints.

- **URL:** `/calculateHashForSearching`
- **Method:** `POST`
- **Description:** Computes a hash for an existing evidence file in the Solid Pod to enable searching and matching.

---

## Request

**Headers:**

```http
Content-Type: application/json
```

## Request Body

```json
{
    "type": {
        "image": boolean,
        "fingerprint": boolean,
        "voice": boolean
    },
    "full_facial_image_url": string,
    "full_fingerprint_url": string,
    "full_voice_url": string
}

```

### 3. Search for Matching Images

Search for similar images in the LEA’s private database.

- **URL:** `/searchForMatches`
- **Method:** `POST`
- **Description:** Given a hash or image, searches the private LEA database for matches.

---

## Request

**Headers:**

```http
Content-Type: application/json
```

## Request Body

The body should include the response from the calculateHashForSearching endpoint and increment it with "from" and "to" fields.

```json
{
  "from": "REQUESTOR_LEA_ID",
  "to": "OWNER_LEA_ID",
  "bound": [[14], [22], [47], [57], [17], [19]],
  "face": [["", "", ""]],
  "fingerprint": [],
  "type": {
    "FINGERPRINT": false,
    "IMAGE": true,
    "VOICE": false
  },
  "voice": []
}
```
