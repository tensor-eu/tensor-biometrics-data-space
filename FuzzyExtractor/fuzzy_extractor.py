# fuzzy_extractor_sage.py

import os
import logging
from random import randint
from sage.all import *
from sage.coding.bch_code import BCHCode

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(message)s')

def bits_to_bytes(bits):
    """
    Convert a list of bits (0 or 1) to bytes.
    """
    b = bytearray()
    for i in range(0, len(bits), 8):
        byte = 0
        for j in range(8):
            byte <<= 1
            if i + j < len(bits):
                byte |= bits[i + j]
            else:
                byte |= 0  # Pad with zeros if bits length not multiple of 8
        b.append(byte)
    return bytes(b)

def bytes_to_bits(b):
    """
    Convert bytes to a list of bits (0 or 1).
    """
    bits = []
    for byte in b:
        for i in range(8):
            bits.append((byte >> (7 - i)) & 1)
    return bits

class FuzzyExtractor:
    """
    Fuzzy Extractor using BCH codes for error correction.
    """

    def __init__(self, n, d):
        """
        Initialize the Fuzzy Extractor with BCH code parameters.
        """
        self.n = n
        self.d = d
        self.kod = BCHCode(GF(2), n, d)
        self.k = self.kod.dimension()
        # logging.info(f"Initialized FuzzyExtractor with n={n}, d={d}, k={self.k}")

    def generate(self, w_bytes):
        """
        Enrollment function to generate key and helper data.
        """
        # logging.info("Starting enrollment phase (generate)")
        # Convert w_bytes to bits
        w_bits = bytes_to_bits(w_bytes)
        # Pad or truncate w_bits to length n
        if len(w_bits) < self.n:
            w_bits.extend([0] * (self.n - len(w_bits)))
        elif len(w_bits) > self.n:
            w_bits = w_bits[:self.n]
        # Generate random key bits of length k
        K_bits = [randint(0, 1) for _ in range(self.k)]
        # logging.info(f"Generated random key bits: {K_bits}")

        # Convert key bits to bytes
        K_bytes = bits_to_bytes(K_bits)
        # logging.info(f"Converted key bits to bytes: {K_bytes.hex()}")

        # Encode the key using BCH code to get codeword
        K_vector = vector(GF(2), K_bits)
        codeword = self.kod.encode(K_vector)
        # logging.info(f"Encoded key to codeword: {codeword}")

        # Compute helper data P = w + codeword (over GF(2))
        w_vector = vector(GF(2), w_bits)
        helper_data = w_vector + codeword
        P_bits = [int(bit) for bit in helper_data]
        # logging.info(f"Computed helper data P: {P_bits}")

        # Convert P_bits to bytes
        P_bytes = bits_to_bytes(P_bits)
        # logging.info(f"Helper data P as bytes: {P_bytes.hex()}")

        return K_bytes, P_bytes

    def reproduce(self, w_prime_bytes, P_bytes):
        """
        Reconstruction function to recover the key.
        """
        # logging.info("Starting verification phase (reproduce)")
        # Convert w_prime_bytes and P_bytes to bits
        w_prime_bits = bytes_to_bits(w_prime_bytes)
        P_bits = bytes_to_bits(P_bytes)
        # Pad or truncate w_prime_bits and P_bits to length n
        if len(w_prime_bits) < self.n:
            w_prime_bits.extend([0] * (self.n - len(w_prime_bits)))
        elif len(w_prime_bits) > self.n:
            w_prime_bits = w_prime_bits[:self.n]
        if len(P_bits) < self.n:
            P_bits.extend([0] * (self.n - len(P_bits)))
        elif len(P_bits) > self.n:
            P_bits = P_bits[:self.n]
        # Compute codeword: codeword = w' + P (over GF(2))
        w_prime_vector = vector(GF(2), w_prime_bits)
        P_vector = vector(GF(2), P_bits)
        codeword = w_prime_vector + P_vector
        # logging.info(f"Computed codeword from w' and P: {codeword}")

        # Decode the codeword to recover the key bits
        try:
            K_vector = self.kod.decode_to_message(codeword)
            K_bits = [int(bit) for bit in K_vector]
            # logging.info(f"Decoded codeword to key bits: {K_bits}")

            # Convert key bits to bytes
            K_bytes = bits_to_bytes(K_bits)
            # logging.info(f"Converted key bits to bytes: {K_bytes.hex()}")

            return K_bytes
        except Exception as e:
            # Decoding failed
            logging.error(f"Decoding failed: {e}")
            return None
