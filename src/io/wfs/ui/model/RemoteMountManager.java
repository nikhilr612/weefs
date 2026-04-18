package io.wfs.ui.model;

import io.wfs.core.nfs.NfsConnectionConfig;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Manages the NFS/remote mount state independently of the archive state.
 * Extracted from ArchiveModel to honour Single Responsibility Principle.
 */
public final class RemoteMountManager {

    public static final String PROP_NFS_CONFIG = "nfsConfig";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private NfsConnectionConfig nfsConfig;

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    public void setNfsConfig(NfsConnectionConfig config) {
        NfsConnectionConfig old = this.nfsConfig;
        this.nfsConfig = config;
        pcs.firePropertyChange(PROP_NFS_CONFIG, old, config);
    }

    public NfsConnectionConfig getNfsConfig() {
        return nfsConfig;
    }

    public boolean isNfsMounted() {
        return nfsConfig != null;
    }
}
