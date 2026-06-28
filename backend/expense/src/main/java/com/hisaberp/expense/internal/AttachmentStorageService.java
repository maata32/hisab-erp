package com.hisaberp.expense.internal;

import com.hisaberp.shared.error.BusinessException;
import com.hisaberp.shared.storage.StorageProperties;
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
class AttachmentStorageService {

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

    /** Uploads a file and returns its object KEY (presigned on read). */
    String upload(String folder, MultipartFile file) {
        if (file.getSize() > props.maxFileSizeBytes()) {
            throw new BusinessException("error.attachment.too_large",
                    Map.of("max", props.maxFileSizeBytes() / 1_048_576 + "MB",
                            "actual", file.getSize() / 1_048_576 + "MB"));
        }

        String objectName = folder + "/" + UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
        try {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new BusinessException("error.attachment.upload_failed", Map.of("cause", e.getMessage()));
        }

        // Store the object key; private bucket → URL is presigned at read time (StoragePresigner).
        return objectName;
    }

    private static String sanitize(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
