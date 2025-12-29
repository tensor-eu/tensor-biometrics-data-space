# face_bio_utils.py

import cv2
import dlib
import numpy as np

# Initialize face detector and landmark predictor
detector = dlib.get_frontal_face_detector()
predictor = dlib.shape_predictor("./biometric_FE/shape_predictor_68_face_landmarks.dat")  # Ensure the path is correct

def extract_face_features(image_path):
    """
    Extract facial features from an image.
    :param image_path: Path to the image file
    :return: List of facial feature points or None if no face is detected
    """
    image = cv2.imread(image_path)
    if image is None:
        print(f"Error: Unable to load image at {image_path}")
        return None
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    faces = detector(gray)
    
    if len(faces) == 0:
        print("No face detected in image:", image_path)
        return None
    
    for face in faces:
        landmarks = predictor(gray, face)
        face_features = []
        for n in range(0, 68):  # Use 68 facial landmarks
            x = landmarks.part(n).x
            y = landmarks.part(n).y
            face_features.append((x, y))
        return face_features
    return None

def generate_bio_descriptor(face_features):
    """
    Generate a biometric descriptor from facial features.
    :param face_features: List of facial feature points
    :return: Numpy array of the biometric descriptor
    """
    if face_features is None:
        return None
    bio_descriptor = np.array(face_features).flatten()
    return bio_descriptor

def visualize_landmarks(image_path, face_features, output_path='./visualization/landmarks_visualization.png'):
    """
    Visualize the facial landmarks on the original image and save the result.

    :param image_path: Path to the original image file
    :param face_features: List of facial feature points as (x, y) tuples
    :param output_path: Path to save the visualization image
    """
    # Load the original image
    image = cv2.imread(image_path)
    if image is None:
        print(f"Error: Unable to load image at {image_path}")
        return

    # Draw circles at each facial landmark point
    for (x, y) in face_features:
        cv2.circle(image, (x, y), radius=2, color=(0, 255, 0), thickness=-1)  # Green dots

    # Optionally, draw lines between landmarks for better visualization
    # Example: Draw lines between consecutive points
    for i in range(len(face_features) - 1):
        cv2.line(image, face_features[i], face_features[i + 1], color=(255, 0, 0), thickness=1)  # Blue lines

    # Save the visualization image
    cv2.imwrite(output_path, image)
    print(f"Landmarks visualization saved at {output_path}")
