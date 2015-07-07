# focus-maven-extension

This Maven Extension is available under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)

[![Build Status](https://travis-ci.org/rebaze/focus-maven-extension.svg?branch=master)](https://travis-ci.org/rebaze/focus-maven-extension)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.rebaze.maven/focus-maven-extension/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.rebaze.maven/focus-maven-extension)

# Motivation: There can only be one
Lets you tighten access to maven repositories like maven central on a per build level.
Similar to offline builds this will redirect all repositories to a single repository described with property -Dfocus.repo.
In effect, it will invalidate the urls to maven central and set a mirror to all repositories point to the repository defined above.

We are using this extension to harden builds that are required to be working using a single (company internal) repository but developers are known to use proxied internet connections to Maven Central and other public resources in regular day-to-day development.
You may not want to restrict developer access to public repositories but want to give developers a simple way to verify the build still works in a constrainted (continuous integration) environment without maintaining multiple settings.

# Installation
This extension will probably work with Maven starting version 3.2.5.
Since this is a Maven Core Extension, you must either install it directly into MavenHome/lib/ext or install it into [extensions.xml](http://takari.io/2015/03/19/core-extensions.html) (starting Maven 3.3.1).

It is recommended to use Maven 3.3.1+ and configure the extension under file `yourprojectfolder/.mvn/extensions.xml` like so:

      <extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
          <extension>
              <groupId>com.rebaze.maven</groupId>
              <artifactId>focus-maven-extension</artifactId>
              <version>0.2.0</version>
          </extension>
      </extensions>

# Usage
The extension is installed as described but will not be effective unless activated per Maven Build using the property 
`-Dfocus.repo=myrepos`

Example:
`mvn clean verify -Dfocus.repo=nexus`

This of cause requires to have a repository called "nexus" configured in your settings.
A build kicked off like this will direct all traffic to that single repository.

