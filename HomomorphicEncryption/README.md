## Component Homomorphic Encryption

## Repository Structure

- **`HomomorphicEncryption/`**  
  This component integrates **Deep Hashing (DH)** and **Homomorphic Encryption (HE)** for privacy-preserving biometric comparisons. It can be directly deployed using Docker to set up and run the required environment without additional configuration.

## Deep Hashing with Homomorphic Encryption

This demo processes biometric descriptors using deep hashing and compares them using homomorphic encryption for secure comparisons. The Docker environment handles all configurations, including setting up the SEAL library.

### Directory Descriptions

- **`encrypted_data/`**:  
  This folder contains encrypted biometric data generated during the execution of the component. Each file here represents homomorphically encrypted hashes used in the comparison process.

- **`keys/`**:  
  Stores any keys generated during the homomorphic encryption process, including public and private keys necessary for encryption and decryption operations.

- **`raw_data/`**:  
  Contains raw biometric input data, such as images or descriptors, that are processed by the deep hashing mechanism.

---
