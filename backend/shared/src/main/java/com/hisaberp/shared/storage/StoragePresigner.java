package com.hisaberp.shared.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Generates time-limited presigned GET URLs for PRIVATE objects (subscription justificatifs,
 * expense attachments). The URL is signed against the PUBLIC endpoint (browser-reachable, e.g.
 * {@code https://files.<domain>}) so it works from the browser WITHOUT an anonymous bucket policy.
 *
 * <p>What is stored in the DB is the object key (e.g. {@code subscription-payments/<uuid>_f.pdf}).
 * For backward compatibility a legacy full URL ({@code <endpoint>/<bucket>/<key>}) is also accepted
 * and its key extracted. Presigning is an offline signing operation — no network call, so it works
 * even if MinIO is momentarily unreachable.
 */
@Component
@Slf4j
public class StoragePresigner {

    private final StorageProperties props;
    private final MinioClient signer;
    private final int ttlSeconds;

    public StoragePresigner(StorageProperties props,
                            @Value("${STORAGE_PRESIGN_TTL_SECONDS:3600}") int ttlSeconds) {
        this.props = props;
        // Sign against the public endpoint so the signature's host matches what the browser hits.
        this.signer = MinioClient.builder()
                .endpoint(props.resolvedPublicEndpoint())
                .credentials(props.accessKey(), props.secretKey())
                .build();
        // SigV4 caps presign validity at 7 days.
        this.ttlSeconds = Math.max(60, Math.min(ttlSeconds, 7 * 24 * 3600));
    }

    /** Presigned GET URL for a single stored reference (object key, or legacy full URL). */
    public String presign(String storedRef) {
        if (storedRef == null || storedRef.isBlank()) {
            return storedRef;
        }
        String key = objectKey(storedRef);
        try {
            return signer.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(props.bucket())
                    .object(key)
                    .expiry(ttlSeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.warn("Could not presign object '{}': {}", key, e.getMessage());
            return storedRef; // graceful fallback
        }
    }

    /** Presigns each reference in a list (null/empty list returned unchanged). */
    public List<String> presignAll(List<String> storedRefs) {
        if (storedRefs == null || storedRefs.isEmpty()) {
            return storedRefs;
        }
        return storedRefs.stream().map(this::presign).toList();
    }

    /** Extracts the object key, tolerating a legacy "&lt;...&gt;/&lt;bucket&gt;/&lt;key&gt;[?query]" URL. */
    private String objectKey(String storedRef) {
        String marker = "/" + props.bucket() + "/";
        int idx = storedRef.indexOf(marker);
        if (idx >= 0) {
            String tail = storedRef.substring(idx + marker.length());
            int q = tail.indexOf('?'); // drop any query from a previously-presigned URL
            return q >= 0 ? tail.substring(0, q) : tail;
        }
        return storedRef; // already a bare key
    }
}
