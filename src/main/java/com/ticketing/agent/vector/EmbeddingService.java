package com.ticketing.agent.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EmbeddingService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/embeddings";
    private static final String MODEL_NAME = "nomic-embed-text";
    private final OkHttpClient client;
    private final Gson gson;

    public EmbeddingService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public List<Float> getEmbedding(String text) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL_NAME);
        requestBody.addProperty("prompt", text);   // 改为 prompt

        Request request = new Request.Builder()
                .url(OLLAMA_URL)
                .post(RequestBody.create(requestBody.toString(),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            // 确保返回的 embedding 字段不为空
            return gson.fromJson(jsonResponse.get("embedding"),
                    new TypeToken<List<Float>>() {}.getType());
        }
    }

    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
