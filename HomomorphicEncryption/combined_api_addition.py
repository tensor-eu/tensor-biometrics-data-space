import seal
import numpy as np
import os
import inspect
from fastapi import FastAPI, HTTPException, UploadFile, File, Response
from pydantic import BaseModel
from typing import List
import zipfile
from io import BytesIO
import base64

# Define request model
class HEEncryptDistancesRequest(BaseModel):
    distances_matrix: List[List[int]]  # 6x64 matrix

class HEAggregateDistanceRequest(BaseModel):
    encrypted_values: List[str]  # 6 base64-encoded encrypted values

class HEAggregateDistanceBatchRequest(BaseModel):
    batch_encrypted_values: List[List[str]]  # Each inner list is one set of encrypted values

class SingleEncryptedValue(BaseModel):
    encrypted_value: str  # single base64-encoded encrypted value

class MultipleEncryptedValues(BaseModel):
    encrypted_values: List[str]  # List of base64-encoded encrypted values
    
    # 添加验证器
    class Config:
        @classmethod
        def schema_extra(cls, schema: dict) -> None:
            schema["example"] = {
                "encrypted_values": ["base64_string1", "base64_string2"]
            }

class DeepHashingSEAL:
    def __init__(self):
        # Setup SEAL for BFV scheme
        print("Setting up SEAL...")
        self.parms = seal.EncryptionParameters(seal.scheme_type.bfv)
        
        # Since we only need to handle three-digit numbers, we can use a smaller polynomial modulus
        self.poly_modulus_degree = 2048  # Reduced from 4096 to 2048
        self.parms.set_poly_modulus_degree(self.poly_modulus_degree)
        
        # Use a smaller coefficient modulus chain
        self.parms.set_coeff_modulus(seal.CoeffModulus.BFVDefault(self.poly_modulus_degree))
        
        # Minimum plain_modulus to support batching
        # For poly_modulus_degree=2048, use the smallest prime that satisfies the condition
        self.parms.set_plain_modulus(12289)  # 12289 is the smallest prime that satisfies the 2048 batching condition
        
        self.context = seal.SEALContext(self.parms)

        keys_dir = "./keys"
        if (os.path.exists(os.path.join(keys_dir, 'public_key')) and 
            os.path.exists(os.path.join(keys_dir, 'secret_key'))):
            print("Checking existing keys...")
            try:
                self.public_key = seal.PublicKey()
                self.public_key.load(self.context, os.path.join(keys_dir, 'public_key'))
                self.secret_key = seal.SecretKey()
                self.secret_key.load(self.context, os.path.join(keys_dir, 'secret_key'))
                
                # Verify if the keys are compatible with the current BFV scheme
                test_encryptor = seal.Encryptor(self.context, self.public_key)
                test_decryptor = seal.Decryptor(self.context, self.secret_key)
                test_encoder = seal.BatchEncoder(self.context)
               
                # Perform test encryption and decryption
                test_plain = test_encoder.encode([1, 0]) 
                test_cipher = test_encryptor.encrypt(test_plain)
                test_decrypted = test_decryptor.decrypt(test_cipher)
                test_decoded = test_encoder.decode(test_decrypted)
                
                if test_decoded[0] == 1: 
                    print("Existing keys are compatible with BFV scheme and loaded successfully.")
                    print("Creating Encryptor, Decryptor, Evaluator, and Encoder...")
                    self.encryptor = seal.Encryptor(self.context, self.public_key)
                    self.decryptor = seal.Decryptor(self.context, self.secret_key)
                    self.evaluator = seal.Evaluator(self.context)
                    self.encoder = seal.BatchEncoder(self.context)
                else:
                    raise ValueError("Existing keys are not compatible with current BFV scheme.")
            except Exception as e:
                print(f"Error loading or verifying existing keys: {str(e)}")
                print("Generating new keys...")
                self._generate_and_save_keys()
        else:
            print("Existing keys not found. Generating new keys...")
            self._generate_and_save_keys()

    def _generate_and_save_keys(self):
        print("Generating new keys...")
        self.keygen = seal.KeyGenerator(self.context)
        self.public_key = self.keygen.create_public_key()
        self.secret_key = self.keygen.secret_key()

        print("Saving new keys...")
        keys_dir = "./keys"
        os.makedirs(keys_dir, exist_ok=True)
        self.public_key.save(os.path.join(keys_dir, 'public_key'))
        self.secret_key.save(os.path.join(keys_dir, 'secret_key'))
        print("New keys generated and saved successfully.")

        print("Creating Encryptor, Decryptor, Evaluator, and Encoder...")
        self.encryptor = seal.Encryptor(self.context, self.public_key)
        self.decryptor = seal.Decryptor(self.context, self.secret_key)
        self.evaluator = seal.Evaluator(self.context)
        self.encoder = seal.BatchEncoder(self.context)

    def encrypt_single_value(self, value):
        """Encrypts a single integer value."""
        print(f"Encrypting single value: {value}")
        # Ensure input values are within reasonable limits
        if not (0 <= value <= 999):
            raise ValueError(f"Value {value} out of range (0-999)")
            
        # Use smaller vectors, only two slots needed
        vector = [value, 0]
        plain = self.encoder.encode(vector)
        encrypted = self.encryptor.encrypt(plain)
        print("Single value encryption successful.")
        return encrypted

    def decrypt_single_value(self, encrypted_data):
        """Decrypts a single encrypted value."""
        print("Decrypting single value...")
        try:
            plain = self.decryptor.decrypt(encrypted_data)
            decoded = self.encoder.decode(plain)
            # Only the first value is returned
            result = int(decoded[0]) # make sure the result is an integer
            print(f"Decrypted value: {result}")
            return result
        except Exception as e:
            print(f"Error during decryption: {str(e)}")
            raise

    def sum_encrypted_values(self, encrypted_values):
        """Sum multiple encrypted values homomorphically."""
        if not encrypted_values:
            raise ValueError("No encrypted values provided")
        
        print(f"Starting sum of {len(encrypted_values)} encrypted values")
        result = encrypted_values[0]
        
        try:
            for i in range(1, len(encrypted_values)):
                self.evaluator.add_inplace(result, encrypted_values[i])
        except Exception as e:
            print(f"Error during homomorphic addition: {str(e)}")
            raise
        
        print("Sum completed successfully")
        return result

    def save_encrypted_data_to_file(self, encrypted_data, file_path):
        """Saves encrypted data to a file."""
        print(f"Saving encrypted data to {file_path}...")
        encrypted_data.save(file_path)
        print("Encrypted data saved successfully.")

    def read_encrypted_data_from_file(self, file_path):
        """Reads encrypted data from a file."""
        print(f"Reading encrypted data from {file_path}...")
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"File not found: {file_path}")
            
        encrypted_data = seal.Ciphertext()
        try:
            encrypted_data.load(self.context, file_path)
            print(f"Encrypted data read successfully from {file_path}")
            return encrypted_data
        except Exception as e:
            print(f"Error reading encrypted data: {str(e)}")
            raise

    def serialize_encrypted(self, encrypted_data):
        """Serialize encrypted data to base64 string."""
        # Create a temporary file to save the encrypted data
        temp_file = "temp_cipher.dat"
        encrypted_data.save(temp_file)
        
        # Read the file and encode to base64
        with open(temp_file, 'rb') as f:
            base64_str = base64.b64encode(f.read()).decode('utf-8')
        
        # Clean up temporary file
        os.remove(temp_file)
        return base64_str

    def deserialize_encrypted(self, base64_str):
        """Deserialize base64 string to encrypted data."""
        # Create a temporary file to store the decoded data
        temp_file = "temp_cipher.dat"
        
        # Decode base64 and write to temporary file
        with open(temp_file, 'wb') as f:
            f.write(base64.b64decode(base64_str))
        
        # Load the ciphertext from the file
        encrypted_data = seal.Ciphertext()
        encrypted_data.load(self.context, temp_file)
        
        # Clean up temporary file
        os.remove(temp_file)
        return encrypted_data

