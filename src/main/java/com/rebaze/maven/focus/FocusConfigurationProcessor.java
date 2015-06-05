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

import org.apache.maven.MavenExecutionException;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.settings.Mirror;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A configuration post-processor restricting all external repositories to a single (focus-) repository.
 *
 * @author Toni Menzel (toni.menzel@rebaze.com)
 */
@Component( role = ConfigurationProcessor.class, hint = FocusConfigurationProcessor.HINT )
public class FocusConfigurationProcessor
    implements ConfigurationProcessor
{
    /**
     * When set, this plugin will upload dependencies in target/PAYLOAD_FILENAME to the repo configured here.
     */
    public static String PROPERTY_FOCUS_REPO = "focus.repo";

    public static final String HINT = "focus";
    private static final String FOCUS_PROFILE_NAME = "focus";
    private static final String FOCUS_MIRROR_NAME = "focus";
    private static final String NOOP_URL = "http://noop";

    @Requirement( hint = SettingsXmlConfigurationProcessor.HINT )
    ConfigurationProcessor realProc;

    @Requirement
    Logger logger;

    @Override public void process( CliRequest cliRequest ) throws Exception
    {
        realProc.process( cliRequest );
        if ( cliRequest.getUserProperties().getProperty( PROPERTY_FOCUS_REPO ) != null )
        {
            logger.info( "Focus mode is enabled." );
            Repository focusRepo = findRepository( cliRequest.getRequest(), cliRequest.getUserProperties().getProperty( PROPERTY_FOCUS_REPO ) );
            cliRequest.getRequest().setMirrors( adaptMirrors( cliRequest.getRequest(), focusRepo ) );
            cliRequest.getRequest().setProfiles( adaptProfiles( cliRequest.getRequest(), focusRepo ) );

            cliRequest.getRequest().addActiveProfile( FOCUS_PROFILE_NAME );
            Set<String> profiles = new HashSet<String>( cliRequest.getRequest().getActiveProfiles() );
            printNewSettingsOverview( cliRequest, profiles );
        }
        else
        {
            logger.info( "Focus mode is disabled." );
        }
    }

    private void printNewSettingsOverview( CliRequest cliRequest, Set<String> profiles )
    {
        for ( Profile p : cliRequest.getRequest().getProfiles() )
        {
            String active = profiles.contains( p.getId() ) ? "Active" : "Inactive";
            logger.info( active + " Profile: " + p.getId() );
            for ( Repository repo : p.getRepositories() )
            {
                logger.info( active + " Repository " + repo.getId() + " from profile " + p.getId() + " with target: " + repo.getUrl() );
            }
            for ( Repository repo : p.getPluginRepositories() )
            {
                logger.info( active + " PluginRepository " + repo.getId() + " from profile " + p.getId() + " with target: " + repo.getUrl() );
            }
        }
    }

    private Repository findRepository( MavenExecutionRequest request, String repoID ) throws MavenExecutionException
    {
        for ( Profile profile : request.getProfiles() )
        {
            for ( Repository repo : profile.getRepositories() )
            {
                if ( repo.getId().equals( repoID ) )
                {
                    return repo;
                }
            }
        }
        throw new MavenExecutionException( "Focus repository " + repoID + " is not configured.", request.getPom() );
    }

    private List<Profile> adaptProfiles( MavenExecutionRequest request, Repository focusRepo )
    {
        List<Profile> profiles = new ArrayList<>();

        for ( Profile profile : request.getProfiles() )
        {
            logger.info( "Checking profile.." + profile );
            for ( Repository repo : profile.getRepositories() )
            {
                if ( !repo.getId().equals( focusRepo.getId() ) )
                {
                    repo.setUrl( NOOP_URL );
                    logger.info( "Invalidated access for + " + repo.getId() + " to " + repo.getUrl() + "." );
                }
            }
            for ( Repository repo : profile.getPluginRepositories() )
            {
                if ( !repo.getId().equals( focusRepo.getId() ) )
                {
                    repo.setUrl( NOOP_URL );
                    logger.info( "Invalidated access for + " + repo.getId() + " to " + repo.getUrl() + "." );
                }
            }
            profiles.add( profile );
        }
        profiles.add( invalidateCentralProfile() );
        return profiles;
    }

    private Profile invalidateCentralProfile()
    {
        Profile focusProfile = new Profile();
        focusProfile.setId( FOCUS_PROFILE_NAME );

        RepositoryPolicy activePolicy = new RepositoryPolicy();
        activePolicy.setEnabled( true );

        Repository centralRepoReleases = new Repository();
        centralRepoReleases.setId( "central" );
        centralRepoReleases.setReleases( activePolicy );
        centralRepoReleases.setSnapshots( activePolicy );
        centralRepoReleases.setUrl( NOOP_URL );
        focusProfile.addRepository( centralRepoReleases );
        focusProfile.addPluginRepository( centralRepoReleases );
        return focusProfile;
    }

    private List<Mirror> adaptMirrors( MavenExecutionRequest request, Repository focusRepo )
    {
        List<Mirror> mirrors = new ArrayList<>();
        Mirror mirror = new Mirror();
        mirror.setId( FOCUS_MIRROR_NAME );
        mirror.setMirrorOf( "*,!" + focusRepo.getId() );
        mirror.setUrl( focusRepo.getUrl() );
        logger.info( "Set mirror for " + mirror.getMirrorOf() + " to " + mirror.getUrl() + "." );
        mirrors.add( mirror );
        return mirrors;
    }
}
