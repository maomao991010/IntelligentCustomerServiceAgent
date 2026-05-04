package com.ticketing.agent.vector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class QdrantHttpClient {

    private static final String QDRANT_URL = "http://106.54.15.105:6333";
    private static final String QDRANT_API_KEY = "IYnVQqR0+QOtPupBObRSEWuBWGnxtZlwylWcngPJMT4=";
    private final OkHttpClient client;
    private final Gson gson;

    public QdrantHttpClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    /**
     * 创建集合
     */
    public boolean createCollection(String collectionName, int vectorSize) throws IOException {
        JsonObject requestBody = new JsonObject();
        JsonObject vectors = new JsonObject();
        vectors.addProperty("size", vectorSize);
        vectors.addProperty("distance", "Cosine");
        requestBody.add("vectors", vectors);

        Request request = new Request.Builder()
                .url(QDRANT_URL + "/collections/" + collectionName)
                .put(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .addHeader("api-key", QDRANT_API_KEY)   // 添加 API Key
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    /**
     * 删除集合
     */
    public boolean deleteCollection(String collectionName) throws IOException {
        Request request = new Request.Builder()
                .url(QDRANT_URL + "/collections/" + collectionName)
                .delete()
                .addHeader("api-key", QDRANT_API_KEY)   // 添加 API Key
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    /**
     * 检查集合是否存在
     */
    public boolean collectionExists(String collectionName) throws IOException {
        Request request = new Request.Builder()
                .url(QDRANT_URL + "/collections")
                .get()
                .addHeader("api-key", QDRANT_API_KEY)   // 添加 API Key
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return false;
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            JsonObject result = json.getAsJsonObject("result");
            if (result == null) return false;
            JsonArray collections = result.getAsJsonArray("collections");
            if (collections == null) return false;
            for (int i = 0; i < collections.size(); i++) {
                JsonObject coll = collections.get(i).getAsJsonObject();
                if (coll.get("name").getAsString().equals(collectionName)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 添加点（单点）
     */
    public boolean addPoint(String collectionName, List<Float> vector, Map<String, String> payload) throws IOException {
        // 构建 point
        JsonObject point = new JsonObject();
        point.addProperty("id", UUID.randomUUID().toString());
        JsonArray vectorArray = new JsonArray();
        for (Float f : vector) {
            vectorArray.add(f);
        }
        point.add("vector", vectorArray);

        JsonObject payloadObj = new JsonObject();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            payloadObj.addProperty(entry.getKey(), entry.getValue());
        }
        point.add("payload", payloadObj);

        JsonArray pointsArray = new JsonArray();
        pointsArray.add(point);

        JsonObject requestBody = new JsonObject();
        requestBody.add("points", pointsArray);

        Request request = new Request.Builder()
                .url(QDRANT_URL + "/collections/" + collectionName + "/points")
                .put(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .addHeader("api-key", QDRANT_API_KEY)   // 添加 API Key
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    /**
     * 搜索相似向量
     */
    public List<SearchResult> search(String collectionName, List<Float> queryVector, int topK) throws IOException {
        JsonObject requestBody = new JsonObject();
        JsonArray vectorArray = new JsonArray();
        for (Float f : queryVector) {
            vectorArray.add(f);
        }
        requestBody.add("vector", vectorArray);
        requestBody.addProperty("limit", topK);
        requestBody.addProperty("with_payload", true);

        Request request = new Request.Builder()
                .url(QDRANT_URL + "/collections/" + collectionName + "/points/search")
                .addHeader("api-key", QDRANT_API_KEY)
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Search failed: " + response);
            }
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            JsonArray results = json.getAsJsonArray("result");

            List<SearchResult> searchResults = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JsonObject res = results.get(i).getAsJsonObject();
                SearchResult sr = new SearchResult();
                sr.score = res.get("score").getAsFloat();
                sr.payload = gson.fromJson(res.get("payload"), Map.class);
                searchResults.add(sr);
            }
            return searchResults;
        }
    }

    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    public static class SearchResult {
        public float score;
        public Map<String, String> payload;
    }
}