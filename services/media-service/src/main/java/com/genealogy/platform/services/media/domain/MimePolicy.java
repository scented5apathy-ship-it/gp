package com.genealogy.platform.services.media.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * MIME policy executor. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.mimeAllowList + mimeDenyList + mimeSandboxRequired +
 * mimeDeepScanRequired + maxSniffBytes + dnaBucketPrefixes +
 * dnaSensitiveMimeHints` (E7.1) + `requirements.md` R9.2 +
 * `design.md` §8.2 + §11.
 *
 * <p>The executor is a pure function: it takes the declared
 * MIME type, the observed sniff bytes, the
 * {@link MediaCategory}, and the upload intent, and returns
 * a {@link MimeVerdict} + a closed-set reason code.
 */
public final class MimePolicy {

    private static final Set<String> EMPTY_MIME = Set.of();

    private final Map<MediaCategory, Set<String>> allowList;
    private final Set<String> denyList;
    private final Set<String> sandboxRequired;
    private final Set<String> deepScanRequired;
    private final Set<String> dnaSensitiveMimeHints;
    private final Set<String> dnaBucketPrefixes;
    private final long maxSniffBytes;

    public MimePolicy(
            Map<MediaCategory, Set<String>> allowList,
            Set<String> denyList,
            Set<String> sandboxRequired,
            Set<String> deepScanRequired,
            Set<String> dnaSensitiveMimeHints,
            Set<String> dnaBucketPrefixes,
            long maxSniffBytes) {
        Objects.requireNonNull(allowList, "allowList");
        Objects.requireNonNull(denyList, "denyList");
        Objects.requireNonNull(sandboxRequired, "sandboxRequired");
        Objects.requireNonNull(deepScanRequired, "deepScanRequired");
        Objects.requireNonNull(dnaSensitiveMimeHints, "dnaSensitiveMimeHints");
        Objects.requireNonNull(dnaBucketPrefixes, "dnaBucketPrefixes");
        Map<MediaCategory, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<MediaCategory, Set<String>> e : allowList.entrySet()) {
            if (e.getKey() == null) {
                throw new IllegalArgumentException("allowList key must not be null");
            }
            Set<String> mimes = e.getValue() == null ? EMPTY_MIME : Set.copyOf(e.getValue());
            copy.put(e.getKey(), Collections.unmodifiableSet(mimes));
        }
        this.allowList = Collections.unmodifiableMap(copy);
        this.denyList = Set.copyOf(denyList);
        this.sandboxRequired = Set.copyOf(sandboxRequired);
        this.deepScanRequired = Set.copyOf(deepScanRequired);
        this.dnaSensitiveMimeHints = Set.copyOf(dnaSensitiveMimeHints);
        this.dnaBucketPrefixes = Set.copyOf(dnaBucketPrefixes);
        if (maxSniffBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxSniffBytes must be positive, got " + maxSniffBytes);
        }
        this.maxSniffBytes = maxSniffBytes;
    }

    public static MimePolicy fromContractDefaults() {
        Map<MediaCategory, Set<String>> allowList = new LinkedHashMap<>();
        allowList.put(MediaCategory.IMAGE, Set.of(
                "image/jpeg", "image/png", "image/webp", "image/avif",
                "image/heic", "image/tiff"));
        allowList.put(MediaCategory.AUDIO, Set.of(
                "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav",
                "audio/flac", "audio/aac"));
        allowList.put(MediaCategory.VIDEO, Set.of(
                "video/mp4", "video/quicktime", "video/webm", "video/x-matroska"));
        allowList.put(MediaCategory.DOCUMENT, Set.of(
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain", "text/markdown"));
        allowList.put(MediaCategory.PDF, Set.of("application/pdf"));
        allowList.put(MediaCategory.SVG, Set.of("image/svg+xml"));
        allowList.put(MediaCategory.ARCHIVE, Set.of(
                "application/zip", "application/x-tar", "application/gzip"));
        allowList.put(MediaCategory.DNA_FASTQ, Set.of());
        return new MimePolicy(
                allowList,
                Set.of(
                        "application/x-msdownload",
                        "application/x-msdos-program",
                        "application/x-executable",
                        "application/x-sharedlib",
                        "application/x-sh",
                        "application/x-bat",
                        "application/x-msi",
                        "application/x-apple-diskimage",
                        "application/x-dosexec"),
                Set.of(
                        "image/svg+xml",
                        "application/zip",
                        "application/x-tar",
                        "application/gzip",
                        "application/pdf"),
                Set.of(
                        "application/pdf",
                        "application/zip",
                        "application/x-tar",
                        "application/gzip",
                        "image/svg+xml"),
                Set.of(
                        "application/x-fasta",
                        "application/x-fastq",
                        "application/octet-stream+dna"),
                Set.of("dna/raw", "dna/match", "dna/consent"),
                26214432L);
    }

    public MimeVerdict evaluate(
            String declaredMimeType,
            String sniffedMimeType,
            MediaCategory category,
            UploadSessionIntent intent,
            long sniffBytes) {
        Objects.requireNonNull(declaredMimeType, "declaredMimeType");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(intent, "intent");
        if (sniffBytes < 0) {
            throw new IllegalArgumentException("sniffBytes must not be negative");
        }
        if (sniffBytes > maxSniffBytes) {
            return MimeVerdict.DENY;
        }
        String declared = declaredMimeType.trim().toLowerCase();
        if (denyList.contains(declared)) {
            return MimeVerdict.DENY;
        }
        if (dnaSensitiveMimeHints.contains(declared)
                || (sniffedMimeType != null
                        && dnaSensitiveMimeHints.contains(sniffedMimeType.trim().toLowerCase()))) {
            return MimeVerdict.DENY;
        }
        Set<String> allowed = allowList.getOrDefault(category, EMPTY_MIME);
        if (!allowed.contains(declared)) {
            return MimeVerdict.DENY;
        }
        if (sniffedMimeType != null
                && !sniffedMimeType.trim().toLowerCase().equals(declared)) {
            return MimeVerdict.DENY;
        }
        if (deepScanRequired.contains(declared)) {
            return MimeVerdict.DEEP_SCAN_REQUIRED;
        }
        if (sandboxRequired.contains(declared)) {
            return MimeVerdict.SANDBOX_REQUIRED;
        }
        return MimeVerdict.ALLOW;
    }

    public boolean routesToDnaBucket(String objectKey) {
        Objects.requireNonNull(objectKey, "objectKey");
        for (String prefix : dnaBucketPrefixes) {
            if (objectKey.startsWith(prefix + "/") || objectKey.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    public Map<MediaCategory, Set<String>> allowList() {
        return allowList;
    }

    public Set<String> denyList() {
        return denyList;
    }

    public Set<String> sandboxRequired() {
        return sandboxRequired;
    }

    public Set<String> deepScanRequired() {
        return deepScanRequired;
    }

    public long maxSniffBytes() {
        return maxSniffBytes;
    }
}
