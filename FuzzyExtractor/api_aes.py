from fastapi import FastAPI, HTTPException, Body
from pydantic import BaseModel
from typing import List
import base64
import os
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import padding, serialization, hashes
from cryptography.hazmat.primitives.asymmetric import rsa, padding as asymmetric_padding, padding as rsa_padding
from main_v1 import encryption_process, decryption_process
import hashlib


app = FastAPI()

# Helper function for AES encryption
def aes_encrypt(key: bytes, plaintext: bytes) -> bytes:
    iv = os.urandom(16)
    cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=default_backend())
    encryptor = cipher.encryptor()
    padder = padding.PKCS7(128).padder()
    padded_data = padder.update(plaintext) + padder.finalize()
    ciphertext = encryptor.update(padded_data) + encryptor.finalize()
    return iv + ciphertext

# Helper function for AES decryption
def aes_decrypt(key: bytes, ciphertext: bytes) -> bytes:
    iv = ciphertext[:16]
    ciphertext = ciphertext[16:]
    cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=default_backend())
    decryptor = cipher.decryptor()
    unpadder = padding.PKCS7(128).unpadder()
    padded_data = decryptor.update(ciphertext) + decryptor.finalize()
    plaintext = unpadder.update(padded_data) + unpadder.finalize()
    return plaintext


# ============================================================
# The API for Indexer: No. 1: encrypt_model_weights
# ============================================================

class ModelWeights(BaseModel):
    model_weights: List[List[List[float]]]  

class EncryptedWeights(BaseModel):
    encrypted_model_weights: str

SHARED_KEY = os.environ.get("SHARED_AES_KEY", "my_32_byte_secret_key_val_123431").encode()

if len(SHARED_KEY) not in {16, 24, 32}:
    raise ValueError(f"Invalid key length: {len(SHARED_KEY)} bytes. Key must be 16, 24, or 32 bytes.")

@app.post("/encrypt_model_weights")
async def encrypt_model_weights(model_weights: ModelWeights):
    plaintext = str(model_weights.model_weights).encode()
    ciphertext = aes_encrypt(SHARED_KEY, plaintext) 
    return {"encrypted_model_weights": base64.b64encode(ciphertext).decode()}

# ============================================================
# The API for Indexer: No. 2: decrypt_model_weights
# ============================================================


@app.post("/decrypt_model_weights")
async def decrypt_model_weights(encrypted_model_weights: EncryptedWeights):
    ciphertext = base64.b64decode(encrypted_model_weights.encrypted_model_weights)
    plaintext = aes_decrypt(SHARED_KEY, ciphertext)
    decrypted_model_weights = eval(plaintext.decode())
    return {"model_weights": decrypted_model_weights}




# ============================================================
# The API for Indexer: No. 3: encrypt_facial_hash_indexes
# ============================================================

# Define a new request model to include both hash indexes and lea_id
class HashIndexesRequest(BaseModel):
    hash_indexes_face: List[int]
    lea_id: str

# Assume we have a dictionary containing dedicated AES keys for each LEA
LEA_KEYS = {
    "lea0": b"my_32_byte_secret_key_lea_000000",
    "lea1": b"my_32_byte_secret_key_lea_000001",
    "lea2": b"my_32_byte_secret_key_lea_000002",
    "lea3": b"my_32_byte_secret_key_lea_000003",
    "lea4": b"my_32_byte_secret_key_lea_000004",
    "lea5": b"my_32_byte_secret_key_lea_000005",
    "lea6": b"my_32_byte_secret_key_lea_000006",
    "lea7": b"my_32_byte_secret_key_lea_000007",
    "lea8": b"my_32_byte_secret_key_lea_000008",
    "lea9": b"my_32_byte_secret_key_lea_000009",
}

# Ensure all keys conform to the AES key length requirements
for key in LEA_KEYS.values():
    if len(key) not in {16, 24, 32}:
        raise ValueError(f"Invalid key length: {len(key)} bytes. Key must be 16, 24, or 32 bytes.")

