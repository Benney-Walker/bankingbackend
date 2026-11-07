package com.bynexlabs.bankingbackend.Utility;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Upload rules for KYC identity documents (owner decision D9, 2026-11-07): JPEG or PNG only,
 * at most 5 MB. The type is decided from the file's first bytes, not from the name or the
 * Content-Type the client sends, since both are trivially faked.
 * spring.servlet.multipart.max-file-size enforces the same 5 MB limit before the request
 * reaches the controller.
 */
public final class KycUploadRules {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    public static final String TOO_LARGE_MESSAGE = "The KYC document must be 5 MB or smaller";

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public enum ImageType {
        JPEG("image/jpeg", ".jpg"),
        PNG("image/png", ".png");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() {
            return contentType;
        }

        public String extension() {
            return extension;
        }
    }

    private KycUploadRules() {
    }

    /** @return the detected image type; throws 400/413 if the file breaks the rules. */
    public static ImageType validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("The KYC document file is empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new PayloadTooLargeException(TOO_LARGE_MESSAGE);
        }

        byte[] header = readHeader(file);
        if (startsWith(header, PNG_SIGNATURE)) {
            return ImageType.PNG;
        }
        if (startsWith(header, JPEG_SIGNATURE)) {
            return ImageType.JPEG;
        }
        throw new BadRequestException("The KYC document must be a JPEG or PNG image");
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(PNG_SIGNATURE.length);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded KYC document", e);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}
