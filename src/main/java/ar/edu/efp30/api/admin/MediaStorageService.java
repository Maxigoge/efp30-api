package ar.edu.efp30.api.admin;

import ar.edu.efp30.api.publicapi.PublicApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class MediaStorageService {
    private final Path root;

    public MediaStorageService(@Value("${efp30.media.root:./data/media}") String root) {
        this.root=Path.of(root).toAbsolutePath().normalize();
    }

    public String store(UUID id,String extension,byte[] bytes) throws IOException {
        Files.createDirectories(root);
        Path realRoot=root.toRealPath();
        String key=id+"."+extension;
        Path target=realRoot.resolve(key).normalize();
        if(!target.getParent().equals(realRoot)) throw new IOException("Invalid generated object key");
        Files.write(target,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        return key;
    }

    public Path resolve(String objectKey,long expectedSize) {
        if(objectKey==null||!objectKey.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp|pdf)")) throw PublicApiException.notFound();
        try {
            Path realRoot=root.toRealPath();
            Path candidate=realRoot.resolve(objectKey).normalize();
            if(!candidate.getParent().equals(realRoot)) throw PublicApiException.notFound();
            Path realFile=candidate.toRealPath();
            if(!realFile.startsWith(realRoot)||!Files.isRegularFile(realFile)||Files.size(realFile)!=expectedSize)
                throw PublicApiException.notFound();
            return realFile;
        } catch(IOException exception) { throw PublicApiException.notFound(); }
    }

    public void delete(String objectKey) {
        if(objectKey==null||!objectKey.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp|pdf)")) return;
        try { Files.deleteIfExists(root.resolve(objectKey).normalize()); } catch(IOException ignored) { }
    }
}
