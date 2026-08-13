package com.cgcpms.file.service;

import com.cgcpms.file.entity.SysFile;

/**
 * File-layer port implemented by the optional project file-center projection.
 */
public interface ProjectFileProjection {

    /** Runs synchronously in the caller's upload transaction. */
    void indexBusinessFile(SysFile file);

    /** Invalidates the projection in the caller's delete transaction. */
    void invalidateBusinessFile(SysFile file);

    /** Executes a persisted preview-conversion task. */
    void processConversionTask(long versionId);
}
