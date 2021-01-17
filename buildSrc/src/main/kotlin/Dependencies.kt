object Libs {
    const val kotlin_version = "1.4.21"

    object Kotlin {
        const val kotlinStdLib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
        private const val coroutinesVersion = "1.4.1"
        const val coroutinesJdk8 = "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion"

    }

    object Vavr {
        private const val version = "0.10.2"
        const val kotlin = "io.vavr:vavr-kotlin:$version"
        const val jackson = "io.vavr:vavr-jackson:$version"
    }

    object Jackson {
        private const val version = "2.11.3"
        const val jacksonModuleKotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:$version"
        const val jacksonAnnotations = "com.fasterxml.jackson.core:jackson-annotations:$version"
        const val jdk8 = "com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$version"
        const val jsr310 = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$version"

    }

    object Kotest {
        private const val version = "4.3.1"
        const val runnerJunit5Jvm = "io.kotest:kotest-runner-junit5-jvm:$version"
        const val assertionsCoreJvm = "io.kotest:kotest-assertions-core-jvm:$version"
    }

    object Slf4J {
        private const val version = "1.7.28"
        const val api = "org.slf4j:slf4j-api:$version"
    }

    object Reactor {
        private const val version = "3.4.2"
        const val core = "io.projectreactor:reactor-core:$version"
        const val test = "io.projectreactor:reactor-test:$version"
        object Kotlin {
            private const val version = "1.1.2"
            const val extensions = "io.projectreactor.kotlin:reactor-kotlin-extensions:$version"
        }

    }

    object Commons {
        object IO {
            private const val version = "2.6"
            const val just = "commons-io:commons-io:$version"
        }
    }


}
