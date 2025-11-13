# santorini

## 背景

生产实践中，我们通常以服务为单元进行管理，阐述一下目的吧:

1. 这个系统支持将某个 `OAuth 2.0` 授权登录，并且可以定义授权逻辑
2. 可以管理版本,包括查看历史版本，发布新版，重启
3. 可以查看日志
4. 可以查看节点运行状况

## 是什么

- 是一组 kubernetes 资源描述的定义。
- 以及对这些定义做出支持的工具集

## 能做什么

简化面对不同人群的运维需求。

## 概念

### 服务



### 环境

空间内对环境的描述，包括基本环境和命名环境。

This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by clicking the Gradle icon in the right-hand toolbar,
or run it directly from the terminal:

* Run `./gradlew run` to build and run the application.
* Run `./gradlew build` to only build the application.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.

Note the usage of the Gradle Wrapper (`./gradlew`).
This is the suggested way to use Gradle in production projects.

[Learn more about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

[Learn more about Gradle tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks).

This project follows the suggested multi-module setup and consists of the `app` and `utils` subprojects.
The shared build logic was extracted to a convention plugin located in `buildSrc`.

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare and version dependencies
and both a build cache and a configuration cache (see `gradle.properties`).