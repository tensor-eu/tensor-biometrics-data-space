import seal
import hashlib
import numpy as np
import os
from seal import EncryptionParameters, SEALContext, KeyGenerator, Encryptor, Decryptor, Evaluator, CKKSEncoder

class DeepHashingSEAL:
    def __init__(self, poly_modulus_degree=8192, scale=2**40):
        # Initialize encryption parameters
        print("Initializing encryption parameters...")
        self.parms = EncryptionParameters(seal.scheme_type.ckks)
        self.parms.set_poly_modulus_degree(poly_modulus_degree)
        self.parms.set_coeff_modulus(seal.CoeffModulus.Create(poly_modulus_degree, [60, 40, 40, 60]))
        self.scale = scale

        # Create SEALContext
        print("Creating SEALContext...")
        self.context = SEALContext(self.parms)

        # Key Generation
        print("Generating public and private keys...")
        self.keygen = KeyGenerator(self.context)
        self.public_key = self.keygen.create_public_key()
        self.secret_key = self.keygen.secret_key()
        self.relin_keys = self.keygen.create_relin_keys()
        self.galois_keys = self.keygen.create_galois_keys()

        # Create Encryptor, Decryptor, Evaluator, and Encoder
        print("Creating Encryptor, Decryptor, Evaluator, and Encoder...")
        self.encryptor = Encryptor(self.context, self.public_key)
        self.decryptor = Decryptor(self.context, self.secret_key)
        self.evaluator = Evaluator(self.context)
        self.encoder = CKKSEncoder(self.context)

    def deep_hash(self, data):
        """Generates a deep hash from data (typically a string or bytes)."""
        if isinstance(data, str):
            data = data.encode('utf-8')
        return hashlib.sha256(data).hexdigest()

    def encrypt(self, hash_value):
        """Encrypts the hash value."""
        print(f"Encrypting hash value: {hash_value}")
        plain = self.encoder.encode([float(ord(c)) for c in hash_value], self.scale)
        encrypted = self.encryptor.encrypt(plain)
        print("Encryption successful.")
        return encrypted

    def decrypt(self, encrypted_hash):
        """Decrypts the encrypted hash value (string)."""
        print("Decrypting encrypted hash...")
        decrypted_plain = self.decryptor.decrypt(encrypted_hash)
        decrypted_vector = self.encoder.decode(decrypted_plain)
        decrypted_str = ''.join(chr(int(np.round(val))) for val in decrypted_vector)
        print(f"Decryption successful, decrypted value: {decrypted_str}")
        return decrypted_str

    def decrypt_numeric(self, encrypted_value):
        """Decrypts the encrypted numeric value."""
        print("Decrypting numeric value...")
        decrypted_plain = self.decryptor.decrypt(encrypted_value)
        decrypted_vector = self.encoder.decode(decrypted_plain)
        result = decrypted_vector[0]
        print(f"Decrypted numeric result: {result}")
        return result

    def sum_encrypted_vector(self, encrypted_vector):
        """Sums the values of an encrypted vector using rotations and additions."""
        print("Summing encrypted vector...")
        slot_count = self.encoder.slot_count()
        temp_encrypted = encrypted_vector

        for i in range(int(np.log2(slot_count))):
            rotated = self.evaluator.rotate_vector(temp_encrypted, 2**i, self.galois_keys)
            self.evaluator.add_inplace(temp_encrypted, rotated)

        print("Summation of encrypted vector completed.")
        return temp_encrypted

    def compare_homomorphically(self, encrypted_plain1, encrypted_plain2, data1, data2):
        """Compares two encrypted plaintexts homomorphically and returns a similarity score."""
        print("Starting homomorphic comparison of two encrypted plaintexts...")

        # Step 1: Homomorphic subtraction to get differences
        diff_encrypted = self.evaluator.sub(encrypted_plain1, encrypted_plain2)

        # Step 2: Square the difference to make sure non-negative values (simulates absolute value)
        diff_squared = self.evaluator.square(diff_encrypted)
        self.evaluator.relinearize_inplace(diff_squared, self.relin_keys)

        # Step 3: Sum the squared differences to get mismatch count (ASCII difference count)
        sum_of_mismatches = self.sum_encrypted_vector(diff_squared)

        # Decrypt the total mismatch count (ASCII differences)
        decrypted_mismatch_count = self.decrypt_numeric(sum_of_mismatches)

        # Calculate total ASCII values for the original plaintexts
        total_ascii_plain1 = sum(ord(c) for c in data1)
        total_ascii_plain2 = sum(ord(c) for c in data2)

        # Use the average of the total ASCII values as the denominator for normalization
        total_ascii_value = (total_ascii_plain1 + total_ascii_plain2) / 2

        # Calculate similarity score based on mismatch count and total ASCII value
        mismatch_count = max(float(decrypted_mismatch_count), 0)

        # Adjust the similarity score to be based on the total ASCII value, not just the length
        similarity_score = max(0, ((total_ascii_value - mismatch_count) / total_ascii_value) * 100)
        similarity_score = min(similarity_score, 100)

        print(f"Homomorphic comparison completed. Similarity score: {similarity_score:.2f}%")
        return similarity_score







    def save_encrypted_data(self, encrypted_data, file_path):
        """Save encrypted data to a file."""
        print(f"Saving encrypted data to {file_path}...")
        with open(file_path, 'wb') as f:
            f.write(encrypted_data.to_bytes())
        print(f"Encrypted data saved to {file_path}.")

    def save_keys(self, public_key_path, secret_key_path):
        """Save public and secret keys to files."""
        try:
            # Save the public key
            self.public_key.save(public_key_path)
            print(f"Public key saved to {public_key_path}")
        except Exception as e:
            print(f"Failed to save public key: {e}")

        try:
            # Save the secret key
            self.secret_key.save(secret_key_path)
            print(f"Secret key saved to {secret_key_path}")
        except Exception as e:
            print(f"Failed to save secret key: {e}")


