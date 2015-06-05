package com.rebaze.maven.focus;

import com.rebaze.maven.support.MavenBoot;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CompleteRunnerTest
{
    //@Test
    public void testRunner() throws Exception
    {
        System.setProperty( "org.slf4j.simpleLogger.defaultLogLevel","trace" );
        MavenBoot mavenBoot = new MavenBoot();
        // should automatically load all extensions in classpath
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        File project = new File("src/test/resources/testproject");
        request.setLocalRepositoryPath( new File( "target/localrepo" ) );
        request.setBaseDirectory( project );
        request.setPom( new File(project,"pom.xml") );
        request.setGoals( goals( "install" ) );
        MavenExecutionResult result = mavenBoot.maven( request );
        assertEquals( "wrong", result.getProject().getId() );
    }

    private List<String> goals( String... goals )
    {
        return Arrays.asList(goals);
    }
}
