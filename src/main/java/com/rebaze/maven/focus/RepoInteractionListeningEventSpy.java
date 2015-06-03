/*******************************************************************************
 * Copyright (c) 2015 Rebaze GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache Software License v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/
 * <p/>
 * Contributors:
 * Rebaze
 *******************************************************************************/
package com.rebaze.maven.focus;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.util.*;

/**
 *
 * @author Toni Menzel (toni.menzel@rebaze.com)
 *
 * A dependency tracing spy.
 * Dumps all relevant repository interaction to a file so we can provision
 * repositories with dependencies.
 */
@Named
public class RepoInteractionListeningEventSpy extends AbstractEventSpy
{
    /**
     * When set, this plugin will upload dependencies in target/recording.txt to the repo configured here.
     */
    public static String PROPERTY_FOCUS_REPO = "focus.repo";

    /**
     * When enabled, this spy will trace dependencies requested during builds in file target/recording.txt.
     *
     */
    public static String PROPERTY_ENABLED = "focus.enabled";

    @Inject
    private Logger logger;

    private RepositorySystemSession repoSession;

    @Inject RepositorySystem repoSystem;

    private List<RemoteRepository> remoteRepos;

    private MavenProject m_reactorProject;

    private List<RepositoryEvent> m_eventLog = new ArrayList<>();

    private boolean m_sync = true;

    private MavenExecutionRequest execRequest;

    private String uploadRepo;

    private boolean enabled = false;

    @Override public void onEvent( Object event ) throws Exception
    {
        super.onEvent( event );

        //traceEvents( event );
        try
        {
            if ( event instanceof ExecutionEvent )
            {
                org.apache.maven.execution.ExecutionEvent exec = ( ExecutionEvent ) event;
                if ( exec.getProject() != null && exec.getProject().isExecutionRoot() )
                {
                    if ( m_reactorProject == null )
                    {
                        m_eventLog = new ArrayList<>();
                        m_reactorProject = exec.getProject();

                    }
                }
            }
            else if ( event instanceof org.eclipse.aether.RepositoryEvent )
            {
                RepositoryEvent repositoryEvent = ( RepositoryEvent ) event;
                if ( enabled )
                {
                    m_eventLog.add( repositoryEvent );
                    if ( repoSession == null )
                    {
                        repoSession = repositoryEvent.getSession();
                    }
                }
            }
            else if ( event instanceof DefaultMavenExecutionResult )
            {
                DefaultMavenExecutionResult execResult = ( DefaultMavenExecutionResult ) event;
                remoteRepos = AetherUtils.toRepos( execResult.getProject().getRemoteArtifactRepositories() );
                if ( execResult.getDependencyResolutionResult() != null && execResult.getDependencyResolutionResult().getUnresolvedDependencies().size() > 0 )
                {
                    m_sync = false;
                }

                // MAYBE TRIGGER DEPLOY HERE??
            }
            else if ( event instanceof MavenExecutionRequest )
            {
                if ( execRequest == null )
                {
                    execRequest = ( MavenExecutionRequest ) event;
                    uploadRepo = execRequest.getUserProperties().getProperty( PROPERTY_FOCUS_REPO );
                    enabled = FocusConfigurationProcessor.isEnabled( execRequest.getUserProperties().getProperty( PROPERTY_ENABLED ) );
                }

            }
            else if ( event instanceof SettingsBuildingRequest )
            {
                SettingsBuildingRequest settingsBuildingRequest = ( SettingsBuildingRequest ) event;

            }
            else if ( event instanceof SettingsBuildingResult )
            {
                //logger.info( "Overwrite repos for request: " + execRequest );
            }
            else
            {
                //logger.info( "Unrecognized event: " + event.getClass().getName() );
            }
        }
        catch ( Exception e )
        {
            logger.error( "Problem!", e );
        }
    }

    private void traceEvents( Object event )
    {
        if ( !( event instanceof RepositoryEvent ) )
            logger.info( "Event: " + event.getClass().getName() );
    }

    @Override public void close() throws Exception
    {
        logger.info( "Finishing focus extension.. " + enabled );
        if ( enabled && m_reactorProject != null )
        {
            File file = writeDependencyList();
            if ( uploadRepo != null )
            {
                if ( m_sync )
                {
                    deployBill( file );
                }
                else
                {
                    logger.info( "Won't deploy due to unresolved dependencies." );
                }
                // deploy.
            }
        }
        super.close();
    }

