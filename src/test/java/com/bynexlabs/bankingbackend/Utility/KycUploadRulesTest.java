package com.bynexlabs.bankingbackend.Utility;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Owner decision D9: KYC documents are JPEG or PNG, at most 5 MB, judged by content. */
class KycUploadRulesTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    @Test
    void acceptsJpeg() {
        assertThat(KycUploadRules.validate(file("id.jpg", "image/jpeg", JPEG))).isEqualTo(KycUploadRules.ImageType.JPEG);
    }

    @Test
    void acceptsPng() {
        assertThat(KycUploadRules.validate(file("id.png", "image/png", PNG))).isEqualTo(KycUploadRules.ImageType.PNG);
    }

    @Test
    void contentDecidesNotTheNameOrHeader() {
        // A real PNG sent with a misleading name and Content-Type is still a PNG.
        assertThat(KycUploadRules.validate(file("id.pdf", "application/octet-stream", PNG)))
                .isEqualTo(KycUploadRules.ImageType.PNG);
    }

    @Test
    void rejectsOtherFormatsEvenWhenLabelledAsJpeg() {
        assertThatThrownBy(() -> KycUploadRules.validate(file("id.jpg", "image/jpeg", "%PDF-1.7 ...".getBytes())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The KYC document must be a JPEG or PNG image");
        assertThatThrownBy(() -> KycUploadRules.validate(file("id.gif", "image/gif", "GIF89a....".getBytes())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> KycUploadRules.validate(file("id.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The KYC document file is empty");
    }

    @Test
    void exactlyFiveMegabytesIsAllowedOneByteMoreIsNot() {
        byte[] fiveMb = new byte[(int) KycUploadRules.MAX_SIZE_BYTES];
        System.arraycopy(JPEG, 0, fiveMb, 0, JPEG.length);
        assertThat(KycUploadRules.validate(file("id.jpg", "image/jpeg", fiveMb))).isEqualTo(KycUploadRules.ImageType.JPEG);

        byte[] tooBig = new byte[(int) KycUploadRules.MAX_SIZE_BYTES + 1];
        System.arraycopy(JPEG, 0, tooBig, 0, JPEG.length);
        assertThatThrownBy(() -> KycUploadRules.validate(file("id.jpg", "image/jpeg", tooBig)))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("The KYC document must be 5 MB or smaller");
    }
}
