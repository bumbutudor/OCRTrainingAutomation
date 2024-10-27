package org.example;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

public class VectorOCRAPI {
    public static void main(String[] args) {
        String urlString = "http://localhost:8000/predict/";
        String imagePath = "C:\\Users\\mariu\\Desktop\\Vector based OCR\\.ipynb_checkpoints\\dataset\\в_217.png"; // Replace with your image path

        try {
            // Open a connection to the API
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=---ContentBoundary");
            connection.setDoOutput(true);

            // Create the multipart body
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(("-----ContentBoundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" +
                    new File(imagePath).getName() + "\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes());

            // Write the image file bytes to the output stream
            Files.copy(new File(imagePath).toPath(), outputStream);
            outputStream.write("\r\n-----ContentBoundary--\r\n".getBytes());
            outputStream.flush();
            outputStream.close();

            // Get the response
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                String response = new String(connection.getInputStream().readAllBytes());
                System.out.println("Response: " + response);
            } else {
                System.err.println("Error: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
