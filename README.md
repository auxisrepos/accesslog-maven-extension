# focus-maven-extension

This Maven Extension is available under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)

[![Build Status](https://travis-ci.org/rebaze/focus-maven-extension.svg?branch=master)](https://travis-ci.org/rebaze/focus-maven-extension)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.rebaze.maven/focus-maven-extension/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.rebaze.maven/focus-maven-extension)

# There can only be one
Lets you tighten access to maven repositories like maven central on a per build level.
Similar to offline builds this will redirect all repositories to a single repository described with property -Dfocus.repo.
In effect, it will invalidate the urls to maven central and set a mirror to all repositories point to the repository defined above.

# Installation
This extension will probably work with Maven starting version 3.2.5.
Since this is a |Maven Core Extension], you must either install it directly into MavenHome/lib/ext or install it into [ProjectHome/.mvn/extensions.xml](http://takari.io/2015/03/19/core-extensions.html) (starting Maven 3.3.1).
