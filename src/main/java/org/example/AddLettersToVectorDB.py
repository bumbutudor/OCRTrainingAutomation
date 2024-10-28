# Program 1: Vectorizing and Adding Images to the Weaviate Database

import weaviate
from weaviate import WeaviateClient
from weaviate.connect import ConnectionParams
from weaviate.classes.config import Property, Configure, DataType
import os
from pathlib import Path
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input
from tensorflow.keras.preprocessing import image as keras_image
import numpy as np
import tensorflow as tf

# Path to the base dataset directory
BASE_DATASET_PATH = "C:\\Users\\mariu\\Desktop\\Vector based OCR\\.ipynb_checkpoints\\LettersDataset20\\"

# Connection parameters for local Docker Weaviate instance
connection_params = ConnectionParams.from_params(
    http_host="localhost",  # This matches the Docker port mapping
    http_port=8080,         # Port exposed by Docker
    http_secure=False,      # Weaviate runs on HTTP by default unless configured with SSL
    grpc_host="localhost",  # Same host for gRPC communication
    grpc_port=50051,        # Port exposed by Docker for gRPC
    grpc_secure=False       # Weaviate runs without gRPC security unless configured with SSL
)

# Load the pre-trained MobileNetV2 model for vectorization
# Assuming that this model has been fine-tuned for handwritten character recognition
model = MobileNetV2(weights='imagenet', include_top=False, pooling='avg', input_shape=(224, 224, 3))

# Wrapping the predict function with @tf.function to optimize and reduce retracing
@tf.function
def predict_vector(img_data):
    return model(img_data)

# Function to vectorize an image
def vectorize_image(img_path):
    img = keras_image.load_img(img_path, target_size=(224, 224))
    img_data = keras_image.img_to_array(img)
    img_data = np.expand_dims(img_data, axis=0)
    img_data = preprocess_input(img_data)

    # Use the optimized predict function
    embedding = predict_vector(tf.convert_to_tensor(img_data))
    return embedding.numpy().flatten()

# Function to add images to Weaviate collection
def add_images_from_folder(client, folder_path, label):
    image_files = list(Path(folder_path).glob('*.*'))
    data_objects = []

    for img_file in image_files:
        vector = vectorize_image(str(img_file))
        data_object = weaviate.classes.data.DataObject(
            properties={"label": label},
            vector=vector.tolist()
        )
        data_objects.append(data_object)

    client.collections.get("LetterImage").data.insert_many(data_objects)

# Main process using a context manager
with WeaviateClient(connection_params=connection_params) as client:
    try:
        # Retrieve the existing collections
        collections = client.collections.list_all()

        # Extract class names
        existing_classes = list(collections.keys())

        if "LetterImage" in existing_classes:
            print("Class 'LetterImage' already exists. Using the existing class.")

            # Get the 'LetterImage' collection
            collection = client.collections.get("LetterImage")
        else:
            # Create the class since it doesn't exist
            class_definition = {
                "class": "LetterImage",
                "description": "An image of a handwritten letter",
                "vectorizer": "none",
                "properties": [
                    {
                        "name": "label",
                        "dataType": ["string"],  # Use a list to indicate data type
                        "description": "The label (letter) of the image"
                    }
                ]
            }
            client.collections.create_from_dict(class_definition)
            print("Class 'LetterImage' created successfully.")

        # Add training images to Weaviate
        TRAINING_FOLDERS = {
            'а': f'{BASE_DATASET_PATH}а',
            'б': f'{BASE_DATASET_PATH}б',
            'ꙗ': f'{BASE_DATASET_PATH}ꙗ',
            'и': f'{BASE_DATASET_PATH}и',
            'н': f'{BASE_DATASET_PATH}н',
            'в': f'{BASE_DATASET_PATH}в',
            'о': f'{BASE_DATASET_PATH}о',
            'р': f'{BASE_DATASET_PATH}р',
            'ѫ': f'{BASE_DATASET_PATH}ѫ',
            'с': f'{BASE_DATASET_PATH}с',
            'т': f'{BASE_DATASET_PATH}т',
            'г': f'{BASE_DATASET_PATH}г',
            'д': f'{BASE_DATASET_PATH}д',
            'є': f'{BASE_DATASET_PATH}є',
            'ж': f'{BASE_DATASET_PATH}ж',
            'з': f'{BASE_DATASET_PATH}з',
            'ї': f'{BASE_DATASET_PATH}ї',
            'к': f'{BASE_DATASET_PATH}к',
            'л': f'{BASE_DATASET_PATH}л',
            'м': f'{BASE_DATASET_PATH}м',
            'п': f'{BASE_DATASET_PATH}п',
            'ꙋ': f'{BASE_DATASET_PATH}ꙋ',
            'у': f'{BASE_DATASET_PATH}у',
            'ф': f'{BASE_DATASET_PATH}ф',
            'х': f'{BASE_DATASET_PATH}х',
            'ц': f'{BASE_DATASET_PATH}ц',
            'ѡ': f'{BASE_DATASET_PATH}ѡ',
            'ч': f'{BASE_DATASET_PATH}ч',
            'ш': f'{BASE_DATASET_PATH}ш',
            'щ': f'{BASE_DATASET_PATH}щ',
            'ъ': f'{BASE_DATASET_PATH}ъ',
            'ь': f'{BASE_DATASET_PATH}ь',
            'ѣ': f'{BASE_DATASET_PATH}ѣ',
            'ѧ': f'{BASE_DATASET_PATH}ѧ',
            'ꙟ': f'{BASE_DATASET_PATH}ꙟ',
            'џ': f'{BASE_DATASET_PATH}џ'
        }

        for label, folder_path in TRAINING_FOLDERS.items():
            add_images_from_folder(client, folder_path, label)
            print(f"Added images for label '{label}' to the class.")

    except Exception as e:
        print(f"Error: {e}")
