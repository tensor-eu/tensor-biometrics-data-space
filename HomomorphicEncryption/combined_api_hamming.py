import seal
import numpy as np
import os
from pydantic import BaseModel
from typing import List, Union
from fastapi import FastAPI, HTTPException, File, UploadFile
from fastapi.responses import FileResponse
from uuid import uuid4
import base64

app = FastAPI()

class DeepHashingSEAL:
    def __init__(self):
        # Setup SEAL for BFV scheme
        print("Setting up SEAL...")
        self.parms = seal.EncryptionParameters(seal.scheme_type.bfv)
        self.poly_modulus_degree = 4096
        self.parms.set_poly_modulus_degree(self.poly_modulus_degree)
        self.parms.set_coeff_modulus(seal.CoeffModulus.BFVDefault(self.poly_modulus_degree))
        self.parms.set_plain_modulus(seal.PlainModulus.Batching(self.poly_modulus_degree, 20))
        self.context = seal.SEALContext(self.parms)
        
        # Check if keys exist and are compatible with current BFV scheme
        if os.path.exists('public_key') and os.path.exists('secret_key') and os.path.exists('relin_keys'):
            print("Checking existing keys...")
            try:
                self.public_key = seal.PublicKey()
                self.public_key.load(self.context, 'public_key')
                self.secret_key = seal.SecretKey()
                self.secret_key.load(self.context, 'secret_key')
                self.relin_keys = seal.RelinKeys()
                self.relin_keys.load(self.context, 'relin_keys')
                
                # Verify keys are compatible with current BFV scheme
                test_encryptor = seal.Encryptor(self.context, self.public_key)
                test_decryptor = seal.Decryptor(self.context, self.secret_key)
                test_evaluator = seal.Evaluator(self.context)
                
                # Perform a test encryption and decryption
                test_plain = seal.Plaintext("1")
                test_cipher = test_encryptor.encrypt(test_plain)
                test_decrypted = test_decryptor.decrypt(test_cipher)
                
                if test_plain.to_string() == test_decrypted.to_string():
                    print("Existing keys are compatible and loaded successfully.")
                    self._initialize_components()
                else:
                    raise ValueError("Existing keys are not compatible with current BFV scheme.")
            except Exception as e:
                print(f"Error loading or verifying existing keys: {str(e)}")
                print("Generating new keys...")
                self._generate_and_save_keys()
        else:
            print("Existing keys not found. Generating new keys...")
            self._generate_and_save_keys()

    def _initialize_components(self):
        print("Initializing Encryptor, Decryptor, Evaluator, and Encoder...")
        self.encryptor = seal.Encryptor(self.context, self.public_key)
        self.decryptor = seal.Decryptor(self.context, self.secret_key)
        self.evaluator = seal.Evaluator(self.context)
        self.encoder = seal.BatchEncoder(self.context)

    def _generate_and_save_keys(self):
        print("Generating new keys...")
        keygen = seal.KeyGenerator(self.context)
        self.public_key = keygen.create_public_key()
        self.secret_key = keygen.secret_key()
        self.relin_keys = keygen.create_relin_keys()

        print("Saving new keys...")
        self.public_key.save('public_key')
        self.secret_key.save('secret_key')
        self.relin_keys.save('relin_keys')
        print("New keys generated and saved successfully.")
        self._initialize_components()

    def encrypt_hash(self, data):
        """Encrypts a 64-bit hash."""
        print("Encrypting hash...")
        plain = self.encoder.encode(data)
        encrypted = self.encryptor.encrypt(plain)
        print("Hash encryption successful.")
        return encrypted

    def compute_hamming_distance(self, encrypted_a, encrypted_b):
        """Computes the Hamming distance between two encrypted hashes homomorphically."""
        print("Computing Hamming distance homomorphically...")
        # XOR operation
        diff = self.evaluator.sub(encrypted_a, encrypted_b)
        print("Hamming distance computation complete.")
        return diff

    def decrypt(self, encrypted_data):
        """Decrypts the encrypted data and returns the plaintext."""
        print("Decrypting data...")
        plain = self.decryptor.decrypt(encrypted_data)
        decoded = self.encoder.decode(plain)
        print("Decryption and decoding complete.")
        
        # Convert to standard Python integers
        decoded_list = [int(x) for x in decoded]
        return decoded_list

    def save_encrypted_data_to_file(self, encrypted_data, file_path):
        """Save encrypted data to a file."""
        print(f"Saving encrypted data to {file_path}...")
        # Ensure the directory exists
        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        encrypted_data.save(file_path)
        print("Encrypted data saved successfully.")
        
    def read_encrypted_data_from_file(self, file_path):
        """Reads encrypted data from a file."""
        print(f"Reading encrypted data from {file_path}...")
        encrypted_data = seal.Ciphertext()
        encrypted_data.load(self.context, file_path)
        print("Encrypted data read successfully.")
        return encrypted_data

    def serialize_encrypted(self, encrypted_data):
        """Serialize encrypted data to base64 string."""
        temp_file = "temp_cipher.dat"
        encrypted_data.save(temp_file)
        
        with open(temp_file, 'rb') as f:
            base64_str = base64.b64encode(f.read()).decode('utf-8')
        
        os.remove(temp_file)
        return base64_str

    def deserialize_encrypted(self, base64_str):
        """Deserialize base64 string to encrypted data."""
        temp_file = "temp_cipher.dat"
        
        with open(temp_file, 'wb') as f:
            f.write(base64.b64decode(base64_str))
        
        encrypted_data = seal.Ciphertext()
        encrypted_data.load(self.context, temp_file)
        
        os.remove(temp_file)
        return encrypted_data

# Initialize SEAL Deep Hashing
seal_deep_hash = DeepHashingSEAL()

class HEEncryptHashRequest(BaseModel):
    finger_or_voice_hash: List[int]

class HEHammingDistanceRequest(BaseModel):
    encrypted_values: List[str]  # List of base64-encoded encrypted values

class HEHammingDistanceBatchRequest(BaseModel):
    encrypted_values: List[List[str]]

@app.post("/encrypt_finger_or_voice_hash_representations")
async def encrypt_finger_or_voice_hash_representations(request: HEEncryptHashRequest):
    try:
        encrypted_data = seal_deep_hash.encrypt_hash(request.finger_or_voice_hash)
        # Convert to base64 string
        encrypted_str = seal_deep_hash.serialize_encrypted(encrypted_data)
        return {"encrypted_data": encrypted_str}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Encryption failed: {str(e)}")

@app.post("/calc_hamming_distance")
async def calc_hamming_distance(request: HEHammingDistanceRequest):
    try:
        if len(request.encrypted_values) != 2:
            raise HTTPException(status_code=400, detail="Exactly two encrypted values are required")
        
        # Deserialize encrypted values
        encrypted_hash_A = seal_deep_hash.deserialize_encrypted(request.encrypted_values[0])
        encrypted_hash_B = seal_deep_hash.deserialize_encrypted(request.encrypted_values[1])
        
        # Calculate Hamming distance homomorphically
        encrypted_hamming_distance = seal_deep_hash.compute_hamming_distance(encrypted_hash_A, encrypted_hash_B)
        
        # Decrypt the final result
        decrypted_distance = seal_deep_hash.decrypt(encrypted_hamming_distance)
        hamming_distance = sum(abs(x) for x in decrypted_distance)
        
        return {"message": "Hamming distance calculated successfully.", "hamming_distance": hamming_distance}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Hamming distance computation failed: {str(e)}")

