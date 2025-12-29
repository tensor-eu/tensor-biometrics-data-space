from fastapi import FastAPI, UploadFile, File
from pydantic import BaseModel
from deep_hashing_seal import DeepHashingSEAL
import os

app = FastAPI()

# Initialize the SEAL Deep Hashing
seal_deep_hash = DeepHashingSEAL()

# File paths
raw_data_folder = "../raw_data"
encrypted_data_folder = "../encrypted_data"
key_folder = "../keys"

# Ensure the folders exist
os.makedirs(encrypted_data_folder, exist_ok=True)
os.makedirs(key_folder, exist_ok=True)

# Define request and response models
class DataModel(BaseModel):
    data1: str
    data2: str

class HashModel(BaseModel):
    hash1: str
    hash2: str

class SimilarityResponse(BaseModel):
    similarity_score: float


@app.post("/hash_encrypt/", response_model=HashModel)
async def hash_and_encrypt(data: DataModel):
    """Hash and encrypt the provided data."""
    hash1 = seal_deep_hash.deep_hash(data.data1)
    hash2 = seal_deep_hash.deep_hash(data.data2)

    encrypted_hash1 = seal_deep_hash.encrypt(hash1)
    encrypted_hash2 = seal_deep_hash.encrypt(hash2)

    return {"hash1": encrypted_hash1, "hash2": encrypted_hash2}


@app.post("/compare/", response_model=SimilarityResponse)
async def compare_homomorphically(data: DataModel):
    """Encrypts and compares the two provided data strings homomorphically."""
    encrypted_hash1 = seal_deep_hash.encrypt(data.data1)
    encrypted_hash2 = seal_deep_hash.encrypt(data.data2)

    # Perform homomorphic comparison
    similarity_score = seal_deep_hash.compare_homomorphically(encrypted_hash1, encrypted_hash2, data.data1, data.data2)

    return {"similarity_score": similarity_score}


@app.post("/upload/")
async def upload_file(file1: UploadFile = File(...), file2: UploadFile = File(...)):
    """Uploads two files, encrypts the content, and saves the encrypted files."""
    # Read files
    data1 = (await file1.read()).decode('utf-8')
    data2 = (await file2.read()).decode('utf-8')

    encrypted_hash1 = seal_deep_hash.encrypt(data1)
    encrypted_hash2 = seal_deep_hash.encrypt(data2)

    # Save the encrypted files
    save_encrypted_data_to_file(encrypted_hash1, os.path.join(encrypted_data_folder, "encrypted_data1.dat"))
    save_encrypted_data_to_file(encrypted_hash2, os.path.join(encrypted_data_folder, "encrypted_data2.dat"))

    return {"message": "Files encrypted and saved successfully."}


@app.post("/save_keys/")
async def save_keys():
    """Saves the generated public and secret keys."""
    seal_deep_hash.save_keys(os.path.join(key_folder, "public_key.key"), os.path.join(key_folder, "secret_key.key"))
    return {"message": "Keys saved successfully."}


def save_encrypted_data_to_file(encrypted_data, file_path):
    """Save encrypted data to a file."""
    print(f"Saving encrypted data to {file_path}...")
    if isinstance(encrypted_data, seal.Ciphertext):
        # If it's a Ciphertext object, use SEAL's save method to save it
        encrypted_data.save(file_path)
    else:
        # Otherwise save it as plain text
        with open(file_path, 'w') as f:
            f.write(str(encrypted_data))
    print(f"Encrypted data saved to {file_path}.")