    private void deployBill( File input )
    {
        RemoteRepository targetRepository = selectTargetRepo();
        List<RemoteRepository> allowedRepositories = calculateAllowedRepositories();
        List<String> sortedArtifacts = readInputBill( input );
        try
        {
            List<Artifact> listOfArtifacts = parseAndResolveArtifacts( sortedArtifacts, allowedRepositories );
            DeployRequest deployRequest = new DeployRequest();
            deployRequest.setRepository( targetRepository );

            for ( Artifact artifact : listOfArtifacts )
            {
                assert ( artifact.getFile() != null );
                deployRequest.addArtifact( artifact );
            }
            getLog().info( "Deployment of " + deployRequest.getArtifacts().size() + " artifacts .." );

            DeployResult result = repoSystem.deploy( repoSession, deployRequest );
            getLog().info( "Deployment Result: " + result.getArtifacts().size() );
        }
        catch ( DeploymentException e )
        {
            getLog().error( "Problem deploying set..!", e );
        }
        catch ( ArtifactResolutionException e )
        {
            getLog().error( "Problem resolving artifact(s)..!", e );
        }
    }

    private File writeDependencyList()
    {
        File f = new File( new File( m_reactorProject.getBuild().getOutputDirectory() ).getParent(), "recording.txt" );
        if ( !f.getParentFile().exists() )
        {
            f.getParentFile().mkdirs();
        }
        int finalSize = -1;
        try ( BufferedWriter writer = new BufferedWriter( new FileWriter( f, true ) ) )
        {
            Set<String> content = new HashSet<>();
            for ( RepositoryEvent repositoryEvent : m_eventLog )
            {
                if ( repositoryEvent.getArtifact() != null )
                {
                    content.add( repositoryEvent.getArtifact().toString() );
                }
            }
            List<String> sorted = new ArrayList<>( content );
            Collections.sort( sorted );
            finalSize = sorted.size();
            for ( String s : sorted )
            {
                writer.append( s );
                writer.newLine();
            }
        }
        catch ( IOException e )
        {
            logger.error( "Problem writing file.", e );
        }
        logger.info( "Halo written (count=" + finalSize + "): " + f.getAbsolutePath() );
        return f;
    }

    private RemoteRepository selectTargetRepo()
    {
        logger.info( "Repositories configured: " + this.remoteRepos.size() );
        for ( RemoteRepository repo : this.remoteRepos )
        {
            getLog().info( "Using repo: " + repo );
            if ( repo.getId().equals( uploadRepo ) )
            {
                return repo;
            }
        }
        throw new IllegalArgumentException( "Target Repository ID " + uploadRepo + " is unkown. Is it configured?" );
    }

    private List<String> readInputBill( File input )
    {
        Set<String> artifacts = new HashSet<>();
        try ( BufferedReader reader = new BufferedReader( new FileReader( input ) ) )
        {
            String line = null;
            getLog().info( "Preparing deployment request.. " );
            while ( ( line = reader.readLine() ) != null )
            {
                artifacts.add( line );
            }
        }
        catch ( IOException e )
        {
            getLog().error( "Cannot parse bill: " + input.getAbsolutePath(), e );
            return null;
        }

        List<String> sortedArtifacts = new ArrayList<>( artifacts );
        sort( artifacts );
        return sortedArtifacts;
    }

    private List<Artifact> parseAndResolveArtifacts( Collection<String> artifacts, List<RemoteRepository> allowedRepositories ) throws ArtifactResolutionException
    {
        List<Artifact> artifactList = new ArrayList<>();
        for ( String a : artifacts )
        {
            Artifact artifact = new DefaultArtifact( a );
            artifactList.add( artifact );
        }
        return resolve( artifactList, allowedRepositories );
    }

    private List<RemoteRepository> calculateAllowedRepositories()
    {
        List<RemoteRepository> result = new ArrayList<>();
        result = this.remoteRepos;
        return result;
    }

    private Logger getLog()
    {
        return logger;
    }

    private List<Artifact> resolve( Collection<Artifact> artifacts, List<RemoteRepository> allowedRepositories ) throws ArtifactResolutionException
    {
        Collection<ArtifactRequest> artifactRequests = new ArrayList<>();
        for ( Artifact a : artifacts )
        {

            ArtifactRequest request = new ArtifactRequest( a, allowedRepositories, null );
            artifactRequests.add( request );
        }
        List<Artifact> result = new ArrayList<>( artifacts.size() );
        List<ArtifactResult> reply = repoSystem.resolveArtifacts( repoSession, artifactRequests );
        for ( ArtifactResult res : reply )
        {
            if ( !res.isMissing() )
            {
                result.add( res.getArtifact() );
            }
            else
            {
                getLog().warn( "Artifact " + res.getArtifact() + " is still missing." );
            }
        }
        return result;
    }

    private List<String> sort( Set<String> artifacts )
    {
        List<String> sortedArtifacts = new ArrayList<>( artifacts );
        Collections.sort( sortedArtifacts );
        return sortedArtifacts;
    }
}
