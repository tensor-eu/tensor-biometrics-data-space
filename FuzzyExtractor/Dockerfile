# Use the official Python 3.10 slim image
FROM python:3.10-slim

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    cmake \
    wget \
    bzip2 \
    libgl1 \
    && rm -rf /var/lib/apt/lists/*

# Install Miniconda
RUN wget --quiet https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O /tmp/miniconda.sh && \
    bash /tmp/miniconda.sh -b -p /opt/conda && \
    rm /tmp/miniconda.sh

# Add conda to PATH
ENV PATH="/opt/conda/bin:${PATH}"

# Accept Anaconda TOS for main and r channels
RUN conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/main && \
    conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/r

# Install mamba in base environment for faster installs
RUN conda install -n base -c conda-forge mamba -y

# Create Conda environment and install dependencies using mamba
RUN mamba create -y -n sage-env -c conda-forge python=3.10 \
    sage dlib numpy scipy matplotlib Pillow cryptography opencv scikit-image pytest

# Activate the environment for subsequent commands
SHELL ["conda", "run", "-n", "sage-env", "/bin/bash", "-c"]

# Set up working directory
WORKDIR /app

# Copy project files
COPY . /app

# Set permissions
RUN chmod -R 777 /app

# Install FastAPI and Uvicorn
RUN pip install fastapi uvicorn python-multipart

# Expose FastAPI port
EXPOSE 8000

# Run FastAPI app within Conda environment
CMD ["conda", "run", "--no-capture-output", "-n", "sage-env", "uvicorn", "api:app", "--host", "0.0.0.0", "--port", "8000"]
