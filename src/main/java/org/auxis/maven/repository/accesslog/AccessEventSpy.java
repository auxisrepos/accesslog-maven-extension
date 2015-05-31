package org.auxis.maven.repository.accesslog;

import org.apache.maven.artifact.repository.ArtifactRepository;
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
 * A dependency tracing spy.
 * Dumps all relevant repository interaction to a file so we can provision
 * repositories with dependencies.
 */
@Named
public class AccessEventSpy extends AbstractEventSpy
{
    @Inject
    private Logger logger;

    private RepositorySystemSession repoSession;

    @Inject RepositorySystem repoSystem;

    private List<RemoteRepository> remoteRepos;

    private MavenProject m_reactorProject;

    private List<RepositoryEvent> m_eventLog = new ArrayList<>();

    private String deploymentTarget = "singledeploy";

    private String[] allowedRepos;

    private boolean m_sync = true;

    @Override public void close() throws Exception
    {
        if ( m_reactorProject != null )
        {
            File file = writeDependencyList();
            if ( deploymentTarget != null )
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

    @Override public void onEvent( Object event ) throws Exception
    {
        super.onEvent( event );
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
            m_eventLog.add( repositoryEvent );
            if ( repoSession == null )
            {
                repoSession = repositoryEvent.getSession();
            }
        }
        else if ( event instanceof DefaultMavenExecutionResult )
        {
            // Read from result as those things will be available at fulliest at the very end of the reactor run.
            DefaultMavenExecutionResult execResult = ( DefaultMavenExecutionResult ) event;
            logger.info( "REGISTER getRemoteArtifactRepositories  : " + execResult.getProject().getRemoteArtifactRepositories().size() );
            remoteRepos = AetherUtils.toRepos( execResult.getProject().getRemoteArtifactRepositories() );
            if ( execResult.getDependencyResolutionResult().getUnresolvedDependencies().size() > 0 )
            {
                m_sync = false;
            }
        }
        else if ( event instanceof MavenExecutionRequest )
        {
            MavenExecutionRequest execRequest = ( MavenExecutionRequest ) event;
        }
        else if ( event instanceof SettingsBuildingRequest )
        {
            SettingsBuildingRequest settingsBuildingRequest = ( SettingsBuildingRequest ) event;
        }
        else if ( event instanceof SettingsBuildingResult )
        {
            SettingsBuildingResult settingsBuildingRequest = ( SettingsBuildingResult ) event;
        }
        else
        {
            logger.info( "Unrecognized event: " + event.getClass().getName() );
        }
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

    private RemoteRepository selectTargetRepo()
    {
        logger.info( "Repositories configured: " + this.remoteRepos.size() );
        for ( RemoteRepository repo : this.remoteRepos )
        {
            getLog().info( "Using repo: " + repo );
            if ( repo.getId().equals( this.deploymentTarget ) )
            {
                return repo;
            }
        }
        throw new IllegalArgumentException( "Target Repository ID " + deploymentTarget + " is unkown. Is it configured?" );
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

        if ( allowedRepos == null || allowedRepos.length == 0 )
        {
            result = this.remoteRepos;
        }
        else
        {
            Set<String> allowed = new HashSet<>( Arrays.asList( allowedRepos ) );
            for ( RemoteRepository repo : this.remoteRepos )
            {
                if ( allowed.contains( repo.getId() ) )
                {
                    result.add( repo );
                }
            }
        }
        for ( RemoteRepository repo : result )
        {
            getLog().info( "Allowed repo: " + repo );
        }
        getLog().info( "Repositories allowed for resolving: " + result );
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
