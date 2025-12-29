# TensorFlow base image
FROM tensorflow/tensorflow:2.11.0-gpu

# Or, alternatively, PyTorch image
# FROM pytorch/pytorch:1.12.1-cuda11.3-cudnn8-runtime

ENV INSTALL_PATH /app
RUN mkdir -p ${INSTALL_PATH}
WORKDIR ${INSTALL_PATH}

# install libsndfile
RUN apt-get update && apt-get install -y libsndfile1

COPY requirements.txt .
RUN pip install --no-cache-dir --timeout=300 -r requirements.txt

COPY . .

EXPOSE 8000 8001

ENV FLASK_APP=app/flask_main.py

CMD ["flask", "run", "--host=0.0.0.0", "--port=8000"]
