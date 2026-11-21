import tensorflow as tf

# Load your trained Keras model
model = tf.keras.models.load_model("saved_models/lightweight_1d_cnn.keras")

# Create TensorFlow Lite converter
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# Convert the model
tflite_model = converter.convert()

# Save the Android-compatible model
with open("road_hazard_model.tflite", "wb") as f:
    f.write(tflite_model)

print("SUCCESS: Model converted to road_hazard_model.tflite")
