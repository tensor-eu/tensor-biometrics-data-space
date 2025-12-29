from flask import Flask, request, jsonify
from comparator import calculateHashForSearchingFace, searchForMatchesFace, indexFace
from LargeScaleIndexer.SourceCode.app.voice_comparator import calculateHashForSearchingVoice, searchForMatchesVoice, indexVoice
from finger_comparator import calculateHashForSearchingFinger, searchForMatchesFinger, indexFinger
from flask_cors import CORS, cross_origin
import torch
import requests
from io import BytesIO
import numpy as np
import gc
from dotenv import load_dotenv
import os
from requests.adapters import HTTPAdapter, Retry
import threading
import time

load_dotenv()


app = Flask(__name__)
CORS(app, support_credentials=True) 

class ModelManager:
    def __init__(self):

        #  login before requesting models      
        self.base_url = os.getenv("BASE_URL")
        self.oidc_url = os.getenv("OIDC_URL")
        self.username = os.getenv("USERNAME")
        self.password = os.getenv("PASSWORD")

        
        self.session = requests.Session()
        retries = Retry(
            total=5,
            backoff_factor=2,
            status_forcelist=[500, 502, 503, 504],
            allowed_methods=frozenset(["GET", "OPTIONS", "POST"]),
            raise_on_status=False,
            respect_retry_after_header=True,
        )


        adapter = HTTPAdapter(max_retries=retries)
        self.session.mount("https://", adapter)
        self.session.mount("http://", adapter)

        # Try to avoid multiple logins at the same time
        self._login_lock = threading.Lock()
        self._last_login_time = 0
        self._login_expiry_seconds = 180
        

        # Encryptor URLs from environment        
        self.api_encrypt_decrypt = os.getenv("API_ENCRYPT_DECRYPT")
        self.api_voice_finger = os.getenv("API_VOICE_FINGER")
        self.api_face = os.getenv("API_FACE")

        """Initialize and load all model weights once at startup."""
        self.models = {}
        self.load_models()


        """Initialise and load catalogues once at startup."""
        self.catalogues = {}
        self.load_catalogues()

    def login(self):
        
        print("Login Called")

        with self._login_lock:

            now = time.time()
            if now - self._last_login_time < self._login_expiry_seconds:
                print("Recent login detected, skipping re-login.")
                return

            print("Performing login...")
            try:
                resp = self.session.post(
                    f"{self.base_url}/login",
                    params={"oidc_url": self.oidc_url},
                    json={"username": self.username, "password": self.password},
                )


                print(f"Login response status: {resp.status_code}")
                print(f"Login response text: {resp.text}")

                resp.raise_for_status()

                if "Logged in" in resp.text:
                    print("Session login succeeded.")
                    self._last_login_time = now
                else:
                    print("Unexpected login response.")
            except Exception as e:
                print(f"Login failed: {e}")

    
    
    def _needs_reauth(self, r): 
        return r is not None and r.status_code in (401,403)

    def session_get(self, url, headers=None, **kw):
        kw.setdefault("timeout", 60)
        r = self.session.get(url, headers=headers, **kw)
        if self._needs_reauth(r):
            self.login(); r = self.session.get(url, **kw)
        return r

    def session_post(self, url, headers=None, **kw):
        kw.setdefault("timeout", 60)
        r = self.session.post(url, headers=headers, **kw)
        if self._needs_reauth(r):
            self.login(); r = self.session.post(url, **kw)
        return r


    def load_models(self):
        """Preload all model weights at startup."""

        print("Logging in...")
        self.login()

        print("Loading models...")

        try:
            # Load Face Model
            print("Loading Face Model...")
            encrypted_model_weights = self.session_get(os.getenv("FACE_MODEL_URL"))
            encrypted_model_weights.raise_for_status()

            data = {"encrypted_model_weights": encrypted_model_weights.text}
            encrypted_codes = requests.post(
                self.api_encrypt_decrypt+"decrypt_model_weights", json=data).json()
            self.models["face"] = torch.tensor(encrypted_codes["model_weights"])
            print("Face Model Loaded")

            # Load Finger Model
            print("Loading Finger Model...")
            response = self.session_get(os.getenv("FINGER_MODEL_URL"))
            response.raise_for_status()

            self.models["finger"] = np.load(BytesIO(response.content), allow_pickle=True, encoding='bytes').item()
            print("Finger Model Loaded")

            # Load Voice Model
            print("Loading Voice Model...")
            response = self.session_get(os.getenv("VOICE_MODEL_URL"))
            response.raise_for_status()
            self.models["voice"] = np.load(BytesIO(response.content), allow_pickle=True, encoding='bytes').item()
            print("Voice Model Loaded")

        except Exception as e:
            print(f"Error loading models: {e}")


    def load_catalogues(self, reload_real_only=False):
        """Load catalogues into memory. Synthetic only loaded once."""
        try:
            print("Loading catalogues...")

            # Face
            if not reload_real_only:
                print("Loading Face synthetic catalogue...")
                synthetic = self.session_get(os.getenv("FACE_SYNTHETIC_URL"))
                self.catalogues["face_synthetic"] = synthetic.text if synthetic.status_code == 200 else ""
            print("Loading Face real catalogue...")
            real = self.session_get(os.getenv("FACE_URL"))
            self.catalogues["face_real"] = real.text if real.status_code == 200 else ""

            # Finger
            if not reload_real_only:
                print("Loading Finger synthetic catalogue...")
                synthetic = self.session_get(os.getenv("FINGER_SYNTHETIC_URL"))
                self.catalogues["finger_synthetic"] = synthetic.text if synthetic.status_code == 200 else ""
            print("Loading Finger real catalogue...")
            real = self.session_get(os.getenv("FINGER_URL"))
            self.catalogues["finger_real"] = real.text if real.status_code == 200 else ""

            # Voice
            if not reload_real_only:
                print("Loading Voice synthetic catalogue...")
                synthetic = self.session_get(os.getenv("VOICE_SYNTHETIC_URL"))
                self.catalogues["voice_synthetic"] = synthetic.text if synthetic.status_code == 200 else ""
            print("Loading Voice real catalogue...")
            real = self.session_get(os.getenv("VOICE_URL"))
            self.catalogues["voice_real"] = real.text if real.status_code == 200 else ""

        except Exception as e:
            print(f"Error loading catalogues: {e}")

    def get_model(self, model_name):
        return self.models.get(model_name, None)

    def get_catalogue(self, catalogue_name):
        return self.catalogues.get(catalogue_name, None)


    # def update_cookie(self, new_cookie):
    #     self.headers['Cookie'] = new_cookie

