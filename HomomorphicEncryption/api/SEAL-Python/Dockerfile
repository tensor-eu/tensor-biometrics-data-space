FROM ubuntu:22.04

# define the folder where our src should exist/ be deposited
ARG SRC=/python-seal

# prevents update and install asking for tz
ENV DEBIAN_FRONTEND=noninteractive

ENV PYTHONPATH=$PYTHONPATH:/python-seal

# install dependencies
RUN apt update && \
    apt install -y git build-essential cmake python3 python3-dev python3-pip && \
    mkdir -p ${SRC}

# install FastAPI, Uvicorn, and other Python dependencies
RUN pip3 install numpy pybind11 fastapi uvicorn python-multipart

# copy everything into container now that requirements stage is complete
COPY . ${SRC}

# setting our default directory to the one specified above
WORKDIR ${SRC}

# # update submodules
# RUN cd ${SRC} && \
#     git submodule update --init --recursive
# # git submodule update --remote

# build and install seal + bindings
RUN cd ${SRC}/SEAL && \
    cmake -S . -B build -DSEAL_USE_MSGSL=OFF -DSEAL_USE_ZLIB=OFF -DSEAL_USE_ZSTD=OFF && \
    cmake --build build && \
    cd ${SRC} && \
    python3 setup.py build_ext -i

# expose port for the FastAPI application
EXPOSE 8000

# command to run the FastAPI server
CMD ["uvicorn", "api:app", "--host", "0.0.0.0", "--port", "8000"]