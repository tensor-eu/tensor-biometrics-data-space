import os
import warnings
import numpy as np
import dhn as model
import numpy as np
import tensorflow as tf
import requests
import librosa
from PIL import Image  
from io import BytesIO

from dotenv import load_dotenv
import os
import json
import base64

load_dotenv()

np.random.seed(1)
tf.random.set_seed(1)
tf.config.experimental.enable_op_determinism()
os.environ['TF_DETERMINISTIC_OPS'] = '1'

current_dir = os.path.dirname(__file__)

print("Voice GPUs Available: ", len(tf.config.list_physical_devices('GPU')))

# Suppress warnings
warnings.filterwarnings("ignore", category=DeprecationWarning)
warnings.filterwarnings("ignore", category=FutureWarning)

# Configuration parameters
config = {
    'device': '/gpu:0',
    'max_iter': 2000,
    'batch_size': 1,
    'val_batch_size': 1, 
    'decay_step': 5000,  
    'learning_rate_decay_factor': 0.5, 
    'learning_rate': 0.00005,  
    'output_dim': 64,
    'alpha': 10.0,
    'R': 54000,
    # 'model_weights': os.path.join(current_dir, 'lr_5e-05_cqlambda_0.0_alpha_10.0_dataset_voice.npy'),
    'model_weights': "https://test.tensor-horizon.eu/api/resources/forth-pod%2Flr_5e-05_cqlambda_0.0_alpha_10.0_dataset_voice.npy",
    'weights_file': None,
    'img_model': 'alexnet',
    'loss_type': 'normed_cross_entropy',
    'finetune_all': True,
    'cq_lambda': 0.0,
    'label_dim': 110,
    'dataset': "voice"
}
    


def calculateHashForSearchingVoice_base64(b64_string, weights, api_voice_finger):

    audio_bytes = base64.b64decode(b64_string)
    flac_file = BytesIO(audio_bytes)
    y, sr = librosa.load(flac_file)  

    y_norm = librosa.util.normalize(y)
    image = np.ones((224, 224), dtype=np.float32)
    samples_per_column = max(1, len(y_norm) // 224)

    for i in range(224):
        start = i * samples_per_column
        end = (i + 1) * samples_per_column
        column_data = y_norm[start:end]
        max_rows = min(224, len(column_data))
        if max_rows > 0:
            image[:max_rows, i] = column_data[:max_rows]

    image_scaled = (image * 255).astype(np.uint8)
    img = Image.fromarray(image_scaled).convert("RGB")
    img = np.array(img)
    img = img[:, :, ::-1]  # BGR
    img = np.array(Image.fromarray(img).resize((256, 256), resample=Image.Resampling.BILINEAR))
    img = np.expand_dims(img, axis=0)


    config['weights_file'] = weights
    query_output = model.pred_solo(img, config, model=0)
    query_output = np.sign(query_output[0]).astype(np.int8).reshape(1, 64)


    # file_path = "temp1.txt"
    # with open(file_path, "w") as f:
    #     f.write(str(query_output.tolist()))


    resp = requests.post(
        api_voice_finger + "encrypt_finger_or_voice_hash_representations",
        json={"finger_or_voice_hash": query_output[0].tolist()}
    ).json()

    return resp['encrypted_data']



def indexVoice(image_url, model_manager, weights, api_voice_finger, suspect_id, owner, sensitivity):


    encrypted_codes = calculateHashForSearchingVoice_base64(image_url, weights, api_voice_finger)

    # Append new entry
    new_line = f"{suspect_id} {encrypted_codes} {sensitivity}"

    try:
        existing_file = model_manager.get_catalogue("voice_real")
        try:
            json_obj = json.loads(existing_file)
            if isinstance(json_obj, dict) and "error" in json_obj:
                print("[INFO] File not found on server. Creating new one.")
                existing_file = ""
        except json.JSONDecodeError:
            pass
    except Exception as e:
        print(f"[WARN] Could not fetch index file: {e}")
        existing_file = ""

    updated_file = existing_file.strip() + "\n" + new_line

    file_path = "temp.txt"
    with open(file_path, "w") as f:
        f.write(updated_file)

    url = os.getenv("VOICE_URL")

    last_split_index = url.rfind('%2F')
    post_url = url[:last_split_index + 3] 
    filename = url[last_split_index + 3:]   

    files = {
        "file": (filename, open(file_path, "rb"), "text/plain"),
    }
    data = {
        "fileName": filename,
        "contentType": "text/plain",
    }

    response = model_manager.session_post(post_url, files=files, data=data)

    return encrypted_codes




def calculateHashForSearchingVoice(url, model_manager, weights, api_voice_finger):
 
    # Request voice
    cms_headers = {"Authorization": os.getenv("CMS_AUTH_HEADER")}
    response = model_manager.session_get(url, headers=cms_headers)
    if response.status_code == 200:

        flac_file = BytesIO(response.content)
        y, sr = librosa.load(flac_file)

        y_norm = librosa.util.normalize(y)
        image = np.ones((224, 224), dtype=np.float32)
        samples_per_column = len(y_norm) // 224

        for i in range(224):
            column_data = y_norm[i * samples_per_column: (i + 1) * samples_per_column]
            max_rows = min(224, len(column_data))
            image[:max_rows, i] = column_data[:max_rows] 

        image_scaled = (image * 255).astype(np.uint8)
        img = Image.fromarray(image_scaled)
        img = img.convert("RGB")
        img = np.array(img)
        img = img[:, :, ::-1]
        img = np.array(Image.fromarray(img).resize((256, 256), resample=Image.Resampling.BILINEAR))
        img = np.expand_dims(img, axis=0)

    else:
        raise Exception(f"Failed to fetch voice sample from {url}. HTTP status code: {response.status_code}")

    config['weights_file'] = weights

    query_output = model.pred_solo(img, config, model=0)

    query_output = np.sign(query_output[0]).astype(np.int8).reshape(1,64)

    resp = requests.post(api_voice_finger+"encrypt_finger_or_voice_hash_representations", json={"finger_or_voice_hash": query_output[0].tolist()}).json()

    return resp['encrypted_data']
    


def searchForMatchesVoice(hashcode,  originator, lea, model_manager, api_voice_finger, top_n=10):

    file_content = model_manager.get_catalogue("voice_real") + "\n" + model_manager.get_catalogue("voice_synthetic")


    filenames = []
    distances = []

    for line in file_content.splitlines():

        line = line.strip()
        if not line:
            continue

        parts = line.split(' ')
        if len(parts) < 3:
            continue

        filename, encrypted_code, sensitivity_flag = parts
        is_sensitive = sensitivity_flag.strip().lower() == "true"

        if is_sensitive and originator != lea:
            continue

        response = requests.post(api_voice_finger+"calc_hamming_distance", json={"encrypted_values": [hashcode, parts[1]]}).json()

        distance = response['hamming_distance'] / 2
        similarity = round(((64 - distance) / 64.0) * 100, 2)

        # Keep only the highest similarity per filename
        if filename not in filenames:
            filenames.append(filename)
            distances.append(similarity)
        else:
            idx = filenames.index(filename)
            if similarity > distances[idx]:
                distances[idx] = similarity


    distances = np.array(distances)
    filenames = np.array(filenames)

    s_idx = np.argsort(distances)[::-1]

    # Retrieve filenames and similarities for top N matches
    top_filenames = filenames[s_idx][:top_n]
    top_similarity = distances[s_idx][:top_n]

    return top_filenames.tolist(), top_similarity.tolist()