# Uncomment the following line to initialize ModelManager at startup
model_manager = ModelManager()

@app.route('/updateCookie', methods=['POST', 'OPTIONS'])
@cross_origin(support_credentials=True)
def updateCookie():
    
    try:

        data = request.json
        model_manager.update_cookie(data['cookie'])

        return jsonify({'response': 'Successful cookie update'}), 200
    
    except Exception as e:
        print(f"An error occurred: {e}")
        return jsonify({'error': 'An error occurred'}), 500


@app.route('/updateRealCatalogues', methods=['POST', 'OPTIONS'])
@cross_origin(support_credentials=True)
def updateRealCatalogues():
    try:
        
        model_manager.load_catalogues(reload_real_only=True)

        return jsonify({"response": "Real catalogues updated"}), 200
    except Exception as e:
        print(f"An error occurred: {e}")
        return jsonify({"error": "An error occurred"}), 500


@app.route('/indexLocalData', methods=['POST', 'OPTIONS'])
@cross_origin(support_credentials=True)
def index_local_data():
    try:
        data = request.json

        # Extract expected fields
        suspect_id = data.get('suspect_id')
        biometric_type = data.get('biometric_type', '').upper()
        bio_url = data.get('full_biometric_data_url')
        owner = data.get('owner').upper()
        sensitive = data.get('sensitive', False)


        # Prepare storage for results
        hashcode = []

        # Determine and process biometric type
        if biometric_type == "IMAGE" and bio_url:
            try:
                hashcode = indexFace(bio_url, model_manager, model_manager.get_model("face"), model_manager.api_encrypt_decrypt, suspect_id, owner, sensitive)
                gc.collect()
            except Exception as e:
                print(f"Error in face index hash calculation: {e}")

        elif biometric_type == "VOICE" and bio_url:
            try:
                hashcode = indexVoice(bio_url, model_manager, model_manager.get_model("voice"), model_manager.api_voice_finger, suspect_id, owner, sensitive)
                gc.collect()
            except Exception as e:
                print(f"Error in voice index hash calculation: {e}")

        elif biometric_type == "FINGERPRINT" and bio_url:
            try:
                hashcode = indexFinger(bio_url, model_manager, model_manager.get_model("finger"), model_manager.api_voice_finger, suspect_id, owner, sensitive)
                gc.collect()
            except Exception as e:
                print(f"Error in fingerprint index hash calculation: {e}")
        else:
            return jsonify({"error": "Unsupported or missing biometric type or URL"}), 400

        # Successful response
        response = {
            "criminal_id": suspect_id,
            "biometric_type_stored": biometric_type.capitalize()
        }
        return jsonify(response), 200

    except Exception as e:
        print(f"An error occurred: {e}")
        return jsonify({'error': 'An error occurred'}), 500




