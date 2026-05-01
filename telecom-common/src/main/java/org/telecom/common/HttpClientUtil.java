package org.telecom.common;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.io.File;
import java.io.IOException;

public class HttpClientUtil {

    private HttpClientUtil() {
    }

    public static void sendFile(String url, File file, String fieldName) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(url);

            var entity = MultipartEntityBuilder.create()
                    .addBinaryBody(fieldName, file)
                    .build();

            post.setEntity(entity);

            client.execute(post, response -> {
                System.out.println("Upload status: " + response.getCode());
                return null;
            });
        }
    }

    public static void sendFile(String url, File file) throws IOException {
        sendFile(url, file, "file");
    }

    public static void sendFileAsync(String url, File file, FileCallback callback) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(url);

            var entity = MultipartEntityBuilder.create()
                    .addBinaryBody("file", file)
                    .build();

            post.setEntity(entity);

            client.execute(post, response -> {
                if (callback != null) {
                    callback.onComplete(response.getCode());
                }
                return null;
            });
        } catch (IOException e) {
            if (callback != null) {
                callback.onError(e);
            }
        }
    }

    public interface FileCallback {
        void onComplete(int statusCode);
        void onError(Exception e);
    }
}