# File handling and processing
def read_data_from_file(file_path):
    """Read data from a file."""
    print(f"Reading data from {file_path}...")
    with open(file_path, 'r') as f:
        data = f.read()
    print(f"Data read successfully from {file_path}.")
    return data


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


if __name__ == "__main__":
    # Initialize the SEAL Deep Hashing
    seal_deep_hash = DeepHashingSEAL()

    # File paths
    raw_data_folder = "./raw_data"
    encrypted_data_folder = "./encrypted_data"
    key_folder = "./keys"

    os.makedirs(encrypted_data_folder, exist_ok=True)
    os.makedirs(key_folder, exist_ok=True)

    # Read data1 and data2 from files
    data1 = read_data_from_file(os.path.join(raw_data_folder, "data1.txt"))
    data2 = read_data_from_file(os.path.join(raw_data_folder, "data2.txt"))

    # # Hash and Encrypt
    # print("Hashing and encrypting data1 and data2...")
    # hash1 = seal_deep_hash.deep_hash(data1)
    # hash2 = seal_deep_hash.deep_hash(data2)

    encrypted_hash1 = seal_deep_hash.encrypt(data1)
    encrypted_hash2 = seal_deep_hash.encrypt(data2)

    # Save encrypted hashes to files
    save_encrypted_data_to_file(encrypted_hash1, os.path.join(encrypted_data_folder, "encrypted_data1.dat"))
    save_encrypted_data_to_file(encrypted_hash2, os.path.join(encrypted_data_folder, "encrypted_data2.dat"))

    # Homomorphic comparison of the encrypted hashes
    print("Performing homomorphic comparison of encrypted hashes...")
    similarity_homomorphic = seal_deep_hash.compare_homomorphically(encrypted_hash1, encrypted_hash2, data1, data2)

    # Save the comparison result (encrypted) to a file
    save_encrypted_data_to_file(similarity_homomorphic, os.path.join(encrypted_data_folder, "comparison_result.dat"))

    # Save keys
    print("Saving keys...")
    seal_deep_hash.save_keys(os.path.join(key_folder, "public_key.key"), os.path.join(key_folder, "secret_key.key"))

    print(f"Homomorphic similarity score between data1 and data2: {similarity_homomorphic:.2f}%")
