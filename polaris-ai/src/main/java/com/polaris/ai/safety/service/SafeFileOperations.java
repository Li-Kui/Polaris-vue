package com.polaris.ai.safety.service;

import java.nio.file.Path;

public interface SafeFileOperations {
    void move(Path source, Path destination);
    void delete(Path target);
}
