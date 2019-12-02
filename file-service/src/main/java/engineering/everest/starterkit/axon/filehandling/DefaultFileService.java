package engineering.everest.starterkit.axon.filehandling;

import engineering.everest.starterkit.axon.filehandling.persistence.PersistableFileMapping;
import engineering.everest.starterkit.axon.filehandling.persistence.FileMappingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static engineering.everest.starterkit.axon.filehandling.FileStoreType.PERMANENT;
import static java.nio.file.Files.createTempFile;

@Component
class DefaultFileService implements FileService {

    private final FileMappingRepository fileMappingRepository;
    @Qualifier("permanentFileStore")
    private final DeduplicatingFileStore permanentFileStore;
    @Qualifier("artifactFileStore")
    private final DeduplicatingFileStore artifactFileStore;

    public DefaultFileService(FileMappingRepository fileMappingRepository,
                              DeduplicatingFileStore permanentFileStore,
                              DeduplicatingFileStore artifactFileStore) {
        this.fileMappingRepository = fileMappingRepository;
        this.permanentFileStore = permanentFileStore;
        this.artifactFileStore = artifactFileStore;
    }

    @Override
    public File createTemporaryFile() throws IOException {
        File tempFile = createTempFile("temp", "upload").toFile();
        tempFile.deleteOnExit();
        return tempFile;
    }

    @Override
    public UUID transferToPermanentStore(String originalFilename, InputStream inputStream) throws IOException {
        return permanentFileStore.store(originalFilename, inputStream).getPersistedFileIdentifier().getFileId();
    }

    @Override
    public UUID transferToArtifactStore(String filename, InputStream inputStream) throws IOException {
        return artifactFileStore.store(filename, inputStream).getPersistedFileIdentifier().getFileId();
    }

    @Override
    public UUID transferToArtifactStore(InputStream inputStream) throws IOException {
        return transferToArtifactStore("", inputStream);
    }

    @Override
    public InputStream stream(UUID fileId) throws IOException {
        PersistableFileMapping persistableFileMapping = fileMappingRepository.findById(fileId).orElseThrow();
        var fileStore = persistableFileMapping.getFileStoreType().equals(PERMANENT) ? permanentFileStore : artifactFileStore;
        return fileStore.stream(persistableFileMapping.getPersistedFileIdentifier());
    }
}