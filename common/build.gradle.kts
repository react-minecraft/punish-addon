
repositories {

    maven("https://oss.sonatype.org/content/groups/public/")

    maven("https://repo.codemc.io/repository/maven-public/")

}

dependencies {

    compileOnly(libs.paper.api)
    compileOnly(libs.xseries)

    compileOnly(libs.react.api)
    compileOnly(libs.react.common)
    compileOnly(libs.actions)
    compileOnly(libs.schedula.common)

    compileOnly(libs.cloud.paper)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.packetevents)

    testImplementation(libs.paper.api)
    testImplementation(libs.react.api)
    testImplementation(libs.actions)
    testImplementation(libs.schedula.common)
    testImplementation(libs.configurate.yaml)
    testImplementation(libs.packetevents)
    testImplementation("org.slf4j:slf4j-api:2.0.16")

}
