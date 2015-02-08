package org.auxis.maven.repository.accesslog;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;

/**
 * Connects access to the local repository with auxis.
 */
@Named("auxis-recorder")
public class AccessRecordingLocalRepositoryManagerFactory implements LocalRepositoryManagerFactory {

    @Inject
    private List<LocalRepositoryManagerFactory> localRepositoryManagerFactories;

    public LocalRepositoryManager newInstance(RepositorySystemSession session, LocalRepository repository) throws NoLocalRepositoryManagerException {
    	return new AccessRecordingLocalRepositoryManager(evictDelegateManager().newInstance(session, repository), repository, session);
    }

    private LocalRepositoryManagerFactory evictDelegateManager() {
        LocalRepositoryManagerFactory secondLeader = null;
        for (LocalRepositoryManagerFactory locals : localRepositoryManagerFactories) {
            if (locals.getClass().equals(AccessRecordingLocalRepositoryManagerFactory.class)) continue;
            if (secondLeader == null) {
                secondLeader = locals;
            } else {
                if (secondLeader.getPriority() < locals.getPriority()) {
                    secondLeader = locals;
                }
            }
        }
        return secondLeader;
    }

    public float getPriority() {
        return 100;
    }

}