@app.route('/calculateHashForSearching', methods=['POST', 'OPTIONS'])
@cross_origin(support_credentials=True)
def calculateHashForSearching():

    try:
        data = request.json

        response_type = {
            "IMAGE": data['type'].get('image', False),
            "FINGERPRINT": data['type'].get('fingerprint', False),
            "VOICE": data['type'].get('voice', False)
        }

        hash_representation = None
        voice_hashcode = None
        finger_hashcode = None
        bound = None

        if response_type["IMAGE"] and 'full_facial_image_url' in data and data['full_facial_image_url']:
            try:
                hash_representation, bound = calculateHashForSearchingFace(data['full_facial_image_url'], model_manager, model_manager.get_model("face"), model_manager.api_face)
                # print(hash_representation)
                gc.collect()
            except Exception as e:
                print(f"Error in face hash calculation: {e}")

        if response_type["VOICE"] and 'full_voice_url' in data and data['full_voice_url']:
            try:
                voice_hashcode = calculateHashForSearchingVoice(data['full_voice_url'], model_manager, model_manager.get_model("voice"), model_manager.api_voice_finger)
                # print(voice_hashcode)
                gc.collect()
            except Exception as e:
                print(f"Error in voice hash calculation: {e}")
            
        
        if response_type["FINGERPRINT"] and 'full_fingerprint_url' in data and data['full_fingerprint_url']:
            try:
                finger_hashcode = calculateHashForSearchingFinger(data['full_fingerprint_url'], model_manager, model_manager.get_model("finger"), model_manager.api_voice_finger)
                # print(voice_hashcode)
                gc.collect()
            except Exception as e:
                print(f"Error in finger hash calculation: {e}")

        response = {
            "type": response_type,
            "face": hash_representation if hash_representation is not None else [],
            "voice": voice_hashcode if voice_hashcode is not None else [],
            "fingerprint": finger_hashcode if finger_hashcode is not None else [],
            "bound": bound.tolist() if bound is not None else [],
        }

        return jsonify(response), 200

       
    except Exception as e:
        print(f"An error occurred: {e}")
        return jsonify({'error': 'An error occurred'}), 500



@app.route('/searchForMatches', methods=['POST', 'OPTIONS'])
@cross_origin(support_credentials=True)
def searchForMatches():
    try:
        data = request.json

        json_objects = {}

        face_id, face_scores = None, []
        voice_id, voice_scores = None, []
        finger_id, finger_scores = None, []


        if 'face' in data and len(data['face']) > 0:
            try:
                face_id, face_scores = searchForMatchesFace(data['face'], data['from'], data['to'], model_manager, data['bound'], model_manager.api_encrypt_decrypt, model_manager.api_face)
                face_scores = face_scores.tolist()
                face_scores = [round(score, 2) for score in face_scores] 
                print(face_id)
            except Exception as e:
                print(f"Error in face matching: {e}")


        if 'voice' in data and len(data['voice']) > 0:
            try:
                voice_id, voice_scores = searchForMatchesVoice(data['voice'], data['from'], data['to'], model_manager, model_manager.api_voice_finger)
                print(voice_id)
            except Exception as e:
                print(f"Error in voice matching: {e}")


        if 'fingerprint' in data and len(data['fingerprint']) > 0:
            try:
                finger_id, finger_scores = searchForMatchesFinger(data['fingerprint'], data['from'], data['to'], model_manager, model_manager.api_voice_finger)
                print(finger_id)
            except Exception as e:
                print(f"Error in voice matching: {e}")


        if face_id is not None and face_scores:
            for i in range(len(face_id)):
                pseudo_id = str(face_id[i])
                if pseudo_id not in json_objects:
                    json_objects[pseudo_id] = {
                        "pseudo_id": pseudo_id,
                        "scores": {
                            "face": face_scores[i],
                            "voice": 0,
                            "fingerprint": 0
                        }
                    }


        if voice_id is not None and voice_scores:
            for i in range(len(voice_id)):
                pseudo_id = str(voice_id[i])
                if pseudo_id in json_objects:
                    json_objects[pseudo_id]["scores"]["voice"] = voice_scores[i]
                else:
                    json_objects[pseudo_id] = {
                        "pseudo_id": pseudo_id,
                        "scores": {
                            "face": 0,
                            "voice": voice_scores[i],
                            "fingerprint": 0
                        }
                    }

        
        if finger_id is not None and finger_scores:
            for i in range(len(finger_id)):
                pseudo_id = str(finger_id[i])
                if pseudo_id in json_objects:
                    json_objects[pseudo_id]["scores"]["fingerprint"] = finger_scores[i]
                else:
                    json_objects[pseudo_id] = {
                        "pseudo_id": pseudo_id,
                        "scores": {
                            "face": 0,
                            "voice": 0,
                            "fingerprint": finger_scores[i]
                        }
                    }


        json_objects_list = list(json_objects.values())
        gc.collect()

        return jsonify(json_objects_list), 200

    except Exception as e:
        print(f"An error occurred: {e}")
        return jsonify({'error': 'An error occurred'}), 500



@app.route('/isAlive', methods=['GET',])
@cross_origin(support_credentials=True)
def isAlive():
    try:
        return jsonify({'response': 'Component is Alive - v7.0'}), 200
    
    except Exception as e:
        # Catch any exceptions that occur and print the error
        print(f"An error occurred: {e}")
        return jsonify({'error': 'An error occurred'}), 500
 

if __name__ == '__main__':
    # model_manager = ModelManager()
    # app.run()
    model_manager = ModelManager()
    app.run(port=5005)

# export FLASK_APP=flask_main.py
# export FLASK_RUN_PORT=8500
# flask run 
# flask run --host=0.0.0.0
