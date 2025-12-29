from fastapi import FastAPI, UploadFile, File, Response, HTTPException
from fastapi.responses import FileResponse
import shutil
from main_v1_zip import encryption_process, decryption_process

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import hashes
import base64

from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend
from fastapi.responses import FileResponse
import os


app = FastAPI()

# Function to save the uploaded file to a specified directory
def save_upload_file(upload_file: UploadFile, destination: str):
    try:
        with open(destination, "wb") as buffer:
            shutil.copyfileobj(upload_file.file, buffer)
    finally:
        upload_file.file.close()

# Encryption endpoint (only accepts enroll_image and image_to_encrypt)
@app.post("/encrypt")
async def encrypt_image(mode: str, enroll_image: UploadFile, file_to_encrypt: UploadFile):
    enroll_image_path = f"./biometric_FE/{enroll_image.filename}"
    file_to_encrypt_path = f"./raw_data_AES/{file_to_encrypt.filename}"

    # Save the uploaded files
    save_upload_file(enroll_image, enroll_image_path)
    save_upload_file(file_to_encrypt, file_to_encrypt_path)

    try:
        # Call encryption_process to perform the encryption
        ciphertext, iv, helper_data, adjusted_key = encryption_process(mode, enroll_image_path, file_to_encrypt_path)
        
        # Save the encryption result to a file
        encrypted_file_path = f"./encrypted/{file_to_encrypt.filename}.enc"
        with open(encrypted_file_path, "wb") as f:
            f.write(iv + ciphertext)  # Write IV and ciphertext into the file

        # Save helper_data and adjusted_key for decryption
        helper_data_file_path = f"./encrypted/{file_to_encrypt.filename}_helper_data.bin"
        with open(helper_data_file_path, "wb") as f:
            f.write(helper_data)
        
        key_file_path = f"./encrypted/{file_to_encrypt.filename}_key.bin"
        with open(key_file_path, "wb") as f:
            f.write(adjusted_key)

        # Return the encrypted file as a response
        return FileResponse(encrypted_file_path, media_type='application/octet-stream', filename=f"{file_to_encrypt.filename}.enc")
    
    except Exception as e:
        return {"error": "Encryption failed", "details": str(e)}

# Decryption endpoint (accepts verify_image and encrypted file)
@app.post("/decrypt")
async def decrypt_file(mode: str, verify_image: UploadFile, encrypted_file: UploadFile):
    verify_image_path = f"./biometric_FE/{verify_image.filename}"
    encrypted_file_path = f"./encrypted/{encrypted_file.filename}"
    original_filename = encrypted_file.filename.split('.enc')[0]
    file_extension = os.path.splitext(original_filename)[1].lower()

    # Save the uploaded files
    save_upload_file(verify_image, verify_image_path)
    save_upload_file(encrypted_file, encrypted_file_path)

    try:
        # Read IV and ciphertext from the encrypted file
        with open(encrypted_file_path, "rb") as f:
            iv = f.read(16)  # Assuming IV is 16 bytes long
            ciphertext = f.read()  # Read the remaining ciphertext

        # Read helper_data and adjusted_key from files
        helper_data_file_path = f"./encrypted/{original_filename}_helper_data.bin"
        with open(helper_data_file_path, "rb") as f:
            helper_data = f.read()
        
        key_file_path = f"./encrypted/{original_filename}_key.bin"
        with open(key_file_path, "rb") as f:
            adjusted_key = f.read()

        # Call decryption_process to perform the decryption
        decrypted_file_path = decryption_process(mode, verify_image_path, iv, ciphertext, helper_data, adjusted_key)

        # Set media type for ZIP files
        media_type = 'application/zip' if file_extension == '.zip' else (
            'audio/flac' if file_extension == '.flac' else 'image/png'
        )
        
        # Return the decrypted file
        return FileResponse(
            decrypted_file_path, 
            media_type=media_type,
            filename=f"decrypted{file_extension}"
        )
    
    except Exception as e:
        return {"error": "Decryption failed", "details": str(e)}




