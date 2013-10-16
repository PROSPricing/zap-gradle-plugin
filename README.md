## Getting the ZAP gradle plugin

One of the open issues with this project is getting it into maven central. For now, you have to build it yourself, which is fortunately simple.

```bash
git clone http://github.com/PROSPricing/zap-gradle-plugin
cd zap-gradle-plugin
./gradlew jar uploadArchives
```

Retrieve the produced jar file from ../repo/com/pros/gradle.

## Integrating the Plugin With Your Build

Download ZAP from http://code.google.com/p/zaproxy/downloads/list and install it on your system. The plugin does not currently bundle ZAP, but has been tested with versions 2.2.2 and 2.2.1.

Update your project's build.gradle file to contain the following:

```groovy
apply plugin: 'zap'

zapConfig {
    // The directory location containing the ZAP install.
    zapInstallDir = "/path/to/ZAP/install/directory"
    // The URL of the application which ZAP should run active scanning against and generate issue reports for.
    // This should be the URL of the application that you are testing. This is used to generate the report as well
    // as to trigger the active scanning.
    applicationUrl = "http://attackme.example.com:8080"
}

buildscript {
    repositories {
        // Path to a maven repository that contains the plguin jar. Feel free to link the jar however you want.
        mavenRepo(url: "uri/of/repo/containing/plugin")
    }
    dependencies {
        classpath 'com.pros.gradle:zap-gradle-plugin:1.0-SNAPSHOT'
    }
}
```

## Optional Properties
There are a few optional properties that may be specified within the zapConfig section of the gradle file to further tune your use of ZAP.

```groovy
    // The port on which ZAP should run. Defaults to 54300.
    proxyPort = "9999"
    // The format of the output report. Acceptable formats are JSON, HTML, and XML. Defaults to JSON.
    reportFormat = "JSON"
    // The path of the report file to write from the zapReport task. This path must be writable, subdirs will NOT be created.
    reportOutputPath = "report"
    // The timeout for the active scanner process. How long should we keep polling for scan completion in minutes. Defaults to 30.
    activeScanTimeout = "30"
```

## Running ZAP with Tests
`./gradlew zapStart taskThatRunsMyTestsWithALocalProxySetToZAPProxyPort zapActiveScan zapReport zapStop`

## Updating Tests to Use ZAP

In order for ZAP to see traffic to your app, it must be used as a proxy for those requests. Different testing tools will have different mechanisms for setting up a proxy for a given HTTP request. Some examples are below.

Python with httplib2:
```python
http_con = httplib2.Http(proxy_info = httplib2.ProxyInfo(httplib2.socks.PROXY_TYPE_HTTP, 'localhost', proxyPort))
```

Java/Groovy with URLConnection:
```java
URL url = new URL("http://attackme.example.com");
Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", proxyPort));
URLConnection connection = url.openConnection(proxy);
```

Ruby:
```ruby
proxy_addr = 'localhost'
proxy_port = 9999

Net::HTTP.new('attackme.example.com', nil, proxy_addr, proxy_port).start { |http|
  # always proxy via your.proxy.addr:9999
}
```

## LICENSE
Copyright (c) 2013, PROS Inc. All right reserved.

Released under BSD-3 style license.

See http://opensource.org/licenses/BSD-3-Clause and LICENSE file for details.
