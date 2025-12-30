# Project Name

**TENSOR**: Reliable biomeTric tEchNologies to asSist Police authorities in cOmbating terrorism and oRganized crime

## Description

This project is part of the research and development supported by the **EU Horizon Europe** program. It aims to implement the first Biometrics Data Space ecosystem for sovereign and trustworthy cross-border data exchange. The Biometrics Data Space creates a secure ecosystem built on Data Spaces technology, designed to facilitate the safe sharing and exchange of biometric data in cross-border scenarios where international cooperation among foreign Law Enforcement Agencies (LEAs) and forensic agencies from different
countries is required. The proposed framework incorporates Privacy Enhancing Technologies (PETs), such as homomorphic encryption, to mitigate data protection issues. Additionally, large-scale data indexing is used to quickly and efficiently compare facial images of suspects with those stored remotely, ensuring no personal information is revealed during the search and matching process.  Furthermore, the system includes automated access rights control through Blockchain Smart Contracts, integrated within the Biometrics Data Space framework where data owners (e.g., any national forensic agency that maintains a list of suspects identified by their biometric data) define the data usage rules for the biometric data that is to be exchanged.

The work has received funding from the EU Horizon Europe research and innovation programme through the [**TENSOR** project](https://cordis.europa.eu/project/id/101073920) under Grant Agreement (GA) 101073920.

## Components

- Case Management System (Provided as open source code)
- Data Sharing Platform (Provided as a dockerized image)
- Data Space Connector (Provided as open source code)
- Fuzzy Extractor & AES-256 Encryption (Provided as open source code)
- Homomorphic Encryption (Provided as open source code)
- Large-Scale Data Indexer (Provided as open source code)

## Installation

```bash
# Clone the repository
git clone https://github.com/tensor-eu/tensor-biometrics-data-space.git

# Navigate into the project directory
cd tensor-biometrics-data-space

# Create a common docker network
docker network create tensor-biometrics-dataspace-server

# Update the necessary information in env files & deploy each component separately using docker compose up -d
cd CaseManagementSystem/
docker compose up -d
cd DataSharingPlatform/
docker compose up -d
cd DataSpaceConnector/
docker compose up -d
cd FuzzyExtractor/
docker compose up -d
cd HomomorphicEncryption/
docker compose up -d
cd LargeScaleIndexer/
docker compose up -d

```
