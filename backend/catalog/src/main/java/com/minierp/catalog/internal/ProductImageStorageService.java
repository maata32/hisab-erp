package com.minierp.catalog.internal;

import com.minierp.shared.error.BusinessException;
import com.minierp.shared.storage.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class ProductImageStorageService {

    private static final String FOLDER = "product-images";

    private final MinioClient minio;
    private final StorageProperties props;

    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = minio.bucketExists(
                    BucketExistsArgs.builder().bucket(props.bucket()).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
                log.info("Created MinIO bucket '{}'", props.bucket());
            }
        } catch (Exception e) {
            log.warn("Could not verify/create MinIO bucket '{}': {}", props.bucket(), e.getMessage());
        }
    }

    String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("error.attachment.empty", Map.of());
        }
        if (file.getSize() > props.maxFileSizeBytes()) {
            throw new BusinessException("error.attachment.too_large",
                    Map.of("max", props.maxFileSizeBytes() / 1_048_576 + "MB",
                            "actual", file.getSize() / 1_048_576 + "MB"));
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new BusinessException("error.attachment.invalid_type",
                    Map.of("allowed", "image/*", "actual", String.valueOf(ct)));
        }

        String objectName = FOLDER + "/" + UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
        try {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(ct)
                    .build());
        } catch (Exception e) {
            throw new BusinessException("error.attachment.upload_failed",
                    Map.of("cause", e.getMessage()));
        }
        return props.resolvedPublicEndpoint() + "/" + props.bucket() + "/" + objectName;
    }

    private static String sanitize(String name) {
        if (name == null) return "image";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
