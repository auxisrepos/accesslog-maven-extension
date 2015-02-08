package org.auxis.maven.repository.accesslog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Delegating repository manager that passes forward all requests.
 * It only records requested artifacts in a recording logfile.
 *
 */
public class AccessRecordingLocalRepositoryManager implements LocalRepositoryManager {

    private final File output;
    
    private final LocalRepositoryManager m_delegate;

    public AccessRecordingLocalRepositoryManager(LocalRepositoryManager delegate, LocalRepository repository, RepositorySystemSession session) {
        m_delegate = delegate;
        output = new File(repository.getBasedir(),"artifact-request.log");
    }
    
    @Override
    public LocalArtifactResult find(RepositorySystemSession repositorySystemSession, LocalArtifactRequest localArtifactRequest) {
    	// Record all requested artifacts.
    	write(localArtifactRequest.getArtifact());
        return m_delegate.find(repositorySystemSession,localArtifactRequest);
    }
    
    private void write(Artifact artifact) {
    	BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(output,true));
			writer.append(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":" + artifact.getVersion());
			writer.newLine();
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			try {
				writer.close();
			} catch (IOException e) {
			}
		}	
	}

    @Override
    public LocalRepository getRepository() {
        return m_delegate.getRepository();
    }

    @Override
    public String getPathForLocalArtifact(Artifact artifact) {
        return m_delegate.getPathForLocalArtifact(artifact);
    }

    @Override
    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository remoteRepository, String s) {
        return m_delegate.getPathForRemoteArtifact(artifact,remoteRepository,s);
    }

    @Override
    public String getPathForLocalMetadata(Metadata metadata) {
        return m_delegate.getPathForLocalMetadata(metadata);
    }

    @Override
    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository remoteRepository, String s) {
        return m_delegate.getPathForRemoteMetadata(metadata,remoteRepository,s);
    }
    
    @Override
    public void add(RepositorySystemSession repositorySystemSession, LocalArtifactRegistration localArtifactRegistration) {
    	m_delegate.add(repositorySystemSession,localArtifactRegistration);
    }
    
	@Override
    public LocalMetadataResult find(RepositorySystemSession repositorySystemSession, LocalMetadataRequest localMetadataRequest) {
        return m_delegate.find(repositorySystemSession,localMetadataRequest);
    }

    @Override
    public void add(RepositorySystemSession repositorySystemSession, LocalMetadataRegistration localMetadataRegistration) {
        m_delegate.add(repositorySystemSession,localMetadataRegistration);
    }
}
