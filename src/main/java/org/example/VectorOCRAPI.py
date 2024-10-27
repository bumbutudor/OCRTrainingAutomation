from fastapi import FastAPI, UploadFile, File
from pathlib import Path
import uvicorn
import weaviate
from weaviate.collections import Collection  # For type hinting
import tensorflow as tf
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input
from tensorflow.keras.preprocessing import image as keras_image
import numpy as np
import shutil
from collections import Counter

app = FastAPI()

# Load the pre-trained MobileNetV2 model for vectorization
model = MobileNetV2(weights='imagenet', include_top=False, pooling='avg', input_shape=(224, 224, 3))

# Wrapping the predict function with @tf.function to avoid retracing and improve performance
@tf.function
def predict_vector(img_data):
    return model(img_data)

# Function to vectorize an image
def vectorize_image(img_path):
    img = keras_image.load_img(img_path, target_size=(224, 224))
    img_data = keras_image.img_to_array(img)
    img_data = tf.expand_dims(img_data, axis=0)
    img_data = preprocess_input(img_data)
    embedding = predict_vector(tf.convert_to_tensor(img_data))
    return embedding.numpy().flatten()

# Function to predict the label of a query image using the nearest vector search
def predict_query_image(collection: Collection, query_img_path, k=1):
    query_vector = vectorize_image(query_img_path)

    # Correctly formatted near_vector query using the collection object in v4
    response = collection.query.near_vector(
        near_vector=query_vector.tolist(),
        limit=k,
        return_properties=["label"]
    )

    # Extract labels from the response
    try:
        results = response.objects
        labels = [obj.properties['label'] for obj in results]

        if not labels:
            return ""

        # Determine the most common label among the nearest neighbors
        label_counts = Counter(labels)
        most_common = label_counts.most_common(1)[0]

        # Return the label if at least 6 neighbors agree, otherwise return the closest match
        if most_common[1] >= 1:
            return most_common[0]
        else:
            return labels[0]
    except KeyError:
        return ""

# Endpoint to receive an image and return the predicted label
@app.post("/predict/")
async def predict_image(file: UploadFile = File(...)):
    client = weaviate.connect_to_local()
    try:
        # Retrieve the collection
        collection = client.collections.get("LetterImage")
        # Save the uploaded file temporarily
        temp_file = Path("temp_image.jpg")
        with temp_file.open("wb") as buffer:
            shutil.copyfileobj(file.file, buffer)

        # Predict the label
        predicted_label = predict_query_image(collection, str(temp_file))
        return predicted_label.strip()
    except Exception as e:
        return ""
    finally:
        client.close()
        if temp_file.exists():
            temp_file.unlink()

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)

# Clear TensorFlow session to release memory
from tensorflow.keras import backend as K
K.clear_session()
