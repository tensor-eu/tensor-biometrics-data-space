import os
import binascii
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend
from fuzzy_extractor import FuzzyExtractor
from PIL import Image
import io

# Adjust the key length to fit AES requirements
def adjust_key_length(key_bytes, desired_length=32):
    if len(key_bytes) < desired_length:
        padding = bytes(desired_length - len(key_bytes))
        return key_bytes + padding
    elif len(key_bytes) > desired_length:
        return key_bytes[:desired_length]
    else:
        return key_bytes

# AES encryption function
def aes_encrypt(plaintext, key):
    iv = os.urandom(16)
    backend = default_backend()
    cipher = Cipher(algorithms.AES(key), modes.CFB(iv), backend=backend)
    encryptor = cipher.encryptor()
    ciphertext = encryptor.update(plaintext) + encryptor.finalize()
    return ciphertext, iv

# AES decryption function
def aes_decrypt(ciphertext, key, iv):
    backend = default_backend()
    cipher = Cipher(algorithms.AES(key), modes.CFB(iv), backend=backend)
    decryptor = cipher.decryptor()
    plaintext = decryptor.update(ciphertext) + decryptor.finalize()
    return plaintext

# Load image as bytes
def load_image_as_bytes(image_path):
    with Image.open(image_path) as img:
        img_byte_arr = io.BytesIO()
        img.save(img_byte_arr, format=img.format)
        img_bytes = img_byte_arr.getvalue()
    return img_bytes

# Save image from bytes
def save_image_from_bytes(image_bytes, output_path):
    img = Image.open(io.BytesIO(image_bytes))
    img.save(output_path)

def save_key_to_file(key, filename):
    if not os.path.exists('keys'):
        os.makedirs('keys')
    key_hex = binascii.hexlify(key).decode('utf-8')
    with open(filename, 'w') as key_file:
        key_file.write(key_hex)
    print(f"Key saved to {filename}")

# Load file as bytes
def load_file_as_bytes(file_path):
    with open(file_path, 'rb') as f:
        return f.read()

# Save file from bytes
def save_file_from_bytes(file_bytes, output_path):
    with open(output_path, 'wb') as f:
        f.write(file_bytes)

# Encryption process
def encryption_process(mode, enroll_image, file_to_encrypt):
    print("Extracting features from the enrollment image...")
    
    if mode == 'face':
        from face_bio_utils import extract_face_features, generate_bio_descriptor, visualize_landmarks
        features_1 = extract_face_features(enroll_image)
        if features_1 is None:
            raise ValueError("Failed to extract face features.")
        print(f"Extracted face features: {features_1}")
        bio_descriptor_1 = generate_bio_descriptor(features_1)
        if bio_descriptor_1 is None:
            raise ValueError("Failed to generate bio descriptor from face features.")
        print(f"Generated face bio descriptor: {bio_descriptor_1}")
        visualize_landmarks(enroll_image, features_1, './visualization/landmarks_1.png')

    elif mode == 'fingerprint':
        from finger_bio_utils import preprocess_fingerprint, extract_minutiae_points, generate_bio_descriptor, visualize_minutiae
        skeleton_image_1 = preprocess_fingerprint(enroll_image)
        if skeleton_image_1 is None:
            raise ValueError("Failed to preprocess fingerprint image.")
        minutiae_points_1 = extract_minutiae_points(skeleton_image_1)
        if minutiae_points_1 is None:
            raise ValueError("Failed to extract minutiae points.")
        print(f"Extracted fingerprint minutiae points: {minutiae_points_1}")
        bio_descriptor_1 = generate_bio_descriptor(minutiae_points_1)
        if bio_descriptor_1 is None:
            raise ValueError("Failed to generate bio descriptor from minutiae points.")
        print(f"Generated fingerprint bio descriptor: {bio_descriptor_1}")
        visualize_minutiae(skeleton_image_1, minutiae_points_1, './visualization/minutiae_1.png')

    # Proceed with encryption if bio_descriptor_1 is valid
    bio_descriptor_bytes_1 = bio_descriptor_1.tobytes()
    extractor = FuzzyExtractor(255, 3)
    key, helper_data = extractor.generate(bio_descriptor_bytes_1)

    adjusted_key = adjust_key_length(key, 32)
    save_key_to_file(adjusted_key, 'keys/aes_key.key')

    # Load and encrypt the file (now supports ZIP files)
    file_bytes = load_file_as_bytes(file_to_encrypt)
    ciphertext, iv = aes_encrypt(file_bytes, adjusted_key)
    print(f"File encrypted. IV: {iv.hex()}")
    return ciphertext, iv, helper_data, adjusted_key


# Decryption process
def decryption_process(mode, verify_image, iv, ciphertext, helper_data, adjusted_key):
    print("Extracting features from the verification image...")
    if mode == 'face':
        from face_bio_utils import extract_face_features, generate_bio_descriptor
        features_2 = extract_face_features(verify_image)
        bio_descriptor_2 = generate_bio_descriptor(features_2)
    elif mode == 'fingerprint':
        from finger_bio_utils import preprocess_fingerprint, extract_minutiae_points, generate_bio_descriptor
        skeleton_image_2 = preprocess_fingerprint(verify_image)
        minutiae_points_2 = extract_minutiae_points(skeleton_image_2)
        bio_descriptor_2 = generate_bio_descriptor(minutiae_points_2)

    if bio_descriptor_2 is None:
        raise ValueError("Failed to generate biometric descriptor for the verification image.")

    bio_descriptor_bytes_2 = bio_descriptor_2.tobytes()

    extractor = FuzzyExtractor(255, 3)  # Recreate the extractor
    recovered_key = extractor.reproduce(bio_descriptor_bytes_2, helper_data)
    if recovered_key is None:
        raise ValueError("Key recovery failed due to too much noise.")

    adjusted_recovered_key = adjust_key_length(recovered_key, 32)

    if adjusted_key != adjusted_recovered_key:
        raise ValueError("Keys do not match! Decryption may fail.")

    decrypted_file_bytes = aes_decrypt(ciphertext, adjusted_recovered_key, iv)
    
    # Get file extension from verify_image path for naming the output file
    file_extension = os.path.splitext(verify_image)[1].lower()
    
    # Create decrypted directory if it doesn't exist
    if not os.path.exists('./decrypted'):
        os.makedirs('./decrypted')
        
    decrypted_file_path = f'./decrypted/decrypted_file{file_extension}'
    save_file_from_bytes(decrypted_file_bytes, decrypted_file_path)
    print(f"Decrypted file saved successfully at {decrypted_file_path}")
    return decrypted_file_path
