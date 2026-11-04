package edu.whu.developer.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of("application/pdf");
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private final Path root;

    public FileStorageService(@Value("${app.upload.dir:./data/uploads}") String uploadDir) throws IOException {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public StoredFile storePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 PDF 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 不能超过 20MB");
        }
        String original = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename().trim();
        if (!original.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PDF 文件");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !ALLOWED.contains(contentType) && !contentType.equals("application/octet-stream")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PDF 文件");
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + ".pdf";
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件路径不合法");
        }
        try {
            file.transferTo(target);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "上传失败");
        }
        String safeName = original.replaceAll("[\\\\/]+", "_");
        if (safeName.length() > 200) {
            safeName = safeName.substring(safeName.length() - 200);
        }
        return new StoredFile("/api/v1/files/" + storedName, safeName);
    }

    public Path resolve(String storedName) {
        if (storedName == null || !storedName.matches("^[a-f0-9]{32}\\.pdf$")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        return target;
    }

    public record StoredFile(String url, String fileName) {
    }
}