# # Function to save key to file
# def save_key_to_file(key_data: bytes, filename: str):
#     # Ensure the directory exists
#     if not os.path.exists('keys'):
#         os.makedirs('keys')
    
#     # Write the key to a file
#     key_file_path = f'./keys/{filename}'
#     with open(key_file_path, 'wb') as f:
#         f.write(key_data)
#     return key_file_path

# # Generate RSA key pair endpoint
# @app.get("/generate-rsa-key-pair")
# async def generate_rsa_key_pair():
#     # Generate a private key (2048 bits)
#     private_key = rsa.generate_private_key(
#         public_exponent=65537,
#         key_size=2048,
#         backend=default_backend()
#     )
    
#     # Serialize private key to PEM format
#     private_key_pem = private_key.private_bytes(
#         encoding=serialization.Encoding.PEM,
#         format=serialization.PrivateFormat.TraditionalOpenSSL,
#         encryption_algorithm=serialization.NoEncryption()
#     ).decode('utf-8')
    
#     # Serialize public key to PEM format
#     public_key = private_key.public_key()
#     public_key_pem = public_key.public_bytes(
#         encoding=serialization.Encoding.PEM,
#         format=serialization.PublicFormat.SubjectPublicKeyInfo
#     ).decode('utf-8')
    
#     # Return both keys in PEM format
#     return {
#         "private_key": private_key_pem,
#         "public_key": public_key_pem
#     }



# Key retrieval API (returns the key encrypted with the provided RSA public key)
@app.post("/get-encrypted-key/{filename}")
async def get_encrypted_key(filename: str, rsa_public_key: str):
    key_file_path = f"./encrypted/{filename}_key.bin"
    
    try:
        # Read the AES key from the file
        with open(key_file_path, "rb") as f:
            adjusted_key = f.read()
        
        # Convert the RSA public key string to a usable public key object
        try:
            public_key = serialization.load_pem_public_key(rsa_public_key.encode('utf-8'))
        except ValueError:
            raise HTTPException(status_code=400, detail="Invalid public key format")

        # Encrypt the AES key using the RSA public key
        encrypted_key = public_key.encrypt(
            adjusted_key,
            padding.OAEP(
                mgf=padding.MGF1(algorithm=hashes.SHA256()),
                algorithm=hashes.SHA256(),
                label=None
            )
        )
        
        # Return the RSA-encrypted AES key as a base64-encoded string
        encrypted_key_base64 = base64.b64encode(encrypted_key).decode('utf-8')

        return {"encrypted_key": encrypted_key_base64}
    
    
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail="Key not found for the specified file")
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error encrypting the key: {str(e)}")




# # API to decrypt the encrypted AES key using the provided RSA private key
# @app.post("/decrypt-encrypted-key")
# async def decrypt_encrypted_key(encrypted_key: str, rsa_private_key: str):
#     try:
#         # Decode the base64-encoded encrypted AES key
#         encrypted_key_bytes = base64.b64decode(encrypted_key)
#         rsa_private_key_decoded = base64.b64decode(rsa_private_key).decode('utf-8')


#         # Convert the RSA private key string to a usable private key object
#         try:
#             private_key = serialization.load_pem_private_key(
#                 rsa_private_key.encode('utf-8'),
#                 password=None  # Ensure this is None unless your private key is encrypted
#             )
#         except ValueError as e:
#             print(f"Private key load failed: {str(e)}")  
#             raise HTTPException(status_code=400, detail="Invalid private key format or incorrect password")

#         # Decrypt the AES key using the RSA private key
#         decrypted_key = private_key.decrypt(
#             encrypted_key_bytes,
#             padding.OAEP(
#                 mgf=padding.MGF1(algorithm=hashes.SHA256()),
#                 algorithm=hashes.SHA256(),
#                 label=None
#             )
#         )

#         # Return the decrypted AES key as a hex-encoded string
#         return {"decrypted_key": decrypted_key.hex()}

#     except Exception as e:
#         raise HTTPException(status_code=500, detail=f"Decryption failed: {str(e)}")
















