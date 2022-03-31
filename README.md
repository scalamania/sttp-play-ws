## sttp-play-ws ##

![Build status](https://github.com/scalamania/sttp-play-ws/actions/workflows/ci.yml/badge.svg)

[sttp][sttp] backend for [play-ws][playws].

### Goals ###

The main goal of this library is to allow the usage of sttp machinery in the context of an already existing Play app, avoiding inclusion of a different HTTP client.
If you develop HTTP clients with sttp to be used, e.g. on an Akka based micro Service, and you need some of the same logic in your Play Frontend / backoffice application writen in play, this library may be helpful.


### Getting Started ###
 
Include the following on your build.sbt or similar:
 

```scala
libraryDependencies += "io.github.scalamania" %% "sttp-play-ws" % "<latest>"
```

This library is published using play 2.8 as a `Provided` dependency, which means does not include play itself and allows upgrades on non-broken changes.
(published for both 2.12 and 2.13)

For play 2.6, 2.7 use [legacy-play-ws][legacy-play-ws]

### Features ###

Supports all *tested* features of sttp backends. Uses sttp own tests(pooled automatically) for unit testing of this backend.

Main supported features:

* Streaming (using `akka.stream.Source[ByteString, _]`
* Proxy support
* Multipart uploads.



### Usage with Guice ###

TBD


### Notes ###

This library depends on `play-ws` (ense many play dependencies) and not on the play-ws-standalone project. This is due to the missing multipart support on the standalone artifact.
When this gets sorted library will probably change to depend solely on play-ws-standalone.






[sttp]: https://github.com/softwaremill/sttp
[playws]: https://github.com/playframework/play-ws
[legacy-play-ws]: https://github.com/ragb/sttp-play-ws