@app.post("/encrypt_facial_hash_indexes")
async def encrypt_facial_hash_indexes(request: HashIndexesRequest):
    # Validate that the input list contains exactly 6 integers
    if len(request.hash_indexes_face) != 6:
        raise HTTPException(status_code=400, detail="hash_indexes_face must contain exactly 6 integers.")

    # Retrieve the dedicated AES key for the specified LEA
    key = LEA_KEYS.get(request.lea_id)
    if not key:
        raise HTTPException(status_code=400, detail="Invalid LEA ID. Cannot find corresponding encryption key.")

    # Convert the list of hash indexes to a byte string for encryption
    plaintext = str(request.hash_indexes_face).encode()
    ciphertext = aes_encrypt(key, plaintext)

    # Return the encrypted data along with the LEA ID
    return {
        "encrypted_hash_indexes_face": base64.b64encode(ciphertext).decode(),
        "lea_id": request.lea_id
    }

# ============================================================
# The API for Indexer: No. 4: decrypt_facial_hash_indexes
# ============================================================

# Define a new request model for decryption
class DecryptHashIndexesRequest(BaseModel):
    encrypted_hash_indexes_face: str
    lea_id: str

@app.post("/decrypt_facial_hash_indexes")
async def decrypt_facial_hash_indexes(request: DecryptHashIndexesRequest):
    # Retrieve the dedicated AES key for the specified LEA
    key = LEA_KEYS.get(request.lea_id)
    if not key:
        raise HTTPException(status_code=400, detail="Invalid LEA ID. Cannot find corresponding decryption key.")

    try:
        # Decode the Base64-encoded ciphertext
        ciphertext = base64.b64decode(request.encrypted_hash_indexes_face)
        
        # Decrypt the ciphertext
        plaintext = aes_decrypt(key, ciphertext)
        
        # Convert the decrypted byte string back to a list of integers
        decrypted_indexes = eval(plaintext.decode())
        
        # Validate that the decrypted list contains exactly 6 integers
        if len(decrypted_indexes) != 6 or not all(isinstance(x, int) for x in decrypted_indexes):
            raise ValueError("Decrypted data is not a valid list of 6 integers.")
        
        # Return the decrypted hash indexes
        return {
            "hash_indexes_face": decrypted_indexes,
            "lea_id": request.lea_id
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Decryption failed: {str(e)}")

class BatchDecryptRequest(BaseModel):
    encrypted_hash_indexes_face: List[str]  # multiple encrypted facial hash indexes
    lea_id: str

# ============================================================
# The API for Indexer: No. 9: encrypt_face_data
# ============================================================

# Define a request model for the input
class ImageDataRequest(BaseModel):
    image_data: str

@app.post("/encrypt_face_data")
async def encrypt_face_data(request: ImageDataRequest):
    key = os.urandom(32)  # Generate a 256-bit key

    # Decode the Base64-encoded image, with error handling
    try:
        plaintext = base64.b64decode(request.image_data)
    except Exception as e:
        raise HTTPException(status_code=400, detail="Invalid Base64-encoded image data.")

    # Encrypt the decoded image data
    ciphertext = aes_encrypt(key, plaintext)

    # Return the encrypted image and the key, both Base64-encoded
    return {
        "encrypted_image": base64.b64encode(ciphertext).decode(),
        "key": base64.b64encode(key).decode()
    }


# ============================================================
# The API for Indexer: No. 10: decrypt_face_data
# ============================================================

class DecryptImageRequest(BaseModel):
    encrypted_image: str
    key: str

@app.post("/decrypt_face_data")
async def decrypt_face_data(request: DecryptImageRequest):
    try:
        # Decode the Base64-encoded ciphertext and key
        ciphertext = base64.b64decode(request.encrypted_image)
        key_bytes = base64.b64decode(request.key)
        
        # Perform decryption
        plaintext = aes_decrypt(key_bytes, ciphertext)
        
        # Return the decrypted image as a Base64-encoded string
        return {"decrypted_image": base64.b64encode(plaintext).decode()}
    except base64.binascii.Error:
        raise HTTPException(status_code=400, detail="Invalid Base64 input.")
    except ValueError as e:
        raise HTTPException(status_code=400, detail=f"Decryption failed: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Unexpected error: {str(e)}")


# ============================================================
# The API for Indexer: No. 11: encrypt_key_to_be_shared
# ============================================================

class EncryptKeyRequest(BaseModel):
    key: str
    public_key_pem: str

@app.post("/encrypt_key_to_be_shared")
async def encrypt_key_to_be_shared(request: EncryptKeyRequest):
    try:
        # Log input data
        print(f"Input key: {request.key}")
        print(f"Input public key hash: {hashlib.sha256(request.public_key_pem.encode()).hexdigest()}")
        
        # Decode the symmetric key from Base64
        key_bytes = base64.b64decode(request.key)
        print(f"Decoded key bytes: {key_bytes.hex()}")
        
        # Load the RSA public key from PEM format
        public_key = serialization.load_pem_public_key(request.public_key_pem.encode())
        
        # Use PKCS1v15 padding for deterministic encryption
        encrypted_key = public_key.encrypt(
            key_bytes,
            rsa_padding.PKCS1v15()
        )
        
        # Log the encrypted key
        print(f"Encrypted key: {encrypted_key.hex()}")
        
        # Return the encrypted key, encoded in Base64
        result = base64.b64encode(encrypted_key).decode()
        print(f"Returned encrypted key (base64): {result}")
        return {"encrypted_key": result}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Encryption failed: {str(e)}")

# ============================================================
# The API for Indexer: No. 12: decrypt_the_shared_key
# ============================================================

class DecryptKeyRequest(BaseModel):
    encrypted_key: str
    private_key_pem: str

@app.post("/decrypt_the_shared_key")
async def decrypt_the_shared_key(request: DecryptKeyRequest):
    try:
        # Decode the encrypted symmetric key from Base64
        encrypted_key_bytes = base64.b64decode(request.encrypted_key)

        # Load the RSA private key from PEM format
        private_key = serialization.load_pem_private_key(request.private_key_pem.encode(), password=None)

        # Decrypt using PKCS1v15 padding
        decrypted_key = private_key.decrypt(
            encrypted_key_bytes,
            rsa_padding.PKCS1v15()
        )

        # Return the decrypted key, encoded in Base64
        return {"decrypted_key": base64.b64encode(decrypted_key).decode()}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Decryption failed: {str(e)}")


# ============================================================
# The API for Indexer: No. 13: get_LEA_public_key
# ============================================================

# Generate and store RSA key pairs for LEAs 0-9
keys_dir = os.path.join(os.getcwd(), 'keys', 'rsa')
os.makedirs(keys_dir, exist_ok=True)

for i in range(10):
    lea_id = f"lea{i}"
    private_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
        backend=default_backend()
    )
    public_key = private_key.public_key()
    
    # Save private key
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption()
    )
    with open(os.path.join(keys_dir, f"{lea_id}_private.pem"), 'wb') as f:
        f.write(private_pem)
    
    # Save public key
    public_pem = public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )
    with open(os.path.join(keys_dir, f"{lea_id}_public.pem"), 'wb') as f:
        f.write(public_pem)

@app.get("/get_LEA_public_key/{lea_id}")
async def get_LEA_public_key(lea_id: str):
    public_key_path = os.path.join(keys_dir, f"{lea_id}_public.pem")
    if not os.path.exists(public_key_path):
        raise HTTPException(status_code=400, detail="Invalid LEA ID")
    
    with open(public_key_path, 'rb') as f:
        public_key = f.read()
    
    return {"public_key": public_key.decode()}


# ============================================================
# The API for Indexer: No. 14: get_LEA_private_key
# ============================================================

@app.get("/get_LEA_private_key/{lea_id}")
async def get_LEA_private_key(lea_id: str):
    private_key_path = os.path.join(keys_dir, f"{lea_id}_private.pem")
    if not os.path.exists(private_key_path):
        raise HTTPException(status_code=400, detail="Invalid LEA ID")
    
    with open(private_key_path, 'rb') as f:
        private_key = f.read()
    
    return {"private_key": private_key.decode()}