# Initialize FastAPI and SEAL Deep Hashing
app = FastAPI()
seal_deep_hash = DeepHashingSEAL()

@app.post("/encrypt_facial_hash_representations")
async def encrypt_facial_hash_representations(request: HEEncryptDistancesRequest):
    try:
        # Process each row in the distances matrix
        encrypted_matrix = []
        for row in request.distances_matrix:
            encrypted_row = []
            for value in row:
                encrypted_value = seal_deep_hash.encrypt_single_value(value)
                # Serialize the encrypted value to base64
                encrypted_str = seal_deep_hash.serialize_encrypted(encrypted_value)
                encrypted_row.append(encrypted_str)
            encrypted_matrix.append(encrypted_row)
        
        return {"encrypted_matrix": encrypted_matrix}
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/calc_aggregated_distance")
async def calc_aggregated_distance(request: HEAggregateDistanceRequest):
    try:
        # Deserialize and decrypt each value
        encrypted_values = []
        for encrypted_str in request.encrypted_values:
            encrypted_data = seal_deep_hash.deserialize_encrypted(encrypted_str)
            encrypted_values.append(encrypted_data)
        
        # Sum encrypted values
        encrypted_sum = seal_deep_hash.sum_encrypted_values(encrypted_values)
        
        # Decrypt the sum
        final_sum = seal_deep_hash.decrypt_single_value(encrypted_sum)
    
        
        return {"aggregated_distance": final_sum}
    
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

   
# @app.post("/test_decrypt_single_value")
# async def test_decrypt_single_value(request: SingleEncryptedValue):
#     try:
#         # Deserialize the encrypted value
#         encrypted_data = seal_deep_hash.deserialize_encrypted(request.encrypted_value)
        
#         # Decrypt the value
#         decrypted_value = seal_deep_hash.decrypt_single_value(encrypted_data)
        
#         return {"decrypted_value": decrypted_value}
    
#     except Exception as e:
#         raise HTTPException(status_code=500, detail=str(e))

# @app.post("/test_decrypt_multiple_values")
# async def test_decrypt_multiple_values(request: MultipleEncryptedValues):
#     try:
#         print("Received request data type:", type(request.encrypted_values))
#         print("Number of values received:", len(request.encrypted_values))
#         print("First value type:", type(request.encrypted_values[0]) if request.encrypted_values else "No values")
        
#         if not isinstance(request.encrypted_values, list):
#             raise HTTPException(status_code=400, detail="Input must be a list of strings")
        
#         for idx, val in enumerate(request.encrypted_values):
#             if not isinstance(val, str):
#                 raise HTTPException(
#                     status_code=400, 
#                     detail=f"Value at index {idx} is not a string: {type(val)}"
#                 )
        
#         decrypted_values = []
#         for encrypted_str in request.encrypted_values:
#             encrypted_data = seal_deep_hash.deserialize_encrypted(encrypted_str)
#             decrypted_value = seal_deep_hash.decrypt_single_value(encrypted_data)
#             decrypted_values.append(decrypted_value)
        
#         return {
#             "decrypted_values": decrypted_values,
#             "count": len(decrypted_values)
#         }
    
#     except Exception as e:
#         print("Error details:", str(e)
#         raise HTTPException(status_code=500, detail=str(e))
