package com.ticketing.agent.vector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ticketing.config.AiCustomerServiceProperties;
import com.ticketing.entity.Faq;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai.customer-service.vector-store", name = "enabled", havingValue = "true")
public class VectorStoreService {

    @Autowired
    private AiCustomerServiceProperties properties;

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Autowired(required = false)
    private com.ticketing.agent.token.TokenUsageManager tokenUsageManager;

    private EmbeddingStore<TextSegment> embeddingStore;
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    private String qdrantUrl;
    private String qdrantApiKey;
    private String qdrantCollectionName;
    private AiCustomerServiceProperties.VectorStoreConfig vectorStoreConfig;
    
    private enum ModelType {
        OLLAMA,
        ALI
    }
    
    private ModelType currentModelType;

    public VectorStoreService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }
    
    @PostConstruct
    public void init() {
        log.info("初始化向量存储服务...");
        try {
            this.vectorStoreConfig = properties.getVectorStore();
            if (embeddingModel == null) {
                if (!tryInitEmbeddingModel()) {
                    log.warn("向量存储功能将禁用！请确保 Ollama 或阿里云 API Key 至少有一个配置正确");
                    return;
                }
            }

            if ("qdrant".equalsIgnoreCase(vectorStoreConfig.getProvider())) {
                AiCustomerServiceProperties.QdrantConfig qdrantConfig = vectorStoreConfig.getQdrant();
                try {
                    String collectionName = getCollectionNameForCurrentModel(qdrantConfig.getCollectionName());
                    
                    embeddingStore = QdrantEmbeddingStore.builder()
                            .host(qdrantConfig.getHost())
                            .port(qdrantConfig.getPort())
                            .collectionName(collectionName)
                            .apiKey(qdrantConfig.getApiKey())
                            .build();
                    
                    Integer httpPort = qdrantConfig.getHttpPort();
                    if (httpPort == null) {
                        httpPort = 6333;
                    }
                    
                    this.qdrantUrl = "http://" + qdrantConfig.getHost() + ":" + httpPort;
                    this.qdrantApiKey = qdrantConfig.getApiKey();
                    this.qdrantCollectionName = collectionName;
                    
                    log.info("Qdrant 向量存储初始化成功: host={}, httpPort={}, collectionName={}, modelType={}",
                            qdrantConfig.getHost(), httpPort, collectionName, currentModelType);
                } catch (Exception e) {
                    log.error("Qdrant 连接失败，向量存储功能将禁用！请检查 Qdrant 是否启动: {}", e.getMessage());
                    embeddingStore = null;
                }
            } else {
                log.warn("未支持的向量存储提供商: {}", vectorStoreConfig.getProvider());
            }
        } catch (Exception e) {
            log.error("向量存储初始化失败，向量存储功能将禁用！", e);
            embeddingStore = null;
            embeddingModel = null;
        }
    }

    public boolean isAvailable() {
        return embeddingStore != null && embeddingModel != null;
    }

    private boolean tryInitEmbeddingModel() {
        boolean initialized = false;
        String ollamaError = null;
        String aliError = null;
        ModelType previousModelType = this.currentModelType;
        String embeddingProvider = vectorStoreConfig.getEmbeddingProvider();
        
        log.info("根据配置初始化嵌入模型: embeddingProvider={}", embeddingProvider);
        
        boolean shouldTryOllama = "ollama".equalsIgnoreCase(embeddingProvider) || "auto".equalsIgnoreCase(embeddingProvider);
        boolean shouldTryAli = "dashscope-openai".equalsIgnoreCase(embeddingProvider) || "auto".equalsIgnoreCase(embeddingProvider);
        
        if (shouldTryOllama) {
            log.info("尝试初始化本地 Ollama 嵌入模型...");
            try {
                AiCustomerServiceProperties.OllamaConfig ollamaConfig = vectorStoreConfig.getOllama();
                String modelName = vectorStoreConfig.getEmbeddingModelName();
                EmbeddingModel ollamaModel = OllamaEmbeddingModel.builder()
                        .baseUrl(ollamaConfig.getBaseUrl())
                        .modelName(modelName)
                        .build();
                
                Embedding testEmbedding = ollamaModel.embed("测试").content();
                if (isValidEmbedding(testEmbedding)) {
                    embeddingModel = ollamaModel;
                    this.currentModelType = ModelType.OLLAMA;
                    log.info("使用本地 Ollama 嵌入模型成功: baseUrl={}, modelName={}", ollamaConfig.getBaseUrl(), modelName);
                    initialized = true;
                } else {
                    ollamaError = "本地 Ollama 嵌入模型返回的向量无效";
                    log.warn(ollamaError);
                }
            } catch (Exception e) {
                ollamaError = "初始化本地 Ollama 模型失败: " + e.getMessage();
                log.warn(ollamaError);
            }
        }
        
        if (!initialized && shouldTryAli) {
            log.info("尝试初始化阿里云 text-embedding-v4 嵌入模型...");
            try {
                AiCustomerServiceProperties.DashscopeConfig dashscopeConfig = properties.getDashscope();
                String apiKey = dashscopeConfig.getApiKey();
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    aliError = "阿里云 API Key 未配置";
                    log.warn(aliError);
                } else {
                    AiCustomerServiceProperties.DashscopeOpenaiConfig dashscopeOpenaiConfig = vectorStoreConfig.getDashscopeOpenai();
                    String modelName = dashscopeOpenaiConfig.getEmbeddingModelName();
                    
                    EmbeddingModel aliModel = OpenAiEmbeddingModel.builder()
                            .baseUrl(dashscopeOpenaiConfig.getBaseUrl())
                            .apiKey(apiKey)
                            .modelName(modelName)
                            .build();
                    
                    Embedding testEmbedding = aliModel.embed("测试").content();
                    if (isValidEmbedding(testEmbedding)) {
                        embeddingModel = aliModel;
                        this.currentModelType = ModelType.ALI;
                        log.info("使用阿里云 text-embedding-v4 嵌入模型成功: baseUrl={}, modelName={}", dashscopeOpenaiConfig.getBaseUrl(), modelName);
                        initialized = true;
                    } else {
                        aliError = "阿里云 text-embedding-v4 嵌入模型返回的向量无效";
                        log.warn(aliError);
                    }
                }
            } catch (Exception e) {
                aliError = "初始化阿里云 text-embedding-v4 模型失败: " + e.getMessage();
                log.warn(aliError);
            }
        }
        
        if (!initialized) {
            log.error("嵌入模型初始化失败！配置的嵌入模型不可用。embeddingProvider={}", embeddingProvider);
            if (ollamaError != null) {
                log.error("Ollama 错误: {}", ollamaError);
            }
            if (aliError != null) {
                log.error("阿里云错误: {}", aliError);
            }
        } else if (previousModelType != null && previousModelType != this.currentModelType) {
            log.info("嵌入模型类型已切换: {} -> {}", previousModelType, this.currentModelType);
            reinitializeEmbeddingStore();
        }
        
        return initialized;
    }
    
    private void reinitializeEmbeddingStore() {
        if ("qdrant".equalsIgnoreCase(vectorStoreConfig.getProvider())) {
            AiCustomerServiceProperties.QdrantConfig qdrantConfig = vectorStoreConfig.getQdrant();
            try {
                String collectionName = getCollectionNameForCurrentModel(qdrantConfig.getCollectionName());
                
                embeddingStore = QdrantEmbeddingStore.builder()
                        .host(qdrantConfig.getHost())
                        .port(qdrantConfig.getPort())
                        .collectionName(collectionName)
                        .apiKey(qdrantConfig.getApiKey())
                        .build();
                
                this.qdrantCollectionName = collectionName;
                
                log.info("Qdrant 向量存储已重新初始化: host={}, collectionName={}, modelType={}",
                        qdrantConfig.getHost(), collectionName, currentModelType);
            } catch (Exception e) {
                log.error("重新初始化 Qdrant 失败: {}", e.getMessage());
            }
        }
    }
    
    private String getCollectionNameForCurrentModel(String defaultCollectionName) {
        if (currentModelType == ModelType.ALI) {
            return defaultCollectionName + "-ali-v4";
        }
        return defaultCollectionName;
    }

    public boolean isEmbeddingModelAvailable() {
        if (embeddingModel == null) {
            log.warn("当前未初始化嵌入模型，尝试重新初始化...");
            return tryInitEmbeddingModel();
        }
        
        try {
            Embedding testEmbedding = embeddingModel.embed("测试").content();
            if (isValidEmbedding(testEmbedding)) {
                return true;
            } else {
                log.warn("当前嵌入模型返回的向量无效，尝试切换到其他模型...");
                return tryInitEmbeddingModel();
            }
        } catch (Exception e) {
            log.warn("当前嵌入模型可用性检查失败，尝试切换到其他模型: {}", e.getMessage());
            return tryInitEmbeddingModel();
        }
    }

    public boolean isVectorStoreAvailable() {
        if (embeddingStore == null || qdrantUrl == null) {
            return false;
        }
        try {
            Request request = new Request.Builder()
                    .url(qdrantUrl + "/collections/" + qdrantCollectionName)
                    .get()
                    .build();
            
            if (qdrantApiKey != null && !qdrantApiKey.trim().isEmpty()) {
                request = request.newBuilder()
                        .addHeader("api-key", qdrantApiKey)
                        .build();
            }
            
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.warn("向量存储可用性检查失败", e);
            return false;
        }
    }

    public boolean healthCheck() {
        if (!isAvailable()) {
            log.warn("向量存储组件未初始化，健康检查失败");
            return false;
        }
        
        if (!isEmbeddingModelAvailable()) {
            log.warn("嵌入模型不可用，健康检查失败");
            return false;
        }
        
        try {
            List<EmbeddingMatch<TextSegment>> matches = searchViaHttp("健康检查", 1, 0.0);
            log.info("向量存储健康检查通过，返回结果数: {}", matches.size());
            return true;
        } catch (Exception e) {
            log.error("向量存储健康检查失败", e);
            return false;
        }
    }

    public boolean tryReconnect() {
        log.info("尝试重新连接向量存储...");
        try {
            init();
            if (isAvailable()) {
                log.info("向量存储重新连接成功");
                return true;
            } else {
                log.error("向量存储重新连接失败");
                return false;
            }
        } catch (Exception e) {
            log.error("向量存储重新连接异常", e);
            return false;
        }
    }

    public void addFaq(Faq faq) {
        if (!isAvailable()) {
            log.warn("向量存储不可用，跳过添加 FAQ: id={}", faq.getId());
            return;
        }

        if (!isEmbeddingModelAvailable()) {
            log.warn("嵌入模型不可用，跳过添加 FAQ: id={}", faq.getId());
            return;
        }

        if (isTokenLimitExceeded() && !isCurrentModelLocal()) {
            log.warn("Token 使用量已达到上限，且当前使用 API 模型，跳过添加 FAQ: id={}", faq.getId());
            return;
        }

        try {
            String content = buildFaqContent(faq);
            log.debug("FAQ 内容构建完成: id={}, contentLength={}", faq.getId(), content.length());
            
            Metadata metadata = buildMetadata(faq);
            TextSegment textSegment = TextSegment.from(content, metadata);
            
            log.debug("开始向量化 FAQ: id={}", faq.getId());
            Embedding embedding = embeddingModel.embed(content).content();
            
            // 统计 token 使用量
            addTokenUsage(estimateTokens(content), !isCurrentModelLocal());
            
            // 详细检查向量
            if (embedding == null) {
                log.error("向量化返回 null，FAQ 向量化失败: id={}", faq.getId());
                return;
            }
            
            float[] vector = embedding.vector();
            if (vector == null) {
                log.error("向量数组为 null，FAQ 向量化失败: id={}", faq.getId());
                return;
            }
            
            log.debug("向量化完成: id={}, vectorLength={}", faq.getId(), vector.length);
            
            if (!isValidEmbedding(embedding)) {
                log.error("向量无效（长度为0或空），跳过添加 FAQ: id={}, vectorLength={}", faq.getId(), vector.length);
                return;
            }
            
            embeddingStore.add(embedding, textSegment);
            log.info("FAQ 添加到向量存储成功: id={}, question={}, vectorLength={}", faq.getId(), faq.getQuestion(), vector.length);
        } catch (Exception e) {
            log.error("添加 FAQ 到向量存储失败: id={}", faq.getId(), e);
        }
    }

    public void addFaqs(List<Faq> faqs) {
        if (!isAvailable()) {
            log.warn("向量存储不可用，跳过批量添加 FAQ");
            return;
        }

        if (!isEmbeddingModelAvailable()) {
            log.warn("嵌入模型不可用，跳过批量添加 FAQ");
            return;
        }

        Set<Long> existingIds = getExistingFaqIds();
        log.info("向量库中已有 FAQ 数量: {}", existingIds.size());

        List<Faq> faqsToAdd = faqs.stream()
                .filter(faq -> !existingIds.contains(faq.getId()))
                .collect(Collectors.toList());

        if (faqsToAdd.isEmpty()) {
            log.info("所有 FAQ 已存在于向量库，跳过添加");
            return;
        }

        log.info("需要添加的新 FAQ 数量: {}", faqsToAdd.size());

        List<TextSegment> segments = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        int skippedCount = 0;

        for (Faq faq : faqsToAdd) {
            try {
                String content = buildFaqContent(faq);
                Metadata metadata = buildMetadata(faq);
                TextSegment textSegment = TextSegment.from(content, metadata);
                Embedding embedding = embeddingModel.embed(content).content();
                
                if (!isValidEmbedding(embedding)) {
                    log.warn("向量无效（长度为0或空），跳过 FAQ: id={}", faq.getId());
                    skippedCount++;
                    continue;
                }
                
                segments.add(textSegment);
                embeddings.add(embedding);
            } catch (Exception e) {
                log.error("处理 FAQ 失败: id={}", faq.getId(), e);
                skippedCount++;
            }
        }

        if (!embeddings.isEmpty()) {
            embeddingStore.addAll(embeddings, segments);
            log.info("批量添加 FAQ 到向量存储成功: count={}, skipped={}", segments.size(), skippedCount);
        } else {
            log.warn("所有 FAQ 的向量都无效，跳过添加");
        }
    }

    private Set<Long> getExistingFaqIds() {
        Set<Long> existingIds = new HashSet<>();
        
        if (!isEmbeddingModelAvailable()) {
            log.warn("嵌入模型不可用，无法获取向量库已有 FAQ，将重新添加所有 FAQ");
            return existingIds;
        }
        
        try {
            Embedding testEmbedding = embeddingModel.embed("test").content();
            
            if (!isValidEmbedding(testEmbedding)) {
                log.warn("测试向量无效，将重新添加所有 FAQ");
                return existingIds;
            }
            
            log.info("使用HTTP API获取向量库已有FAQ ID");
            
            float[] vector = testEmbedding.vector();
            JsonObject requestBody = new JsonObject();
            JsonArray vectorArray = new JsonArray();
            for (Float f : vector) {
                vectorArray.add(f);
            }
            requestBody.add("vector", vectorArray);
            requestBody.addProperty("limit", 10000);
            requestBody.addProperty("with_payload", true);
            
            Request.Builder requestBuilder = new Request.Builder()
                    .url(qdrantUrl + "/collections/" + qdrantCollectionName + "/points/search")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")));
            
            if (qdrantApiKey != null && !qdrantApiKey.trim().isEmpty()) {
                requestBuilder.addHeader("api-key", qdrantApiKey);
            }
            
            Request request = requestBuilder.build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("获取向量库已有 FAQ ID 失败（HTTP状态码: {}），将重新添加所有 FAQ", response.code());
                    return existingIds;
                }
                
                String body = response.body().string();
                JsonObject json = gson.fromJson(body, JsonObject.class);
                JsonArray results = json.getAsJsonArray("result");
                
                for (int i = 0; i < results.size(); i++) {
                    JsonObject res = results.get(i).getAsJsonObject();
                    JsonObject payload = res.getAsJsonObject("payload");
                    if (payload != null && payload.has("faqId")) {
                        String faqIdStr = payload.get("faqId").getAsString();
                        try {
                            Long faqId = Long.parseLong(faqIdStr);
                            existingIds.add(faqId);
                        } catch (NumberFormatException e) {
                            log.warn("解析FAQ ID失败: {}", faqIdStr);
                        }
                    }
                }
                
                log.info("成功获取向量库已有FAQ数量: {}", existingIds.size());
            }
            
        } catch (Exception e) {
            log.warn("获取向量库已有 FAQ ID 失败，将重新添加所有 FAQ", e);
        }
        return existingIds;
    }

    public void updateFaq(Faq faq) {
        deleteFaq(faq.getId());
        addFaq(faq);
    }

    public void deleteFaq(Long faqId) {
        if (!isAvailable()) {
            log.warn("向量存储不可用，跳过删除 FAQ: id={}", faqId);
            return;
        }

        log.info("从向量存储删除 FAQ: id={}", faqId);
    }

    public List<FaqMatch> search(String question, int limit) {
        List<FaqMatch> results = new ArrayList<>();
        if (!isAvailable()) {
            log.warn("向量存储不可用，跳过搜索");
            return results;
        }

        if (!isEmbeddingModelAvailable()) {
            log.warn("嵌入模型不可用，跳过搜索");
            return results;
        }

        if (isTokenLimitExceeded() && !isCurrentModelLocal()) {
            log.warn("Token 使用量已达到上限，且当前使用 API 模型，跳过搜索");
            return results;
        }

        try {
            double threshold = properties.getVectorStore().getSimilarityThreshold();
            
            log.info("使用HTTP API进行向量搜索: question={}", question);
            List<EmbeddingMatch<TextSegment>> matches = searchViaHttp(question, limit, threshold);
            
            for (EmbeddingMatch<TextSegment> match : matches) {
                try {
                    TextSegment segment = match.embedded();
                    Metadata metadata = segment.metadata();
                    FaqMatch faqMatch = new FaqMatch();
                    faqMatch.setFaqId(getMetadataLong(metadata, "faqId"));
                    faqMatch.setQuestion(metadata.getString("question"));
                    faqMatch.setAnswer(metadata.getString("answer"));
                    faqMatch.setCategory(metadata.getString("category"));
                    faqMatch.setScore(match.score());
                    results.add(faqMatch);
                } catch (Exception e) {
                    log.warn("处理匹配结果失败，跳过该结果", e);
                }
            }

            log.info("HTTP向量搜索完成: question={}, resultsCount={}", question, results.size());
        } catch (Exception e) {
            log.error("HTTP向量搜索失败: question={}", question, e);
        }

        return results;
    }
    
    /**
     * 通过 HTTP API 搜索相似向量 - 参考 QdrantHttpClient 的实现方式
     */
    private List<EmbeddingMatch<TextSegment>> searchViaHttp(String query, int maxResults, double minScore) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            
            // 统计 token 使用量
            addTokenUsage(estimateTokens(query), !isCurrentModelLocal());
            
            float[] vector = queryEmbedding.vector();
            if (vector == null || vector.length == 0) {
                log.error("生成的查询向量为空，无法搜索");
                return Collections.emptyList();
            }
            log.debug("查询向量长度: {}", vector.length);
            
            JsonObject requestBody = new JsonObject();
            JsonArray vectorArray = new JsonArray();
            for (Float f : vector) {
                vectorArray.add(f);
            }
            requestBody.add("vector", vectorArray);
            requestBody.addProperty("limit", maxResults);
            requestBody.addProperty("with_payload", true);
            
            Request.Builder requestBuilder = new Request.Builder()
                    .url(qdrantUrl + "/collections/" + qdrantCollectionName + "/points/search")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")));
            
            if (qdrantApiKey != null && !qdrantApiKey.trim().isEmpty()) {
                requestBuilder.addHeader("api-key", qdrantApiKey);
            }
            
            Request request = requestBuilder.build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Qdrant 搜索失败，状态码: {}", response);
                    return Collections.emptyList();
                }
                
                String body = response.body().string();
                JsonObject json = gson.fromJson(body, JsonObject.class);
                JsonArray results = json.getAsJsonArray("result");
                
                List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
                for (int i = 0; i < results.size(); i++) {
                    JsonObject res = results.get(i).getAsJsonObject();
                    double score = res.get("score").getAsDouble();
                    
                    if (score < minScore) {
                        continue;
                    }
                    
                    JsonObject payload = res.getAsJsonObject("payload");
                    if (payload == null) {
                        continue;
                    }
                    
                    String faqIdStr = payload.has("faqId") ? payload.get("faqId").getAsString() : null;
                    String question = payload.has("question") ? payload.get("question").getAsString() : "";
                    String answer = payload.has("answer") ? payload.get("answer").getAsString() : "";
                    String category = payload.has("category") ? payload.get("category").getAsString() : null;
                    String keywords = payload.has("keywords") ? payload.get("keywords").getAsString() : null;
                    
                    Metadata metadata = new Metadata();
                    if (faqIdStr != null) {
                        metadata.put("faqId", faqIdStr);
                    }
                    metadata.put("question", question);
                    metadata.put("answer", answer);
                    if (category != null) {
                        metadata.put("category", category);
                    }
                    if (keywords != null) {
                        metadata.put("keywords", keywords);
                    }
                    
                    String content = buildFaqContentFromPayload(question, answer, category, keywords);
                    TextSegment segment = TextSegment.from(content, metadata);
                    String pointId = res.get("id").getAsString();
                    matches.add(new EmbeddingMatch<>(score, pointId, null, segment));
                }
                
                log.debug("HTTP 搜索成功，返回 {} 条结果", matches.size());
                return matches;
            }
        } catch (IOException e) {
            log.error("HTTP 搜索失败", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 从payload构建FAQ内容
     */
    private String buildFaqContentFromPayload(String question, String answer, String category, String keywords) {
        StringBuilder content = new StringBuilder();
        content.append("问题: ").append(question).append("\n");
        if (category != null && !category.isEmpty()) {
            content.append("分类: ").append(category).append("\n");
        }
        if (keywords != null && !keywords.isEmpty()) {
            content.append("关键词: ").append(keywords).append("\n");
        }
        content.append("回答: ").append(answer);
        return content.toString();
    }

    /**
     * 检查 Qdrant 中存储的向量维度
     */
    public void checkVectorDimensions() {
        if (!isAvailable()) {
            log.warn("向量存储不可用，跳过维度检查");
            return;
        }

        try {
            // 创建一个测试向量来检查维度
            Embedding testEmbedding = embeddingModel.embed("测试向量维度").content();
            if (!isValidEmbedding(testEmbedding)) {
                log.error("测试向量无效，无法检查维度");
                return;
            }
            
            float[] testVector = testEmbedding.vector();
            log.info("当前模型向量维度: {}", testVector.length);
            
            // 尝试获取 Qdrant 中的一些向量来检查维度
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(testEmbedding)
                    .maxResults(5)
                    .minScore(0.0)
                    .build();
            
            try {
                List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
                log.info("Qdrant 中存储的向量数量: {}", matches.size());
                
                for (int i = 0; i < Math.min(matches.size(), 3); i++) {
                    EmbeddingMatch<TextSegment> match = matches.get(i);
                    float[] storedVector = match.embedding().vector();
                    log.info("Qdrant 中第 {} 个向量的维度: {}", i + 1, storedVector.length);
                }
            } catch (Exception e) {
                log.error("检查 Qdrant 向量维度失败: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("检查向量维度失败: {}", e.getMessage());
        }
    }

    /**
     * 清理并重新初始化向量库
     */
    public void reinitializeVectorStore() {
        log.info("开始重新初始化向量库...");
        
        // 这里可以添加清理 Qdrant 集合的逻辑
        // 由于 Qdrant Java SDK 没有直接删除集合的方法，建议通过 API 或 Dashboard 清理
        
        log.info("请通过以下方式清理 Qdrant 集合:");
        log.info("1. 访问 Qdrant Dashboard: http://106.54.15.105:6333/dashboard");
        log.info("2. 删除 ticketing-faq 集合");
        log.info("3. 重启应用，系统会自动重新创建集合");
        
        // 或者通过 curl 命令清理
        log.info("或使用 curl 命令清理:");
        log.info("curl -X DELETE 'http://106.54.15.105:6334/collections/ticketing-faq'");
    }
    
    /**
     * 执行向量库健康检查并尝试自动修复
     */
    public boolean healthCheckAndRepair() {
        log.info("开始向量库健康检查...");
        
        if (!isAvailable()) {
            log.error("向量存储不可用，健康检查失败");
            return false;
        }
        
        try {
            // 测试基本搜索功能
            Embedding testEmbedding = embeddingModel.embed("健康检查测试").content();
            
            if (!isValidEmbedding(testEmbedding)) {
                log.error("测试向量无效，健康检查失败");
                return false;
            }
            
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(testEmbedding)
                    .maxResults(1)
                    .minScore(0.0)
                    .build();
            
            // 尝试搜索
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
            log.info("向量库健康检查通过，当前存储向量数（样本）: {}", matches.size());
            
            return true;
            
        } catch (Exception e) {
            log.error("向量库健康检查失败，需要修复", e);
            log.warn("建议：执行 reinitializeVectorStore() 重新初始化向量库");
            return false;
        }
    }

    private String buildFaqContent(Faq faq) {
        StringBuilder content = new StringBuilder();
        content.append("问题: ").append(faq.getQuestion()).append("\n");
        if (faq.getCategory() != null) {
            content.append("分类: ").append(faq.getCategory()).append("\n");
        }
        if (faq.getKeywords() != null) {
            content.append("关键词: ").append(faq.getKeywords()).append("\n");
        }
        content.append("回答: ").append(faq.getAnswer());
        return content.toString();
    }

    private Metadata buildMetadata(Faq faq) {
        Metadata metadata = new Metadata();
        metadata.put("faqId", faq.getId().toString());
        metadata.put("question", faq.getQuestion());
        metadata.put("answer", faq.getAnswer());
        if (faq.getCategory() != null) {
            metadata.put("category", faq.getCategory());
        }
        if (faq.getKeywords() != null) {
            metadata.put("keywords", faq.getKeywords());
        }
        return metadata;
    }

    private Long getMetadataLong(Metadata metadata, String key) {
        String value = metadata.getString(key);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private boolean isValidEmbedding(Embedding embedding) {
        if (embedding == null) {
            return false;
        }
        float[] vector = embedding.vector();
        return vector != null && vector.length > 0;
    }

    private boolean isCurrentModelLocal() {
        return currentModelType == ModelType.OLLAMA;
    }

    private boolean isTokenLimitExceeded() {
        if (tokenUsageManager != null && tokenUsageManager.isTokenLimitEnabled()) {
            return tokenUsageManager.isTokenLimitExceeded();
        }
        return false;
    }

    private void addTokenUsage(long tokens, boolean isApi) {
        if (tokenUsageManager != null) {
            tokenUsageManager.addUsage(tokens, !isApi);
        }
    }

    private long estimateTokens(String text) {
        if (tokenUsageManager != null) {
            return tokenUsageManager.estimateTokens(text);
        }
        return Math.max(1, text.length() / 2);
    }

    @lombok.Data
    public static class FaqMatch {
        private Long faqId;
        private String question;
        private String answer;
        private String category;
        private double score;
    }
}
