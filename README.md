# MediaStreaming - Videocall

Repository for the Videocall of Media Streaming Module (T3.3).

## Video Call

This part of the app creates a P2P video call between two users connected to
the same signalling server. The default configuration is the following:

* Signaling server: https://builder.helios-social.eu:11794
* TURN server: turn:77.231.202.138:3478
* STUN server: stun:77.231.202.138:3478
* Room Name: test_room

All these values can be modified in the `values/strings.xml` file.

### How to use Video Call

It must be expecified the room name.

```java
    Intent videoCallIntent = new Intent(MainActivity.this, VideoCallActivity.class);
    videoCallIntent.putExtra("room_name", "test_room");
    MainActivity.this.startActivity(videoCallIntent);
```

### Request permissions

Before start activity of the video call intent:

```java
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
    || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, ALL_PERMISSIONS_CODE);
    }
```

## Multiproject dependencies

HELIOS software components are organized into different repositories so that
these components can be developed separately avoiding many conflicts in code
integration. However, the modules also depend on each other.

### How to configure the dependencies

To manage project dependencies developed by the consortium, the approach
proposed is to use a private Maven repository with Nexus.

To avoid clone all dependencies projects in local, to compile the "father"
project. Otherwise, a developer should have all the projects locally to be able
to compile. Using Nexus, the dependencies are located in a remote repository,
available to compile, as described in the next section. Also to improve the
automation for deploy, versioning and distribution of the project.

### How to use the HELIOS Nexus

Similar to other dependencies available in Maven Central, Google or others
repositories. In this case we specify the Nexus repository provided by Atos:
`https://builder.helios-social.eu/repository/helios-repository/`

This URL makes the project dependencies available.

To access, we simply need credentials, that we will define locally in the
variables `heliosUser` and `heliosPassword`.

The `build.gradle` of the project define the Nexus repository and the credential
variables in this way:

```gradle
repositories {
        ...
        maven {
            url "https://builder.helios-social.eu/repository/helios-repository/"
            credentials {
                username = heliosUser
                password = heliosPassword
            }
        }
    }
```

And the variables of Nexus's credentials are stored locally at
`~/.gradle/gradle.properties`:

```properties
heliosUser=username
heliosPassword=password
```

To request Nexus username and password, contact with:
`jordi.hernandezv@atos.net`

### How to deploy a new version of the dependencies

Let's say that we want to deploy a new version of the videocall project. This
project is a dependency of MediaStreaming. For Continuous Integration we use
Jenkins. It deploys the configured projects (e.g., videocall) in different jobs,
and the results are libraries packaged like AAR (Android ARchive). These
packaged libraries are upload to Nexus and in this way, they are available to
build the projects that depend on them (e.g., MediaStreaming). In the videocall
example, Jenkins jobs generate automatically and aar library and store it at the
Nexus repository to make it available.

Jenkins is the tool deployed by Atos (WP6 leader) in HELIOS to automate the
generation of APKs, joining all the project modules. Due to the need of managing
the dependencies, Atos has selected additional tools, as explained in this
document.

After pushing a change to the `master` branch, the maintainer can builds the
module by means of the job in the Jenkins interface. GitLab repositories are set
to protect the `master` branch push and merge for the partner in charge of its
module/project (maintainer).

To request Jenkins username and password, contact with:
`jordi.hernandezv@atos.net`

### How to use the dependencies

To use the dependency in `build.gradle` of the "father" project, you should
specify the last version available in Nexus, related to the last Jenkins's
deploy. For example, to declare the dependency on the videocall module and the
respective version:

`implementation 'eu.h2020.helios_social.modules.videocall:videocall:1.0.36'`

For more info review:
`https://scm.atosresearch.eu/ari/helios_group/generic-issues/blob/master/multiprojectDependencies.md`
