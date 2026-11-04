package edu.whu.developer.application;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

/** Run main after test compilation to check the upload boundary without a database. */
public class ProposalPdfCheck {
    private static MultipartFile file(String name, byte[] bytes, long size) {
        return (MultipartFile) Proxy.newProxyInstance(MultipartFile.class.getClassLoader(),
                new Class<?>[]{MultipartFile.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getOriginalFilename" -> name;
                    case "getSize" -> size;
                    case "isEmpty" -> size == 0;
                    case "getBytes" -> bytes;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static void rejects(MultipartFile file) {
        try {
            ApplicationService.readPdf(file);
            throw new AssertionError("Expected invalid upload to be rejected");
        } catch (ResponseStatusException error) {
            if (error.getStatusCode().value() != 400) throw new AssertionError(error);
        }
    }

    public static void main(String[] args) {
        byte[] pdf = "%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII);
        if (ApplicationService.readPdf(file("proposal.PDF", pdf, pdf.length)) != pdf) {
            throw new AssertionError("Valid PDF should be accepted");
        }
        rejects(null);
        rejects(file("empty.pdf", new byte[0], 0));
        rejects(file("wrong.txt", pdf, pdf.length));
        rejects(file("fake.pdf", "hello".getBytes(StandardCharsets.US_ASCII), 5));
        rejects(file("big.pdf", pdf, 5 * 1024 * 1024 + 1));
        rejects(file("a".repeat(256) + ".pdf", pdf, pdf.length));
        System.out.println("PDF validation: 7 checks passed");
    }
}
