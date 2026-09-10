package com.wilderness.backend.service;

import com.wilderness.backend.domain.GeneratedImage;
import com.wilderness.backend.repository.GeneratedImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * 文生图产出图片的本地持久化：下载 DashScope 临时链接的字节流，转存到挂载卷目录，
 * 避免 24 小时后 URL 过期导致历史/收藏里的图变死链。
 *
 * download()/copyLocal() 失败均抛异常，由调用方（CelestialAgentService/FavoriteGenerationService）
 * 决定降级——不在这里吞异常，因为调用方需要知道"要不要退回外部临时链接"这个决策。
 */
@Service
public class GeneratedImageStorageService {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final GeneratedImageRepository repository;
    private final String baseDir;
    private final HttpClient http;

    public GeneratedImageStorageService(GeneratedImageRepository repository,
            @Value("${wilderness.storage.generated-images-dir}") String baseDir) {
        this.repository = repository;
        this.baseDir = baseDir;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** 图片资产的最小引用：给前端用的相对 URL（走鉴权接口）+ 行 id（供后续 copyLocal/delete 引用）。 */
    public record StoredImage(Long id, String relativeUrl) {
    }

    /** 下载远程图片并转存；失败（网络/写盘）抛异常，调用方决定降级为直接用 remoteUrl。 */
    public StoredImage download(String remoteUrl, Long ownerUserId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(remoteUrl))
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("下载生成图片失败，HTTP " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
        return store(response.body(), contentType, ownerUserId);
    }

    /** 把一张已本地持久化的图复制一份新文件/新记录（收藏时用，实现"历史与收藏各存一份"）。 */
    public StoredImage copyLocal(Long sourceImageId, Long ownerUserId) throws IOException {
        GeneratedImage source = repository.findById(sourceImageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "源图片不存在"));
        byte[] bytes = Files.readAllBytes(Path.of(source.getStoredPath()));
        return store(bytes, source.getContentType(), ownerUserId);
    }

    /** 删除物理文件 + 记录；文件已不存在也视为成功（幂等）。 */
    public void delete(Long imageId) {
        repository.findById(imageId).ifPresent(image -> {
            try {
                Files.deleteIfExists(Path.of(image.getStoredPath()));
            } catch (IOException ignored) {
                // 磁盘文件删不掉不阻断——记录还是要删，避免下次又尝试删同一个失败文件
            }
            repository.delete(image);
        });
    }

    private StoredImage store(byte[] bytes, String contentType, Long ownerUserId) throws IOException {
        Path dir = Path.of(baseDir);
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID() + extensionFor(contentType);
        Path target = dir.resolve(fileName);
        Files.write(target, bytes);
        GeneratedImage saved = repository.save(new GeneratedImage(ownerUserId, target.toString(), contentType));
        return new StoredImage(saved.getId(), "/api/generated-images/" + saved.getId());
    }

    private String extensionFor(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        if (contentType.contains("png")) {
            return ".png";
        }
        if (contentType.contains("webp")) {
            return ".webp";
        }
        return ".jpg";
    }
}
