## Component

Fuzzy Extractor and AES Encryption

## Repository Structure

- **`FuzzyExtractor/`**  
  This component integrates **Fuzzy Extractors (FE)** with **AES encryption**. It supports two types of biometric data sources: **fingerprint** and **face**. The FE generates cryptographic keys based on biometric inputs, which are then used for AES encryption and decryption.

## Fuzzy Extractor with AES

This component uses Fuzzy Extractors (FE) to generate cryptographic keys from biometric data and then employs AES for encryption and decryption. It supports two biometric input types: **fingerprint** and **face**.

### Setup

To run this component, you need to configure a Sage environment. Follow these steps to set up Sage and run the component:

1. **Install Sage**:
   Follow the instructions to install [SageMath](https://www.sagemath.org/download.html) if you don’t already have it installed.

2. **Run the component**:
   The component can process either fingerprint or face data. Below are example commands for both:

   **Fingerprint example**:

   ```bash
   sage -python run_v3.py --mode fingerprint --enroll_image finger_image_1.tif --verify_image finger_image_1.1.tif
   ```

   **Face example**:

   ```bash
   sage -python run_v3.py --mode face --enroll_image biometric_FE/face_image_1.jpg --verify_image biometric_FE/face_image_1.1.jpg --image_to_encrypt raw_data_AES/sample.png
   ```

   The program will generate the cryptographic keys based on the biometric data and perform the necessary encryption or decryption operations using AES.

```

```
