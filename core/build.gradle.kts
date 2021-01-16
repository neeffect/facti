dependencies {
    api(Libs.Vavr.kotlin) {
        exclude("org.jetbrains.kotlin")
    }
//    api(Libs.Haste.haste)
    api(Libs.Reactor.core)
    implementation(Libs.Vavr.jackson)
    implementation(Libs.Jackson.jacksonModuleKotlin) {
        exclude("org.jetbrains.kotlin")
    }
    implementation(Libs.Jackson.jdk8)
    implementation(Libs.Jackson.jsr310)

//    compile group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jdk8', version: '2.9.7'
//    compile group: 'com.fasterxml.jackson.datatype', name: 'jackson-datatype-jsr310', version: '2.9.7'
    implementation(Libs.Commons.IO.just)


//    compile 'io.github.krasnoludkolo:haste:0.0.1'
//    testCompile ('io.kotlintest:kotlintest-runner-junit5:3.1.7') {
//        exclude group: 'org.jetbrains.kotlin'
//    }
    testImplementation(Libs.Reactor.test)
    testImplementation(Libs.Kotest.runnerJunit5Jvm)
    testImplementation(Libs.Kotest.assertionsCoreJvm)
}

