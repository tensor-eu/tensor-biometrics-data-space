# finger_bio_utils.py

import cv2
import numpy as np
from skimage.morphology import skeletonize
from skimage.util import invert

def preprocess_fingerprint(image_path):
    """
    Preprocess the fingerprint image:
    - Convert to grayscale
    - Apply Gaussian blur
    - Binarize the image
    - Invert colors
    - Thinning (skeletonization)
    
    :param image_path: Path to the fingerprint image
    :return: Preprocessed (thinned) fingerprint image
    """
    # Read the image
    image = cv2.imread(image_path)
    if image is None:
        print(f"Error: Unable to load image at {image_path}")
        return None

    # Convert to grayscale
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    # Apply Gaussian blur
    blurred = cv2.GaussianBlur(gray, (5, 5), 0)

    # Binarize the image using Otsu's thresholding
    _, binary = cv2.threshold(blurred, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    # Invert colors: ridges should be white, background black
    inverted = cv2.bitwise_not(binary)

    # Convert to boolean array for skeletonization
    inverted_bool = inverted > 0

    # Skeletonization
    skeleton = skeletonize(inverted_bool)

    # Convert back to uint8 image
    skeleton_image = (skeleton * 255).astype(np.uint8)

    return skeleton_image

def extract_minutiae_points(skeleton_image):
    """
    Extract minutiae points (ridge endings and bifurcations) from the thinned fingerprint image.

    :param skeleton_image: Preprocessed (thinned) fingerprint image
    :return: List of minutiae points as (x, y) coordinates
    """
    # Define the kernel for neighborhood checking
    kernel = np.array([[1, 1, 1],
                       [1,  0, 1],
                       [1, 1, 1]], dtype=np.uint8)

    minutiae_points = []

    # Pad the image to handle border pixels
    padded_img = np.pad(skeleton_image, ((1, 1), (1, 1)), 'constant', constant_values=0)

    # Iterate through pixels
    rows, cols = skeleton_image.shape
    for i in range(1, rows + 1):
        for j in range(1, cols + 1):
            if padded_img[i, j] == 255:
                neighborhood = padded_img[i - 1:i + 2, j - 1:j + 2]
                # Count the number of white pixels in the neighborhood
                crossing_number = np.sum(neighborhood * kernel) // 255
                if crossing_number == 1:
                    # Ridge ending
                    minutiae_points.append((j - 1, i - 1))
                elif crossing_number == 3:
                    # Bifurcation
                    minutiae_points.append((j - 1, i - 1))

    return minutiae_points

def generate_bio_descriptor(minutiae_points, descriptor_length=64):
    """
    Generate a biometric descriptor from the minutiae points.

    :param minutiae_points: List of minutiae points as (x, y) coordinates
    :param descriptor_length: Desired length of the biometric descriptor
    :return: Numpy array of the biometric descriptor
    """
    if not minutiae_points:
        print("No minutiae points detected.")
        return None

    # Convert list of tuples to numpy array
    minutiae_array = np.array(minutiae_points).flatten()

    # If there are more minutiae points than needed, truncate the array
    if len(minutiae_array) > descriptor_length:
        bio_descriptor = minutiae_array[:descriptor_length]
    else:
        # If less, pad with zeros
        padding_length = descriptor_length - len(minutiae_array)
        bio_descriptor = np.pad(minutiae_array, (0, padding_length), 'constant')

    return bio_descriptor.astype(np.uint16)  # Use uint16 to represent coordinates

def visualize_minutiae(skeleton_image, minutiae_points, output_path='./visualization/minutiae_visualization.png'):
    """
    Visualize the minutiae points on the skeleton image and save the result.

    :param skeleton_image: Preprocessed (thinned) fingerprint image
    :param minutiae_points: List of minutiae points as (x, y) coordinates
    :param output_path: Path to save the visualization image
    """
    # Convert to color image for visualization
    color_image = cv2.cvtColor(skeleton_image, cv2.COLOR_GRAY2BGR)

    # Draw circles at minutiae points
    for x, y in minutiae_points:
        cv2.circle(color_image, (x, y), radius=3, color=(0, 0, 255), thickness=-1)

    # Save the visualization image
    cv2.imwrite(output_path, color_image)
    print(f"Minutiae visualization saved at {output_path}")
